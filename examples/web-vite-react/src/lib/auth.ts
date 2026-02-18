import {CLIENT_ID, KEYCLOAK_BASE_URL, KEYCLOAK_REALM} from '../config';
import axios, {AxiosError} from 'axios';
import {DeviceSignaturePayload} from './canonical-payloads';
import {signPayload, stringifyPublicJwk} from './crypto';
import {createPrefixedId} from './id';
import {loadDeviceRecord} from './storage';

export const TOKEN_STORAGE_KEY = 'keybound.tokens';
export const GRANT_USER_ID_STORAGE_KEY = 'keybound.grant_user_id';
const DEVICE_KEY_GRANT_TYPE = 'urn:ssegning:params:oauth:grant-type:device_key';
const authHttpClient = axios.create();

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

function extractSubject(tokens?: TokenResponse | null): string | null {
    if (!tokens) return null;
    const idClaims = decodeJwt(tokens.id_token);
    const accessClaims = decodeJwt(tokens.access_token);
    const candidate = idClaims.sub ?? accessClaims.sub;
    return typeof candidate === 'string' && candidate.length > 0 ? candidate : null;
}

function isAccessTokenValid(tokens?: TokenResponse | null): boolean {
    if (!tokens?.access_token) return false;
    const claims = decodeJwt(tokens.access_token);
    const exp = claims.exp;
    if (typeof exp !== 'number') return false;
    const now = Math.floor(Date.now() / 1000);
    return exp > now + 15;
}

async function callCustomGrant(userId: string): Promise<TokenResponse> {
    const device = await loadDeviceRecord();
    if (!device?.deviceId || !device.publicJwk || !device.privateJwk) {
        throw new Error('Missing device key material; login once to initialize this browser');
    }

    const ts = Math.floor(Date.now() / 1000).toString();
    const nonce = createPrefixedId('nce');
    const publicKey = stringifyPublicJwk(device.publicJwk);
    const signaturePayload = new DeviceSignaturePayload(device.deviceId, publicKey, ts, nonce);
    const sig = await signPayload(device.privateJwk, signaturePayload.toCanonicalJson());

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
    try {
        const response = await authHttpClient.post<TokenResponse>(endpoint, body.toString(), {
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        });
        if (!response.data.access_token) {
            throw new Error('Custom grant did not return an access token');
        }
        return response.data;
    } catch (error) {
        const axiosError = error as AxiosError<{error?: string; error_description?: string}>;
        const message =
            axiosError.response?.data?.error_description ??
            axiosError.response?.data?.error ??
            axiosError.message;
        throw new Error(message ?? 'Custom grant failed');
    }
}

export async function requestDeviceKeyAccessToken(userId: string): Promise<TokenResponse> {
    const tokens = await callCustomGrant(userId);
    saveTokens(tokens);
    saveGrantUserId(userId);
    return tokens;
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

export function saveGrantUserId(userId: string | null | undefined) {
    if (!userId) return;
    sessionStorage.setItem(GRANT_USER_ID_STORAGE_KEY, userId);
}

export async function ensureAccessToken(): Promise<string> {
    const currentTokens = loadTokens();
    if (isAccessTokenValid(currentTokens)) {
        return currentTokens!.access_token;
    }

    const device = await loadDeviceRecord();
    const userId =
        extractSubject(currentTokens) ??
        sessionStorage.getItem(GRANT_USER_ID_STORAGE_KEY) ??
        device?.userId ??
        null;

    if (!userId) {
        throw new Error('No usable token and no stored user_id for custom grant; login is required');
    }

    const renewedTokens = await requestDeviceKeyAccessToken(userId);
    return renewedTokens.access_token;
}
