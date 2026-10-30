import { test, expect, type BrowserContext, type Page } from "@playwright/test";
import { createHmac, randomUUID } from "node:crypto";
import { execFileSync } from "node:child_process";
const api = process.env.E2E_API_URL || "http://127.0.0.1:18080/api";
const secret = process.env.E2E_JWT_SECRET;
function token(id: string) {
  const enc = (o: unknown) =>
    Buffer.from(JSON.stringify(o)).toString("base64url");
  const h = enc({ alg: "HS256", typ: "JWT" });
  const p = enc({
    sub: id,
    aud: "authenticated",
    role: "authenticated",
    iss: "http://127.0.0.1:54321/auth/v1",
    exp: Math.floor(Date.now() / 1000) + 3600,
    iat: Math.floor(Date.now() / 1000),
  });
  return `${h}.${p}.${createHmac("sha256", secret!).update(`${h}.${p}`).digest("base64url")}`;
}
async function call(
  path: string,
  bearer: string,
  method = "GET",
  body?: unknown,
) {
  const response = await fetch(api + path, {
    method,
    headers: {
      Authorization: `Bearer ${bearer}`,
      "Content-Type": "application/json",
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const result = await response.json();
  expect(response.ok, JSON.stringify(result)).toBeTruthy();
  return result;
}
async function login(context: BrowserContext, name: string) {
  const id = randomUUID();
  const accessToken = token(id);
  await call("/me", accessToken);
  await call("/me", accessToken, "PUT", {
    username: "e2e_" + id.replaceAll("-", "").slice(0, 12),
    displayName: name,
    avatar: name === "Nova" ? 0 : 1,
    language: "English",
    proficiency: "intermediate",
    subjects: ["math", "quant", "logic"],
  });
  await context.addInitScript(
    ({ id, accessToken }) => {
      localStorage.setItem(
        "sb-127-auth-token",
        JSON.stringify({
          access_token: accessToken,
          refresh_token: "local-test-refresh",
          expires_in: 3600,
          expires_at: Math.floor(Date.now() / 1000) + 3600,
          token_type: "bearer",
          user: {
            id,
            aud: "authenticated",
            role: "authenticated",
            email: `${id}@example.test`,
            app_metadata: { provider: "email" },
            user_metadata: {},
            created_at: new Date().toISOString(),
          },
        }),
      );
    },
    { id, accessToken },
  );
  return { id, accessToken };
}
async function playRound(page: Page, count: number) {
  for (let i = 0; i < count; i++) {
    await expect(page.locator(".question-meta")).toContainText(
      `QUESTION ${i + 1}`,
      { timeout: 22000 },
    );
    await page.locator(".answer").first().click();
    await expect(page.locator(".answer-status")).toContainText("Answer locked");
  }
  await expect(page).toHaveURL(/\/results\//, { timeout: 22000 });
}
test("landing, mobile layout, auth guard and setup state", async ({ page }) => {
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "Big brain energy. Welcome in." }),
  ).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Enter the arena" }),
  ).toBeVisible();
  await page.screenshot({
    path: "test-results/home-desktop.png",
    fullPage: true,
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.locator("body")).toHaveJSProperty("scrollWidth", 390);
  await page.screenshot({
    path: "test-results/home-mobile.png",
    fullPage: true,
  });
  await page.getByRole("link", { name: "Enter the arena" }).click();
  await expect(page).toHaveURL(/\/auth\?next=/);
  await expect(page.getByLabel("Email address")).toBeVisible();
});
test("placement → bot → report → recovery persists through reload", async ({
  browser,
}) => {
  test.skip(!secret, "Requires an isolated test backend with E2E_JWT_SECRET.");
  const context = await browser.newContext();
  const player = await login(context, "Nova");
  const page = await context.newPage();
  await page.goto("/play");
  await page.getByRole("button", { name: "Start my placement" }).click();
  await playRound(page, 5);
  await expect(
    page.getByRole("heading", { name: "Your journey has a starting point." }),
  ).toBeVisible();
  await page.getByRole("link", { name: "Play another round" }).click();
  await page.getByRole("button", { name: "Let’s play" }).click();
  await playRound(page, 5);
  await expect(page.locator(".xp-earned")).toContainText("XP");
  const path = new URL(page.url()).pathname;
  await page.reload();
  await expect(page).toHaveURL(new RegExp(path));
  await expect(
    page.getByRole("heading", { name: "Your round, question by question." }),
  ).toBeVisible();
  await page.locator(".review-toggle").first().click();
  await expect(page.locator(".formula")).toBeVisible();
  await page.screenshot({ path: "test-results/results.png", fullPage: true });
  await page.getByRole("button", { name: "Start recovery" }).click();
  await playRound(page, 3);
  const me = await call("/me", player.accessToken);
  expect(me.total_xp).toBeGreaterThanOrEqual(80);
  expect(me.stats.played).toBe(2);
  await context.close();
});
test("two separate browsers share friend questions and recover state", async ({
  browser,
}) => {
  test.skip(
    !secret || !process.env.E2E_PSQL,
    "Requires an isolated test database and signed-token backend.",
  );
  const a = await browser.newContext();
  const b = await browser.newContext();
  const alice = await login(a, "Duel Alpha");
  const bob = await login(b, "Duel Beta");
  // Setup fixture only. Production has no endpoint to bypass placement.
  execFileSync(process.env.E2E_PSQL!, [
    "-h",
    "127.0.0.1",
    "-p",
    "55432",
    "-U",
    "playbit_test",
    "-d",
    "playbit",
    "-c",
    `UPDATE user_subject_ratings SET assessed=true WHERE user_id IN ('${alice.id}','${bob.id}');`,
  ]);
  const pa = await a.newPage();
  const pb = await b.newPage();
  await pa.goto("/play?mode=friend");
  await pa.getByRole("button", { name: "Create a room" }).click();
  await expect(pa.locator(".room-code")).toBeVisible();
  const code = (await pa.locator(".room-code").textContent())!.trim();
  await pb.goto(`/play?mode=friend&room=${code}`);
  await pb.getByRole("button", { name: "Join room", exact: true }).click();
  await expect(pb.locator(".room-code")).toHaveText(code);
  await pa.getByRole("button", { name: "I’m ready" }).click();
  await pb.getByRole("button", { name: "I’m ready" }).click();
  await expect(pa).toHaveURL(/\/arena\//);
  await expect(pb).toHaveURL(/\/arena\//);
  for (let i = 0; i < 5; i++) {
    await expect(pa.locator(".question-meta")).toContainText(
      `QUESTION ${i + 1}`,
      { timeout: 22000 },
    );
    await expect(pb.locator(".question-meta")).toContainText(
      `QUESTION ${i + 1}`,
      { timeout: 22000 },
    );
    expect(await pa.locator(".question-panel h1").textContent()).toBe(
      await pb.locator(".question-panel h1").textContent(),
    );
    await pa.locator(".answer").first().click();
    await pb.locator(".answer").last().click();
    if (i === 1) {
      await pa.reload();
      await expect(pa.locator(".answer-status")).toContainText("Answer locked");
    }
  }
  await expect(pa).toHaveURL(/\/results\//, { timeout: 22000 });
  await expect(pb).toHaveURL(/\/results\//, { timeout: 22000 });
  await expect(pa.locator(".question-review")).toHaveCount(5);
  await expect(pb.locator(".question-review")).toHaveCount(5);
  await a.close();
  await b.close();
});
