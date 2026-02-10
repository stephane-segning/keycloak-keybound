import crypto from 'node:crypto';
import http from 'node:http';
import {
    allowInsecureRequests,
    authorizationCodeGrant,
    buildAuthorizationUrl,
    discovery,
    fetchUserInfo,
    skipSubjectCheck,
} from 'openid-client';
import {
    base64url,
    derToRawEcdsaSignature,
    env,
    issuerUrl,
    printBashExports,
    toFormUrlEncoded,
    publicJwkStringForKeycloak,
    randomBase64Url,
    sha256Base64Url,
    tokenEndpoint,
} from './lib.js';

const KEYCLOAK_BASE_URL = env('KEYCLOAK_BASE_URL', 'http://localhost:9026');
const REALM = env('REALM', 'ssegning-keybound-wh-01');
const CLIENT_ID = env('CLIENT_ID', 'node-cli');
const REDIRECT_URI = env('REDIRECT_URI', 'http://localhost:3005/callback');

type PublicJwk = { kty: 'EC'; crv: 'P-256'; x: string; y: string };
const DEVICE_KEY_GRANT_TYPE = 'urn:ssegning:params:oauth:grant-type:device_key';

async function main() {
    const args = parseArgs(process.argv.slice(2));
    const userHint = args.userHint ?? 'test';
    const bash = args.bash ?? false;

    const issuer = issuerUrl(KEYCLOAK_BASE_URL, REALM);
    const config = await discovery(new URL(issuer), CLIENT_ID, undefined, undefined, {
        execute: [allowInsecureRequests],
    });

    const {publicKey, privateKey} = crypto.generateKeyPairSync('ec', {namedCurve: 'prime256v1'});
    const publicJwk = publicKey.export({format: 'jwk'}) as PublicJwk;
    if (publicJwk.kty !== 'EC' || publicJwk.crv !== 'P-256' || !publicJwk.x || !publicJwk.y) {
        throw new Error('Unexpected public JWK');
    }

    const deviceId = args.deviceId ?? `dev_${randomBase64Url(12)}`;
    const ts = Math.floor(Date.now() / 1000).toString();
    const nonce = randomBase64Url(16);
    const state = randomBase64Url(16);
    const codeVerifier = randomBase64Url(32);
    const codeChallenge = sha256Base64Url(codeVerifier);

    const publicKeyJwk = publicJwkStringForKeycloak(publicJwk);
    const signaturePayload = JSON.stringify({
        deviceId,
        publicKey: publicKeyJwk,
        ts,
        nonce,
    });
    const sig = signPayload(signaturePayload, privateKey);

    const authUrl = buildAuthorizationUrl(config, {
        scope: 'openid profile email',
        response_type: 'code',
        redirect_uri: REDIRECT_URI,
        code_challenge: codeChallenge,
        code_challenge_method: 'S256',
        state,
        user_hint: userHint,
        device_id: deviceId,
        public_key: publicKeyJwk,
        ts,
        nonce,
        sig,
        action: args.action ?? 'login',
        aud: args.aud ?? CLIENT_ID,
        device_os: args.deviceOs ?? 'ios',
        device_model: args.deviceModel ?? 'simulator',
    });

    process.stdout.write(`Open this URL in your browser:\n${authUrl}\n\n`);

    const callbackUrl = await waitForCallback(new URL(REDIRECT_URI), state);
    const tokenSet = await authorizationCodeGrant(
        config,
        new URL(callbackUrl),
        {pkceCodeVerifier: codeVerifier, expectedState: state, expectedNonce: nonce}
    );

    const accessToken = (tokenSet as any).access_token as string | undefined;
    if (!accessToken) {
        throw new Error('Missing access_token');
    }

    const userInfo = await fetchUserInfo(config, accessToken, skipSubjectCheck);
    const grantUserId =
        args.grantUserId ??
        ((userInfo as any).sub as string | undefined);
    if (!grantUserId) {
        throw new Error('UserInfo did not include sub; cannot call custom grant without user_id');
    }

    const grantTs = Math.floor(Date.now() / 1000).toString();
    const grantNonce = randomBase64Url(16);
    const grantSignaturePayload = JSON.stringify({
        deviceId,
        publicKey: publicKeyJwk,
        ts: grantTs,
        nonce: grantNonce,
    });
    const grantSig = signPayload(grantSignaturePayload, privateKey);
    const customGrantToken = await callCustomGrant({
        issuer,
        clientId: CLIENT_ID,
        userId: grantUserId,
        deviceId,
        publicKey: publicKeyJwk,
        ts: grantTs,
        nonce: grantNonce,
        sig: grantSig,
    });

    process.stdout.write(`Auth Code token response:\n${JSON.stringify(tokenSet, null, 2)}\n\n`);
    process.stdout.write(`UserInfo:\n${JSON.stringify(userInfo, null, 2)}\n\n`);
    process.stdout.write(`Custom grant token response:\n${JSON.stringify(customGrantToken, null, 2)}\n\n`);

    if (bash) {
        printBashExports('KC_DEVICE_', {
            issuer,
            user_hint: userHint,
            user_id: grantUserId,
            device_id: deviceId,
            public_key: publicKeyJwk,
            auth_code_access_token: (tokenSet as any).access_token,
            auth_code_refresh_token: (tokenSet as any).refresh_token,
            auth_code_id_token: (tokenSet as any).id_token,
            auth_code_token_type: (tokenSet as any).token_type,
            auth_code_expires_in: (tokenSet as any).expires_in,
            custom_grant_access_token: (customGrantToken as any).access_token,
            custom_grant_id_token: (customGrantToken as any).id_token,
            custom_grant_token_type: (customGrantToken as any).token_type,
            custom_grant_expires_in: (customGrantToken as any).expires_in,
            sub: (userInfo as any).sub,
        });
    }
}

function parseArgs(argv: string[]): {
    userHint?: string;
    deviceId?: string;
    deviceOs?: string;
    deviceModel?: string;
    action?: string;
    aud?: string;
    grantUserId?: string;
    bash?: boolean;
} {
    const out: {
        userHint?: string;
        deviceId?: string;
        deviceOs?: string;
        deviceModel?: string;
        action?: string;
        aud?: string;
        grantUserId?: string;
        bash?: boolean;
    } = {};

    for (let i = 0; i < argv.length; i++) {
        const arg = argv[i];
        if (arg === '--username' || arg === '--user-hint') out.userHint = argv[++i];
        else if (arg === '--device-id') out.deviceId = argv[++i];
        else if (arg === '--device-os') out.deviceOs = argv[++i];
        else if (arg === '--device-model') out.deviceModel = argv[++i];
        else if (arg === '--action') out.action = argv[++i];
        else if (arg === '--aud') out.aud = argv[++i];
        else if (arg === '--grant-user-id') out.grantUserId = argv[++i];
        else if (arg === '--bash') out.bash = true;
    }

    return out;
}

function signPayload(payload: string, privateKey: crypto.KeyObject): string {
    const derSignature = crypto.sign('sha256', Buffer.from(payload, 'utf8'), privateKey);
    const rawSignature = derToRawEcdsaSignature(derSignature, 32);
    return base64url(rawSignature);
}

async function callCustomGrant(params: {
    issuer: string;
    clientId: string;
    userId: string;
    deviceId: string;
    publicKey: string;
    ts: string;
    nonce: string;
    sig: string;
}): Promise<Record<string, unknown>> {
    const response = await fetch(tokenEndpoint(params.issuer), {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: toFormUrlEncoded({
            grant_type: DEVICE_KEY_GRANT_TYPE,
            client_id: params.clientId,
            user_id: params.userId,
            device_id: params.deviceId,
            public_key: params.publicKey,
            ts: params.ts,
            nonce: params.nonce,
            sig: params.sig,
        }),
    });

    const responseText = await response.text();
    let json: Record<string, unknown> | null = null;
    try {
        json = JSON.parse(responseText) as Record<string, unknown>;
    } catch {
        // Keep plain text for error surfacing below.
    }

    if (!response.ok) {
        const message = typeof json?.error_description === 'string'
            ? json.error_description
            : responseText;
        throw new Error(`Custom grant failed (${response.status}): ${message}`);
    }

    if (!json || typeof json.access_token !== 'string') {
        throw new Error('Custom grant succeeded but response has no access_token');
    }
    return json;
}

function waitForCallback(redirectUri: URL, expectedState: string): Promise<string> {
    return new Promise((resolve, reject) => {
        const server = http.createServer((req, res) => {
            try {
                const callbackUrl = new URL(req.url ?? '/', redirectUri);
                const state = callbackUrl.searchParams.get('state');
                if (state !== expectedState) {
                    res.statusCode = 400;
                    res.setHeader('Content-Type', 'text/plain');
                    res.end('Invalid state');
                    return;
                }

                res.statusCode = 200;
                res.setHeader('Content-Type', 'text/plain');
                res.end('OK. You can close this tab.');
                server.close();
                resolve(callbackUrl.toString());
            } catch (error) {
                server.close();
                reject(error);
            }
        });

        const port = Number(redirectUri.port || 80);
        server.listen(port, redirectUri.hostname);
    });
}

main().catch((error) => {
    console.error(error);
    process.exit(1);
});
