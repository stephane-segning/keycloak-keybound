import {CLIENT_ID, KEYCLOAK_BASE_URL, KEYCLOAK_REALM, REDIRECT_URI} from '../config';
import {signPayload, stringifyPublicJwk} from './crypto';
import {createPrefixedId} from './id';
import {loadDeviceRecord} from './storage';

export const TOKEN_STORAGE_KEY = 'keybound.tokens';
export const GRANT_USER_ID_STORAGE_KEY = 'keybound.grant_user_id';
const DEVICE_KEY_GRANT_TYPE = 'urn:ssegning:params:oauth:grant-type:device_key';

export type TokenResponse = {
    access_token: string;
    id_token?: string;
    refresh_token?: string;
    token_type?: string;
    expires_in?: number;
    scope?: string;
};

// Decodes URL-safe Base64 JWT parts into plain JSON text.
function decodeBase64Url(value: string): string {
    const normalized = value.replace(/-/g, '+').replace(/_/g, '/');
    const padding = normalized.length % 4 === 0 ? '' : '='.repeat(4 - (normalized.length % 4));
    return atob(normalized + padding);
}

// Minimal JWT payload reader used only for local claim checks (exp/sub).
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

// Subject is the canonical user id we can safely re-use with the custom grant.
function extractSubject(tokens?: TokenResponse | null): string | null {
    if (!tokens) return null;
    const idClaims = decodeJwt(tokens.id_token);
    const accessClaims = decodeJwt(tokens.access_token);
    const candidate = idClaims.sub ?? accessClaims.sub;
    return typeof candidate === 'string' && candidate.length > 0 ? candidate : null;
}

// Keeps a small safety window so we renew before the token is actually invalid.
function isAccessTokenValid(tokens?: TokenResponse | null): boolean {
    if (!tokens?.access_token) return false;
    const claims = decodeJwt(tokens.access_token);
    const exp = claims.exp;
    if (typeof exp !== 'number') return false;
    const now = Math.floor(Date.now() / 1000);
    return exp > now + 15;
}

// Calls the custom device-key grant to mint a fresh access token.
async function callCustomGrant(userId: string): Promise<TokenResponse> {
    const device = await loadDeviceRecord();
    if (!device?.deviceId || !device.publicJwk || !device.privateJwk) {
        throw new Error('Missing device key material; login once to initialize this browser');
    }

    const ts = Math.floor(Date.now() / 1000).toString();
    const nonce = createPrefixedId('nce');
    const publicKey = stringifyPublicJwk(device.publicJwk);
    // Signature payload must match the server-side canonical verification payload.
    const signaturePayload = JSON.stringify({
        deviceId: device.deviceId,
        publicKey,
        ts,
        nonce,
    });
    const sig = await signPayload(device.privateJwk, signaturePayload);

    const body = new URLSearchParams({
        grant_type: DEVICE_KEY_GRANT_TYPE,
        client_id: CLIENT_ID,
        user_id: userId,
        device_id: device.deviceId,
        public_key: publicKey,
        ts,
        nonce,
        sig,
    });

    const endpoint = `${KEYCLOAK_BASE_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token`;
    const response = await fetch(endpoint, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: body.toString(),
    });

    const payload = (await response.json()) as TokenResponse & { error?: string; error_description?: string };
    if (!response.ok || !payload.access_token) {
        throw new Error(payload.error_description ?? payload.error ?? `Custom grant failed with ${response.status}`);
    }
    return payload;
}

export async function exchangeAuthorizationCode(code: string): Promise<TokenResponse> {
    const codeVerifier = sessionStorage.getItem('code_verifier');
    if (!codeVerifier) {
        throw new Error('Missing code_verifier in sessionStorage');
    }

    // Standard OIDC code exchange with PKCE verifier created at login-start time.
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
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
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
    // Prefer UserInfo subject, then token claims, to keep backend/device linkage stable.
    const idClaims = decodeJwt(tokens.id_token);
    const accessClaims = decodeJwt(tokens.access_token);
    const fromId = idClaims.sub;
    const fromAccess = accessClaims.sub;
    const fromUserInfo = userInfo?.sub;
    const chosen = fromUserInfo ?? fromId ?? fromAccess;
    return typeof chosen === 'string' ? chosen : null;
}

export function saveTokens(tokens: TokenResponse) {
    // Session storage keeps auth state browser-tab scoped for this demo.
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

export function saveGrantUserId(userId: string | null | undefined) {
    if (!userId) return;
    // Explicitly store the subject used by the custom grant fallback path.
    sessionStorage.setItem(GRANT_USER_ID_STORAGE_KEY, userId);
}

export async function ensureAccessToken(): Promise<string> {
    // Fast path: current token is still valid.
    const currentTokens = loadTokens();
    if (isAccessTokenValid(currentTokens)) {
        return currentTokens!.access_token;
    }

    // Renewal path: prefer token subject, then stored subject, then device-bound user id.
    const device = await loadDeviceRecord();
    const userId =
        extractSubject(currentTokens) ??
        sessionStorage.getItem(GRANT_USER_ID_STORAGE_KEY) ??
        device?.userId ??
        null;

    if (!userId) {
        throw new Error('No usable token and no stored user_id for custom grant; login is required');
    }

    const renewedTokens = await callCustomGrant(userId);
    // Replace session tokens with the new token set.
    saveTokens(renewedTokens);
    saveGrantUserId(userId);
    return renewedTokens.access_token;
}
