// OAuth2 PKCE Authorization Code Flow

const AUTH_SERVER = '/services';
const CLIENT_ID = 'DC0E82E58CBF4E2F8C6679CFD5074059';
const REDIRECT_URI = `${window.location.origin}/callback`;
const SCOPES = 'openid profile email idp-admin';
const BRANDING = 'LOCAL';

// --- PKCE helpers ---

function generateRandomString(length: number): string {
  const array = new Uint8Array(length);
  crypto.getRandomValues(array);
  return Array.from(array, (b) => b.toString(16).padStart(2, '0')).join('').slice(0, length);
}

async function sha256(plain: string): Promise<ArrayBuffer> {
  const encoder = new TextEncoder();
  return crypto.subtle.digest('SHA-256', encoder.encode(plain));
}

function base64UrlEncode(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  bytes.forEach((b) => (binary += String.fromCharCode(b)));
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

// --- Token storage (in-memory for security) ---

interface TokenData {
  accessToken: string;
  refreshToken?: string;
  signingKey?: string; // Base64 encoded HMAC key
  expiresAt: number;
}

let tokenData: TokenData | null = null;

export function getToken(): TokenData | null {
  if (tokenData && tokenData.expiresAt > Date.now()) {
    return tokenData;
  }
  // Check sessionStorage for persistence across page reloads
  const stored = sessionStorage.getItem('auth_token');
  if (stored) {
    const parsed = JSON.parse(stored) as TokenData;
    if (parsed.expiresAt > Date.now()) {
      tokenData = parsed;
      return tokenData;
    }
    sessionStorage.removeItem('auth_token');
  }
  return null;
}

function setToken(data: TokenData) {
  tokenData = data;
  sessionStorage.setItem('auth_token', JSON.stringify(data));
}

export function clearToken() {
  tokenData = null;
  sessionStorage.removeItem('auth_token');
  sessionStorage.removeItem('pkce_verifier');
}

export function isAuthenticated(): boolean {
  return getToken() !== null;
}

// --- Authorization flow ---

export async function startAuthFlow() {
  const codeVerifier = generateRandomString(64);
  sessionStorage.setItem('pkce_verifier', codeVerifier);

  const challengeBuffer = await sha256(codeVerifier);
  const codeChallenge = base64UrlEncode(challengeBuffer);

  const now = new Date().toISOString();

  const params = new URLSearchParams({
    response_type: 'code',
    client_id: CLIENT_ID,
    redirect_uri: REDIRECT_URI,
    scope: SCOPES,
    code_challenge: codeChallenge,
    code_challenge_method: 'S256',
    'x-request-datetime': now,
    branding: BRANDING,
  });

  window.location.href = `${AUTH_SERVER}/oauth2/authorize?${params.toString()}`;
}

// --- Token exchange ---

export async function exchangeCodeForToken(code: string): Promise<boolean> {
  const codeVerifier = sessionStorage.getItem('pkce_verifier');
  if (!codeVerifier) {
    console.error('No PKCE verifier found');
    return false;
  }

  const body = new URLSearchParams({
    grant_type: 'authorization_code',
    code,
    redirect_uri: REDIRECT_URI,
    client_id: CLIENT_ID,
    code_verifier: codeVerifier,
  });

  try {
    const res = await fetch(`${AUTH_SERVER}/oauth2/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: body.toString(),
      credentials: 'same-origin',
    });

    if (!res.ok) {
      console.error('Token exchange failed:', res.status);
      return false;
    }

    const data = await res.json();

    setToken({
      accessToken: data.access_token,
      refreshToken: data.refresh_token,
      signingKey: data.signing_key,
      expiresAt: Date.now() + (data.expires_in || 3600) * 1000,
    });

    sessionStorage.removeItem('pkce_verifier');
    return true;
  } catch (e) {
    console.error('Token exchange error:', e);
    return false;
  }
}

// --- Request datetime header ---

export function getRequestDatetime(): string {
  return new Date().toISOString();
}
