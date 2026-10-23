import { expect, it } from "vitest";
import { mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { execFileSync } from "node:child_process";
import { sign, createPrivateKey } from "node:crypto";
import { verifyTokenBody } from "../../src/server/auth";
it("verifies pinned RSA and EC P1363 certificates and rejects missing wrong or reserialized signatures", () => {
  const directory = mkdtempSync(join(tmpdir(), "portal-certificate-"));
  try {
    const certs = ["RSA", "EC"].map((type, index) => {
      const key = join(directory, index + ".key"),
        cert = join(directory, index + ".pem");
      execFileSync(
        "openssl",
        [
          "req",
          "-x509",
          "-newkey",
          type === "RSA" ? "rsa:2048" : "ec",
          ...(type === "EC" ? ["-pkeyopt", "ec_paramgen_curve:P-256"] : []),
          "-nodes",
          "-keyout",
          key,
          "-out",
          cert,
          "-subj",
          "/CN=Portal-Test",
          "-days",
          "1",
        ],
        { stdio: "ignore" },
      );
      return {
        key: createPrivateKey(readFileSync(key)),
        cert: readFileSync(cert, "utf8"),
      };
    });
    const raw = Buffer.from('{ "access_token": "fixture", "expires_in": 300 }');
    for (const [index, { key, cert }] of certs.entries()) {
      const signature = sign(
        "sha256",
        raw,
        key.asymmetricKeyType === "ec"
          ? { key, dsaEncoding: "ieee-p1363" }
          : key,
      ).toString("base64");
      expect(() => verifyTokenBody(raw, signature, cert)).not.toThrow();
      expect(() => verifyTokenBody(raw, null, cert)).toThrow();
      expect(() =>
        verifyTokenBody(raw, signature, certs[1 - index].cert),
      ).toThrow();
      expect(() =>
        verifyTokenBody(
          Buffer.from(JSON.stringify(JSON.parse(raw.toString()))),
          signature,
          cert,
        ),
      ).toThrow();
    }
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});
