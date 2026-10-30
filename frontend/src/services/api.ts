import { supabase } from "../lib/supabase";
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL?.replace(/\/+$/, "");

export async function api<T = Record<string, any>>(
  path: string,
  method = "GET",
  body?: unknown,
): Promise<T> {
  if (!apiBaseUrl) {
    throw new Error(
      "The Playbit API is not configured. Set VITE_API_BASE_URL to your deployed backend URL in Vercel, then redeploy.",
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
