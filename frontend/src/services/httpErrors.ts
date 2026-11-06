let rateLimitedUntil = 0;

export class ApiError extends Error {
  constructor(message: string, public status: number, public retryAt = 0) {
    super(message);
    this.name = "ApiError";
  }
}

function rateLimitError() {
  const seconds = Math.max(1, Math.ceil((rateLimitedUntil - Date.now()) / 1000));
  return new ApiError(
    `Playbit is temporarily limiting requests. Please wait ${seconds} seconds before trying again.`,
    429,
    rateLimitedUntil,
  );
}

// Share the cooldown across API calls in this tab, including polling and uploads.
export function checkApiCooldown() {
  if (Date.now() < rateLimitedUntil) throw rateLimitError();
}

export async function responseError(response: Response): Promise<ApiError> {
  if (response.status === 429) {
    const header = response.headers.get("Retry-After")?.trim();
    const now = Date.now();
    const retryAt = header && /^\d+$/.test(header)
      ? now + Number(header) * 1000
      : header ? Date.parse(header) : NaN;
    rateLimitedUntil = Math.max(
      rateLimitedUntil,
      Number.isFinite(retryAt) && retryAt > now ? retryAt : now + 30000,
    );
    return rateLimitError();
  }
  const body = await response.json().catch(() => null);
  return new ApiError(
    typeof body?.message === "string" ? body.message : response.status === 401
      ? "Your session has expired. Please sign in again."
      : `Request failed (${response.status}). Please try again.`,
    response.status,
  );
}

export function apiRetryDelay(attempt: number, error: Error) {
  return error instanceof ApiError && error.status === 429
    ? Math.max(1000, error.retryAt - Date.now())
    : Math.min(1000 * 2 ** attempt, 30000);
}
