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
  const response = await fetch(
    `${apiBaseUrl}${path}`,
    {
      method,
      headers: {
        "Content-Type": "application/json",
        ...(session ? { Authorization: `Bearer ${session.access_token}` } : {}),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    },
  ).catch(() => {
    throw new Error(
      "Cannot reach the Playbit API. Check VITE_API_BASE_URL in Vercel and confirm the backend allows this site in CORS_ORIGINS.",
    );
  });
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
