import "server-only";
import { getConfig } from "./config";
import { getStore } from "./session-store";
/**
 * Configures lazily and proves the session dependency answers. The Java issuer is
 * deliberately not probed: readiness must not mark the portal down because the
 * identity service or its external delivery providers are briefly unavailable.
 */
export async function readiness() {
  getConfig();
  await (await getStore()).ping();
}
