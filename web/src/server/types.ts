import "server-only";
export type BrowserContext = {
  userAgent: string;
  acceptLanguage: string;
  externalOrigin: string;
};
export type PortalSession = {
  id: string;
  subjectId: string;
  accessToken: string;
  idToken: string;
  signingKey: Uint8Array;
  expiresAt: number;
  createdAt: number;
  lastSeenAt: number;
  browserContext: BrowserContext;
  csrfSecret: string;
};
export type LoginTransaction = {
  state: string;
  binding: string;
  nonce: string;
  verifier: string;
  returnTo: string;
  context: BrowserContext;
  expiresAt: number;
};
export type LogoutTransaction = {
  state: string;
  binding: string;
  idToken: string;
  expiresAt: number;
};
