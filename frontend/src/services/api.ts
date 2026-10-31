import { supabase } from "../lib/supabase";
const configuredApiBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim();
const pointsToLocalhost = /^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?(\/|$)/i.test(
  configuredApiBaseUrl || "",
);
const apiBaseUrl = import.meta.env.PROD
  ? configuredApiBaseUrl && !pointsToLocalhost
    ? configuredApiBaseUrl.replace(/\/+$/, "")
    : "/api"
  : (configuredApiBaseUrl || "http://localhost:8080/api").replace(/\/+$/, "");

export async function api<T = Record<string, any>>(
  path: string,
  method = "GET",
  body?: unknown,
): Promise<T> {
  if (!apiBaseUrl) {
    throw new Error(
      "The Playbit API is not configured. Set VITE_API_BASE_URL to your deployed backend URL, then redeploy.",
    );
  }
  const session = (await supabase?.auth.getSession())?.data.session;
  const request: RequestInit = {
    method,
    cache: method === "GET" ? "no-store" : undefined,
    headers: {
      "Content-Type": "application/json",
      ...(session ? { Authorization: `Bearer ${session.access_token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  };
  // Matchmaking join is an idempotent queue poll; a lost response can safely be retried.
  const canRetry = method === "GET" || path === "/matchmaking/join";
  let response: Response | undefined;
  for (let attempt = 0; attempt < (canRetry ? 3 : 1); attempt++) {
    try {
      response = await fetch(`${apiBaseUrl}${path}`, request);
      break;
    } catch {
      if (!canRetry || attempt === 2) {
        throw new Error(
          "The Playbit server could not be reached. Check your internet connection and retry; if it keeps happening, the backend may be waking up or restarting.",
        );
      }
      await new Promise((resolve) => setTimeout(resolve, 750 * (attempt + 1)));
    }
  }
  if (!response) throw new Error("The Playbit server did not respond. Please retry.");
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(
      error.message ||
        (response.status === 401
          ? "Your session has expired. Please sign in again."
          : `Request failed (${response.status})`),
    );
  }
  return response.json();
}

export async function uploadStudyImage<T = Record<string, any>>(file: File): Promise<T> {
  if (!apiBaseUrl) throw new Error("The Playbit API is not configured.");
  const session = (await supabase?.auth.getSession())?.data.session;
  const form = new FormData();
  form.append("file", file);
  let response: Response;
  try {
    response = await fetch(`${apiBaseUrl}/study/extract-image`, {
      method: "POST",
      headers: session ? { Authorization: `Bearer ${session.access_token}` } : {},
      body: form,
    });
  } catch {
    throw new Error("The Playbit server could not be reached. Check your connection and try again.");
  }
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(error.message || `Image upload failed (${response.status}).`);
  }
  return response.json();
}
