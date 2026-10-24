import "server-only";
import { z } from "zod";
import { getConfig } from "./config";
import { PortalFailure } from "./errors";
export type HostedAction = "change-profile" | "change-password" | "recovery";
const paths = new Set(["/account", "/account/security"]);
function invalid() {
  return new PortalFailure(
    400,
    "INVALID_CONTINUATION",
    "Return to your account and start the action again.",
  );
}
export function buildHostedAction(
  action: HostedAction,
  returnTo: string,
  expectedLoginId: string,
): URL {
  const config = getConfig();
  if (!paths.has(returnTo)) throw invalid();
  if (action === "recovery") return new URL(config.issuer + "/forgot-password");
  if (action !== "change-profile" && action !== "change-password")
    throw invalid();
  if (!z.uuid().safeParse(expectedLoginId).success) throw invalid();
  const url = new URL(config.issuer + "/portal/account-action");
  url.search = new URLSearchParams({
    action: action === "change-profile" ? "profile" : "password",
    clientId: config.clientId,
    expectedLoginId,
    returnTo: config.origin + returnTo,
    "x-request-datetime": new Date().toISOString(),
  }).toString();
  return url;
}
export function parseHostedContinuation(query: URLSearchParams): {
  returnTo: string;
  notice: "returned" | "cancelled" | "unverified";
} {
  if ([...query.keys()].length !== 1 || query.getAll("p").length !== 1)
    throw invalid();
  const encoded = query.get("p")!;
  if (
    encoded.length > 4096 ||
    !encoded.length ||
    !/^([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(
      encoded,
    )
  )
    throw invalid();
  const bytes = Buffer.from(encoded, "base64");
  if (bytes.toString("base64") !== encoded) throw invalid();
  const text = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
  const p = new URLSearchParams(text);
  if (
    p.getAll("surl").length !== 1 ||
    p.getAll("errors.codes").length > 1 ||
    [...p.keys()].some((key) => key !== "surl" && key !== "errors.codes")
  )
    throw invalid();
  const source = p.get("surl")!;
  const returnTo = [...paths].find(
    (path) => source === getConfig().origin + path,
  );
  if (!returnTo) throw invalid();
  // Java's unsigned continuation is a navigation hint, never save confirmation.
  return {
    returnTo,
    notice: !p.has("errors.codes")
      ? "returned"
      : p.get("errors.codes") === "auths.304.001"
        ? "cancelled"
        : "unverified",
  };
}
