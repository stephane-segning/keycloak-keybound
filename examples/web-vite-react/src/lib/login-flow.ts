import {CLIENT_ID, KEYCLOAK_BASE_URL, KEYCLOAK_REALM, REDIRECT_URI} from "../config";
import {
    exchangeAuthorizationCode,
    extractUserId,
    fetchUserInfo,
    saveGrantUserId,
    saveLoginChallengeContext,
    saveTokens,
    TokenResponse
} from "./auth";
import {getCurrentOrigin, openCenteredPopup, resolveDevicePlatform} from "./browser-runtime";
import {DeviceSignaturePayload} from "./canonical-payloads";
import {signPayload, stringifyPublicJwk} from "./crypto";
import {DeviceRecord} from "./device-db";
import {createPrefixedId} from "./id";
import {createCodeChallenge, createCodeVerifier} from "./pkce";

export type AuthCallbackPayload = {
    type?: string;
    code?: string;
    error?: string;
    error_description?: string;
};

export type CompletedAuthResult = {
    tokens: TokenResponse;
    userInfo: Record<string, unknown>;
    userId: string | null;
};

export const readAuthCallbackPayload = (event: MessageEvent): AuthCallbackPayload | null => {
    if (event.origin !== getCurrentOrigin()) {
        return null;
    }

    const payload = event.data as AuthCallbackPayload;
    if (payload?.type !== "keybound-auth-callback") {
        return null;
    }

    return payload;
};

export const openLoginPopup = (url: string): Window | null =>
    openCenteredPopup(url, "keybound-login", 480, 760);

export const buildAuthorizationUrl = async (device: DeviceRecord): Promise<string> => {
    const ts = Math.floor(Date.now() / 1000).toString();
    const nonce = createPrefixedId("nce");
    const signaturePayload = new DeviceSignaturePayload(
        device.deviceId,
        stringifyPublicJwk(device.publicJwk),
        ts,
        nonce
    );
    const signature = await signPayload(device.privateJwk, signaturePayload.toCanonicalJson());

    const codeVerifier = createCodeVerifier();
    const codeChallenge = await createCodeChallenge(codeVerifier);
    saveLoginChallengeContext(codeVerifier, nonce, ts);

    const params = new URLSearchParams({
        scope: "openid profile email",
        response_type: "code",
        client_id: CLIENT_ID,
        redirect_uri: REDIRECT_URI,
        code_challenge: codeChallenge,
        code_challenge_method: "S256",
        state: createPrefixedId("stt"),
        device_id: device.deviceId,
        public_key: stringifyPublicJwk(device.publicJwk),
        ts,
        nonce,
        sig: signature,
        action: "login",
        aud: CLIENT_ID,
        device_os: resolveDevicePlatform(),
        device_model: "vite-react",
        user_hint: device.userId ?? "",
    });

    return `${KEYCLOAK_BASE_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/auth?${params.toString()}`;
};

export const completeAuthByCode = async (code: string): Promise<CompletedAuthResult> => {
    const tokens = await exchangeAuthorizationCode(code);
    const userInfo = await fetchUserInfo(tokens.access_token);
    saveTokens(tokens);

    const userId = extractUserId(tokens, userInfo);
    saveGrantUserId(userId);

    return {
        tokens,
        userInfo,
        userId,
    };
};
