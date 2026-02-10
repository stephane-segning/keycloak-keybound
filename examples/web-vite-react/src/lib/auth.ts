import { CLIENT_ID, KEYCLOAK_BASE_URL, KEYCLOAK_REALM, REDIRECT_URI } from '../config';

export const TOKEN_STORAGE_KEY = 'keybound.tokens';

export type TokenResponse = {
  access_token: string;
  id_token?: string;
  refresh_token?: string;
  token_type?: string;
  expires_in?: number;
  scope?: string;
};

function decodeBase64Url(value: string): string {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/');
  const padding = normalized.length % 4 === 0 ? '' : '='.repeat(4 - (normalized.length % 4));
  return atob(normalized + padding);
}

function decodeJwt(token?: string): Record<string, unknown> {
  if (!token) return {};
  const parts = token.split('.');
  if (parts.length < 2) return {};
  try {
    return JSON.parse(decodeBase64Url(parts[1])) as Record<string, unknown>;
  } catch {
    return {};
  }
}

export async function exchangeAuthorizationCode(code: string): Promise<TokenResponse> {
  const codeVerifier = sessionStorage.getItem('code_verifier');
  if (!codeVerifier) {
    throw new Error('Missing code_verifier in sessionStorage');
  }

  const body = new URLSearchParams({
    grant_type: 'authorization_code',
    client_id: CLIENT_ID,
    code,
    redirect_uri: REDIRECT_URI,
    code_verifier: codeVerifier,
  });

  const endpoint = `${KEYCLOAK_BASE_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token`;
  const response = await fetch(endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: body.toString(),
  });

  const payload = (await response.json()) as TokenResponse & { error?: string; error_description?: string };
  if (!response.ok || !payload.access_token) {
    throw new Error(payload.error_description ?? payload.error ?? `Token endpoint failed with ${response.status}`);
  }

  return payload;
}

export async function fetchUserInfo(accessToken: string): Promise<Record<string, unknown>> {
  const endpoint = `${KEYCLOAK_BASE_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/userinfo`;
  const response = await fetch(endpoint, {
    method: 'GET',
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  });

  const payload = (await response.json()) as Record<string, unknown>;
  if (!response.ok) {
    throw new Error((payload.error_description as string) ?? `UserInfo failed with ${response.status}`);
  }
  return payload;
}

export function extractUserId(tokens: TokenResponse, userInfo?: Record<string, unknown>): string | null {
  const idClaims = decodeJwt(tokens.id_token);
  const accessClaims = decodeJwt(tokens.access_token);
  const fromId = idClaims.sub;
  const fromAccess = accessClaims.sub;
  const fromUserInfo = userInfo?.sub;
  const chosen = fromUserInfo ?? fromId ?? fromAccess;
  return typeof chosen === 'string' ? chosen : null;
}

export function saveTokens(tokens: TokenResponse) {
  sessionStorage.setItem(TOKEN_STORAGE_KEY, JSON.stringify(tokens));
}

export function loadTokens(): TokenResponse | null {
  const raw = sessionStorage.getItem(TOKEN_STORAGE_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as TokenResponse;
  } catch {
    return null;
  }
}
