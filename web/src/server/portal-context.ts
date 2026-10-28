import "server-only";
import { PortalContextSchema, type PortalContext } from "../contracts/portal";
import { javaRequest } from "./java-api";
import type { PortalSession } from "./types";
export function getPortalContext(
  session: PortalSession,
): Promise<PortalContext> {
  return javaRequest(
    session,
    { kind: "context" },
    undefined,
    PortalContextSchema,
  );
}
