import { supabase } from "../lib/supabase";
export async function api<T = Record<string, any>>(
  path: string,
  method = "GET",
  body?: unknown,
): Promise<T> {
  const session = (await supabase?.auth.getSession())?.data.session;
  const response = await fetch(
    `${import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api"}${path}`,
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
      "Cannot reach the Playbit server. Check that the backend is running.",
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
