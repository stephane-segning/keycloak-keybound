import crypto from 'node:crypto';
import http from 'node:http';
import {
    allowInsecureRequests,
    authorizationCodeGrant,
    buildAuthorizationUrl,
    discovery,
    fetchUserInfo,
    skipSubjectCheck
} from 'openid-client';
import {
    base64url,
    derToRawEcdsaSignature,
    env,
    issuerUrl,
    jwkThumbprintES256,
    printBashExports,
    publicJwkStringForKeycloak,
    randomBase64Url,
    sha256Base64Url,
    toFormUrlEncoded,
} from './lib.js';

const KEYCLOAK_BASE_URL = env('KEYCLOAK_BASE_URL', 'http://localhost:9026');
const REALM = env('REALM', 'ssegning-keybound-wh-01');
const CLIENT_ID = env('CLIENT_ID', 'node-cli');
const REDIRECT_URI = env('REDIRECT_URI', 'http://localhost:3005/callback');
const WIREMOCK_ADMIN = env('WIREMOCK_ADMIN', 'http://localhost:8080/__admin');

const GRANT_TYPE = 'urn:ssegning:params:oauth:grant-type:device_key';

type PublicJwk = { kty: 'EC'; crv: 'P-256'; x: string; y: string };

async function main() {
    const args = parseArgs(process.argv.slice(2));
    const username = args.username ?? 'test';
    const bash = args.bash ?? false;

    const issuer = issuerUrl(KEYCLOAK_BASE_URL, REALM);
    const config = await discovery(new URL(issuer), CLIENT_ID, undefined, undefined, {
        execute: [allowInsecureRequests],
    });

    // 1) Standard flow to obtain user identity (`sub`).
    const {tokenSet, userinfo} = await authCodePkce(config, username);
    const sub = (userinfo as any).sub as string;
    if (!sub) {
        throw new Error('Missing userinfo.sub');
    }

    // 2) Generate device keypair.
    const {publicKey, privateKey} = crypto.generateKeyPairSync('ec', {namedCurve: 'prime256v1'});
    const publicJwk = publicKey.export({format: 'jwk'}) as PublicJwk;
    if (publicJwk.kty !== 'EC' || publicJwk.crv !== 'P-256' || !publicJwk.x || !publicJwk.y) {
        throw new Error('Unexpected public JWK');
    }

    const deviceId = args.deviceId ?? `dev_${randomBase64Url(12)}`;
    const jkt = jwkThumbprintES256(publicJwk);

    // 3) Register a WireMock stub so Keycloak can resolve /v1/devices/lookup for this deviceId.
    await registerLookupStub({
        deviceId,
        userId: sub,
        jkt,
        publicJwk,
    });

    // 4) Create signature input exactly as Keycloak's DeviceKeyGrantType expects.
    const ts = Math.floor(Date.now() / 1000).toString();
    const nonce = randomBase64Url(16);
    const publicKeyJwk = publicJwkStringForKeycloak(publicJwk);

    const canonicalData = {
        deviceId,
        publicKey: publicKeyJwk,
        ts,
        nonce,
    };
    const canonicalString = JSON.stringify(canonicalData);

    const derSig = crypto.sign('sha256', Buffer.from(canonicalString, 'utf8'), privateKey);
    const rawSig = derToRawEcdsaSignature(derSig, 32);
    const sig = base64url(rawSig);

    // 5) Call the custom grant.
    const tokenUrl = `${issuer}/protocol/openid-connect/token`;
    const body = toFormUrlEncoded({
        grant_type: GRANT_TYPE,
        client_id: CLIENT_ID,
        username,
        device_id: deviceId,
        ts,
        nonce,
        sig,
    });

    const resp = await fetch(tokenUrl, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body,
    });

    const json = await resp.json().catch(() => ({}));
    if (!resp.ok) {
        throw new Error(`device_key grant failed: ${resp.status} ${JSON.stringify(json)}`);
    }

    process.stdout.write(`device_key token response:\n${JSON.stringify(json, null, 2)}\n\n`);

    if (bash) {
        printBashExports('KC_DEVICE_', json as Record<string, unknown>);
    }
}

async function authCodePkce(client: any, _username: string) {
    const codeVerifier = randomBase64Url(32);
    const codeChallenge = sha256Base64Url(codeVerifier);
    const state = randomBase64Url(16);

    const authUrl = buildAuthorizationUrl(client, {
        scope: 'openid profile email',
        response_type: 'code',
        redirect_uri: REDIRECT_URI,
        code_challenge: codeChallenge,
        code_challenge_method: 'S256',
        state,
    });

    process.stdout.write(`Open this URL in your browser (login as ${_username}):\n${authUrl}\n\n`);

    const callbackUrl = await waitForCallback(new URL(REDIRECT_URI), state);
    const tokenSet = await authorizationCodeGrant(
        client,
        new URL(callbackUrl),
        {pkceCodeVerifier: codeVerifier, expectedState: state}
    );

    const accessToken = (tokenSet as any).access_token as string | undefined;
    if (!accessToken) {
        throw new Error('Missing access_token');
    }

    const userinfo = await fetchUserInfo(client, accessToken, skipSubjectCheck);
    return {tokenSet, userinfo};
}

async function registerLookupStub(input: { deviceId: string; userId: string; jkt: string; publicJwk: PublicJwk }) {
    const mapping = {
        request: {
            method: 'POST',
            url: '/v1/devices/lookup',
            bodyPatterns: [{matchesJsonPath: `$[?(@.device_id == '${input.deviceId}')]`}],
        },
        response: {
            status: 200,
            headers: {'Content-Type': 'application/json'},
            jsonBody: {
                found: true,
                user_id: input.userId,
                device: {
                    device_id: input.deviceId,
                    jkt: input.jkt,
                    status: 'ACTIVE',
                    created_at: new Date().toISOString(),
                    label: 'dev-wiremock',
                },
                public_jwk: {
                    crv: input.publicJwk.crv,
                    kty: input.publicJwk.kty,
                    x: input.publicJwk.x,
                    y: input.publicJwk.y,
                },
            },
        },
    };

    const resp = await fetch(`${WIREMOCK_ADMIN.replace(/\/$/, '')}/mappings`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(mapping),
    });
    if (!resp.ok) {
        const text = await resp.text().catch(() => '');
        throw new Error(`Failed to register WireMock mapping: ${resp.status} ${text}`);
    }
}

function parseArgs(argv: string[]): { username?: string; deviceId?: string; bash?: boolean } {
    const out: { username?: string; deviceId?: string; bash?: boolean } = {};
    for (let i = 0; i < argv.length; i++) {
        const a = argv[i];
        if (a === '--username') out.username = argv[++i];
        else if (a === '--device-id') out.deviceId = argv[++i];
        else if (a === '--bash') out.bash = true;
    }
    return out;
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
            } catch (e) {
                server.close();
                reject(e);
            }
        });

        const port = Number(redirectUri.port || 80);
        const hostname = redirectUri.hostname;
        server.listen(port, hostname);
    });
}

main().catch((e) => {
    console.error(e);
    process.exit(1);
});
