import { ActionFailure, actionMessage } from "../clients/browser-api";
export { ActionFailure, actionMessage };

/** Multipart mutations reuse the session/CSRF boundary but let the browser set the boundary. */
export async function requestForm<T>(url: string, form: FormData): Promise<T> {
  const session = await fetch("/api/session", { cache: "no-store" });
  if (!session.ok)
    throw new ActionFailure(
      session.status,
      "Your session could not be checked. Sign in again if it has ended.",
    );
  let response: Response;
  try {
    response = await fetch(url, {
      method: "POST",
      cache: "no-store",
      headers: { "x-csrf-token": (await session.json()).csrfToken },
      body: form,
    });
  } catch {
    throw new ActionFailure(
      503,
      "The upload response was lost. Reload the certificate list before retrying; the import may have completed.",
    );
  }
  let value: { message?: unknown; code?: unknown };
  try {
    value = await response.json();
  } catch {
    throw new ActionFailure(
      response.ok ? 502 : response.status,
      "The import result could not be read. Reload the certificate list before retrying.",
      "INVALID_RESPONSE",
    );
  }
  if (!response.ok) {
    if (response.status === 403) window.dispatchEvent(new Event("focus"));
    throw new ActionFailure(
      response.status,
      typeof value.message === "string"
        ? value.message
        : "The certificate could not be imported. Reload the list before retrying.",
      typeof value.code === "string" ? value.code : undefined,
    );
  }
  return value as T;
}
