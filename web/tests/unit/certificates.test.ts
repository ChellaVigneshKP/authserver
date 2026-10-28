import { describe, expect, it } from "vitest";
import {
  CertificateDetail,
  CertificateForm,
  CertificateList,
  CertificateName,
  CertificatePassword,
  MAX_CERTIFICATE_FILE_BYTES,
  certificateDownloadFilename,
  certificateExpiry,
  certificateStatusLabel,
} from "../../src/contracts/certificates";
import {
  buildCertificateMultipart,
  parseCertificateMultipart,
  readBoundedRequest,
} from "../../src/server/multipart";

const certificate = Buffer.from(
  "-----BEGIN CERTIFICATE-----\nsynthetic\n-----END CERTIFICATE-----\n",
);
const upload = {
  name: "Signing certificate",
  type: "ORGANIZATION",
  password: "synthetic-password",
  file: {
    filename: "ignored.pem",
    contentType: "application/x-pem-file",
    data: certificate,
  },
};

function file(size: number) {
  return new File([new Uint8Array(size).fill(65)], "certificate.pem", {
    type: "application/x-pem-file",
  });
}

describe("certificate wire contract", () => {
  const row = {
    id: "ca15463e-f36b-1410-8ed8-00fc9a23848b",
    name: "Signing certificate",
    type: "ORGANIZATION",
    status: "ACTIVE",
    validTO: "2030-01-02 03:04:05",
    isExpired: false,
  };
  it("accepts Java's duplicate Lombok boolean alias while keeping isExpired authoritative", () => {
    expect(CertificateList.parse([{ ...row, expired: false }])).toHaveLength(1);
    expect(
      CertificateDetail.parse({
        ...row,
        expired: false,
        fingerprint: "ab",
        thumbprint: "cd",
        certificate: "ZGVy",
      }).isExpired,
    ).toBe(false);
  });
  it("still rejects undocumented drift", () => {
    expect(() => CertificateList.parse([{ ...row, unexpected: true }])).toThrow();
  });
});

describe("certificate expiry", () => {
  it("treats naive SQL datetimes as UTC and date-only values as UTC midnight", () => {
    expect(certificateExpiry("2030-01-02 03:04:05")?.toISOString()).toBe(
      "2030-01-02T03:04:05.000Z",
    );
    expect(certificateExpiry("2030-01-02T03:04:05.1234567")?.toISOString()).toBe(
      "2030-01-02T03:04:05.123Z",
    );
    expect(certificateExpiry("2030-01-02")?.toISOString()).toBe(
      "2030-01-02T00:00:00.000Z",
    );
    expect(certificateExpiry("2030-01-02T03:04:05+05:30")?.toISOString()).toBe(
      "2030-01-01T21:34:05.000Z",
    );
    expect(certificateExpiry("2030-01-02T03:04:05Z")?.toISOString()).toBe(
      "2030-01-02T03:04:05.000Z",
    );
  });
  it("fails closed for missing, malformed and impossible values", () => {
    for (const value of [
      "",
      "not-a-date",
      "2030-13-02",
      "2030-02-30",
      "2030-01-02 24:00:00",
      "2030-01-02 23:60:00",
      "2030-01-02 23:59:60",
    ])
      expect(certificateExpiry(value)).toBeNull();
  });
});

describe("certificate field bounds", () => {
  it("enforces the 255-character name without control characters", () => {
    expect(CertificateName.parse("a".repeat(255))).toHaveLength(255);
    expect(CertificateName.safeParse("a".repeat(256)).success).toBe(false);
    expect(CertificateName.safeParse("bad\u0007name").success).toBe(false);
    expect(CertificateName.parse("  trimmed  ")).toBe("trimmed");
  });
  it("accepts a 1024-character password and rejects line breaks", () => {
    expect(CertificatePassword.safeParse("p".repeat(1024)).success).toBe(true);
    expect(CertificatePassword.safeParse("p".repeat(1025)).success).toBe(false);
    expect(CertificatePassword.safeParse("line\r\nbreak").success).toBe(false);
  });
  it("accepts a 1 MiB certificate file and rejects a larger one", () => {
    const value = (size: number) => ({
      name: "Name",
      type: "PUBLIC_KEY" as const,
      password: "",
      certificate: file(size),
    });
    expect(CertificateForm.safeParse(value(1)).success).toBe(true);
    expect(
      CertificateForm.safeParse(value(MAX_CERTIFICATE_FILE_BYTES)).success,
    ).toBe(true);
    expect(
      CertificateForm.safeParse(value(MAX_CERTIFICATE_FILE_BYTES + 1)).success,
    ).toBe(false);
    expect(CertificateForm.safeParse(value(0)).success).toBe(false);
  });
  it("describes status and expiry without inventing usability", () => {
    expect(certificateStatusLabel("INACTIVE", false)).toBe(
      "Inactive (deactivated)",
    );
    expect(certificateStatusLabel("ACTIVE", true)).toBe(
      "Expired by validity date",
    );
    expect(certificateStatusLabel("ACTIVE", false)).toBe("Active");
  });
});

describe("certificate multipart", () => {
  it("round-trips a canonical body and ignores the client filename", () => {
    const body = buildCertificateMultipart(upload);
    expect(body.boundary).toMatch(/^[0-9a-f]{16,70}$/);
    expect(
      parseCertificateMultipart(
        body.body,
        "multipart/form-data; boundary=" + body.boundary,
      ),
    ).toEqual({ ...upload, file: { ...upload.file, filename: "certificate.pem" } });
  });
  it("requires multipart with a parseable boundary", () => {
    expect(() => parseCertificateMultipart(Buffer.alloc(0), "text/plain")).toThrow(
      expect.objectContaining({ status: 415 }),
    );
    expect(() =>
      parseCertificateMultipart(Buffer.alloc(0), "multipart/form-data"),
    ).toThrow(expect.objectContaining({ status: 400 }));
  });
  it("rejects oversized files before forwarding", () => {
    const body = buildCertificateMultipart({
      ...upload,
      file: { ...upload.file, data: Buffer.alloc(MAX_CERTIFICATE_FILE_BYTES + 1) },
    });
    expect(() =>
      parseCertificateMultipart(
        body.body,
        "multipart/form-data; boundary=" + body.boundary,
      ),
    ).toThrow(expect.objectContaining({ code: "UPLOAD_TOO_LARGE", status: 413 }));
  });
  it("rejects duplicate, unknown and missing parts", () => {
    const duplicate =
      "--b\r\nContent-Disposition: form-data; name=\"name\"\r\n\r\na\r\n" +
      "--b\r\nContent-Disposition: form-data; name=\"name\"\r\n\r\nb\r\n" +
      "--b\r\nContent-Disposition: form-data; name=\"type\"\r\n\r\nPUBLIC_KEY\r\n" +
      "--b\r\nContent-Disposition: form-data; name=\"certificate\"; filename=\"a.pem\"\r\n\r\nPEM\r\n--b--\r\n";
    expect(() =>
      parseCertificateMultipart(Buffer.from(duplicate), "multipart/form-data; boundary=b"),
    ).toThrow(expect.objectContaining({ status: 400 }));
    const unknown = duplicate.replace('name="name"', 'name="extra"');
    expect(() =>
      parseCertificateMultipart(Buffer.from(unknown), "multipart/form-data; boundary=b"),
    ).toThrow(expect.objectContaining({ status: 400 }));
    const canonical = buildCertificateMultipart(upload);
    const missing = canonical.body
      .toString()
      .replace(
        /name="certificate"; filename="certificate.pem"/,
        'name="notcertificate"; filename="certificate.pem"',
      );
    expect(() =>
      parseCertificateMultipart(
        Buffer.from(missing),
        "multipart/form-data; boundary=" + canonical.boundary,
      ),
    ).toThrow(expect.objectContaining({ status: 400 }));
  });
});

describe("bounded request reading", () => {
  it("rejects a declared over-budget request", async () => {
    const request = new Request("http://localhost/upload", {
      method: "POST",
      headers: { "content-length": "11" },
      body: "hello",
    });
    await expect(readBoundedRequest(request, 10)).rejects.toMatchObject({
      status: 413,
      code: "UPLOAD_TOO_LARGE",
    });
  });
  it("streams with a counter even when content-length lies", async () => {
    const request = new Request("http://localhost/upload", {
      method: "POST",
      headers: { "content-length": "1" },
      body: new ReadableStream({
        start(controller) {
          controller.enqueue(new Uint8Array(6));
          controller.enqueue(new Uint8Array(6));
          controller.close();
        },
      }),
      duplex: "half",
    } as RequestInit);
    await expect(readBoundedRequest(request, 10)).rejects.toMatchObject({
      status: 413,
      code: "UPLOAD_TOO_LARGE",
    });
  });
});

describe("download filename", () => {
  it("keeps only a flat sanitized stem and always ends in .pem", () => {
    expect(certificateDownloadFilename('attachment; filename="team cert.pem"')).toBe(
      "team_cert.pem",
    );
    expect(certificateDownloadFilename('attachment; filename="../../etc/passwd"')).toBe(
      "passwd.pem",
    );
    expect(certificateDownloadFilename(null)).toBe("certificate.pem");
    expect(certificateDownloadFilename('attachment; filename=".pem"')).toBe(
      "certificate.pem",
    );
  });
});
