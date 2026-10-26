export class ActionFailure extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}
export async function requestJson<T>(
  url: string,
  method = "GET",
  input?: unknown,
): Promise<T> {
  let headers: Record<string, string> = {};
  if (method !== "GET") {
    const session = await fetch("/api/session", { cache: "no-store" });
    if (!session.ok)
      throw new ActionFailure(
        session.status,
        "Your session could not be checked. Sign in again if it has ended.",
      );
    headers = {
      "Content-Type": "application/json",
      "x-csrf-token": (await session.json()).csrfToken,
    };
  }
  let response: Response;
  try {
    response = await fetch(url, {
      method,
      cache: "no-store",
      headers,
      ...(method === "GET" ? {} : { body: JSON.stringify(input ?? {}) }),
    });
  } catch {
    throw new ActionFailure(
      503,
      "The response was lost. Read current state before retrying. A lost new secret must be rotated; it cannot be recovered.",
    );
  }
  const value = await response.json();
  if (!response.ok) {
    if (response.status === 403) window.dispatchEvent(new Event("focus"));
    throw new ActionFailure(
      response.status,
      typeof value.message === "string"
        ? value.message
        : "The action could not be completed. Read current state before retrying.",
    );
  }
  return value as T;
}
export function actionMessage(error: unknown) {
  return error instanceof ActionFailure
    ? error.message
    : "The action could not be verified. Read current state before retrying.";
}
