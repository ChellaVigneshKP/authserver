import "server-only";
import { randomBytes } from "node:crypto";
import { PortalFailure } from "./errors";

const MAX_PARTS = 8;
const MAX_HEADER_BYTES = 8192;
const MAX_FIELD_BYTES = 4096;

type Part = {
  name: string;
  filename?: string;
  contentType?: string;
  data: Buffer;
};

function invalid(message = "Check the supplied certificate fields.") {
  return new PortalFailure(400, "INVALID_INPUT", message);
}

/**
 * Reads only a bounded prefix of a client request. Content-Length is a fast
 * rejection; the streaming counter is the actual authority and cancels above
 * the budget so an arbitrary upload is never buffered.
 */
export async function readBoundedRequest(
  request: Request,
  maximum: number,
): Promise<Buffer> {
  const declared = Number(request.headers.get("content-length"));
  if (Number.isFinite(declared) && declared > maximum) throw tooLarge();
  const reader = request.body?.getReader();
  if (!reader) return Buffer.alloc(0);
  let total = 0;
  const chunks: Uint8Array[] = [];
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      total += value.length;
      if (total > maximum) throw tooLarge();
      chunks.push(value);
    }
  } catch (error) {
    await reader.cancel().catch(() => {});
    if (error instanceof PortalFailure) throw error;
    throw invalid("The certificate upload could not be read.");
  }
  return Buffer.concat(chunks, total);
}

function tooLarge() {
  return new PortalFailure(
    413,
    "UPLOAD_TOO_LARGE",
    "Certificate files are limited to 1 MiB and the complete upload to 2 MiB.",
  );
}

function boundary(contentType: string | null) {
  const value = contentType ?? "";
  if (!/^multipart\/form-data\b/i.test(value))
    throw new PortalFailure(
      415,
      "UNSUPPORTED_MEDIA_TYPE",
      "Choose a certificate file in the form.",
    );
  const match = /(?:^|;)\s*boundary=(?:"([^"]{1,70})"|([^;\s]{1,70}))/i.exec(
    value,
  );
  if (!match) throw invalid("The certificate upload is incomplete.");
  const boundary = match[1] ?? match[2];
  if (!/^[0-9A-Za-z'()+_,./:=?-]+$/.test(boundary) || boundary.endsWith(" "))
    throw invalid("The certificate upload is incomplete.");
  return boundary;
}

export type CertificateUpload = {
  name: string;
  type: string;
  password: string;
  file: { filename: string; contentType: string; data: Buffer };
};

export function parseCertificateMultipart(
  body: Buffer,
  contentType: string | null,
): CertificateUpload {
  const delimiter = Buffer.from("--" + boundary(contentType));
  let position = 0;
  if (!body.subarray(0, delimiter.length).equals(delimiter))
    throw invalid("The certificate upload is incomplete.");
  position = delimiter.length;
  const parts: Part[] = [];
  while (true) {
    if (body.subarray(position, position + 2).toString() === "--") break;
    if (body.subarray(position, position + 2).toString() !== "\r\n")
      throw invalid("The certificate upload is incomplete.");
    position += 2;
    const headerEnd = body.indexOf("\r\n\r\n", position);
    if (headerEnd < 0 || headerEnd - position > MAX_HEADER_BYTES)
      throw invalid("The certificate upload is incomplete.");
    const headers = body.subarray(position, headerEnd).toString("latin1");
    position = headerEnd + 4;
    const next = body.indexOf(Buffer.concat([Buffer.from("\r\n"), delimiter]), position);
    if (next < 0) throw invalid("The certificate upload is incomplete.");
    parts.push({ ...parseHeaders(headers), data: body.subarray(position, next) });
    if (parts.length > MAX_PARTS) throw invalid("Too many upload fields.");
    position = next + 2 + delimiter.length;
  }
  if (body.subarray(position + 2).toString().replace(/^\r\n/, "") !== "")
    throw invalid("The certificate upload is incomplete.");
  return certificateFields(parts);
}

function parseHeaders(source: string): Omit<Part, "data"> {
  let disposition = "";
  let contentType: string | undefined;
  for (const line of source.split("\r\n")) {
    const separator = line.indexOf(":");
    if (separator < 1 || separator > 128) throw invalid();
    const name = line.slice(0, separator).trim().toLowerCase();
    const value = line.slice(separator + 1).trim();
    if (name === "content-disposition") disposition = value;
    else if (name === "content-type") contentType = value;
  }
  if (!/^form-data\b/i.test(disposition)) throw invalid();
  const fieldName = /(?:^|;)\s*name=(?:"([^"]*)"|([^;\s]*))/i.exec(disposition);
  if (!fieldName || !(fieldName[1] ?? fieldName[2])) throw invalid();
  const filename = /(?:^|;)\s*filename=(?:"([^"]*)"|([^;\s]*))/i.exec(
    disposition,
  );
  return {
    name: fieldName[1] ?? fieldName[2],
    filename: filename ? (filename[1] ?? filename[2]) : undefined,
    contentType,
  };
}

function certificateFields(parts: Part[]): CertificateUpload {
  const fields = new Map<string, string>();
  let file: CertificateUpload["file"] | undefined;
  for (const part of parts) {
    if (part.name === "certificate") {
      if (file || !part.filename) throw invalid("Choose one certificate file.");
      if (part.data.length === 0) throw invalid("The certificate file is empty.");
      if (part.data.length > 1024 * 1024)
        throw new PortalFailure(
          413,
          "UPLOAD_TOO_LARGE",
          "Certificate files are limited to 1 MiB.",
        );
      file = {
        filename: part.filename,
        contentType: part.contentType ?? "application/x-pem-file",
        data: part.data,
      };
      continue;
    }
    if (!["name", "type", "password"].includes(part.name) || part.filename)
      throw invalid();
    if (fields.has(part.name)) throw invalid("Each upload field may appear once.");
    if (part.data.length > MAX_FIELD_BYTES) throw invalid();
    try {
      fields.set(part.name, new TextDecoder("utf-8", { fatal: true }).decode(part.data));
    } catch {
      throw invalid();
    }
  }
  if (!file) throw invalid("Choose a certificate file.");
  return {
    name: fields.get("name") ?? "",
    type: fields.get("type") ?? "",
    password: fields.get("password") ?? "",
    file,
  };
}

/** Rebuilds a canonical multipart body; arbitrary client parts never reach Java. */
export function buildCertificateMultipart(upload: CertificateUpload) {
  const boundary = randomBytes(18).toString("hex");
  const chunks: Buffer[] = [];
  const field = (name: string, value: string) => {
    chunks.push(
      Buffer.from(
        `--${boundary}\r\nContent-Disposition: form-data; name="${name}"\r\n\r\n`,
      ),
      Buffer.from(value, "utf8"),
      Buffer.from("\r\n"),
    );
  };
  field("name", upload.name);
  field("type", upload.type);
  field("password", upload.password);
  chunks.push(
    Buffer.from(
      `--${boundary}\r\nContent-Disposition: form-data; name="certificate"; filename="certificate.pem"\r\nContent-Type: application/x-pem-file\r\n\r\n`,
    ),
    upload.file.data,
    Buffer.from(`\r\n--${boundary}--\r\n`),
  );
  return { boundary, body: Buffer.concat(chunks) };
}
