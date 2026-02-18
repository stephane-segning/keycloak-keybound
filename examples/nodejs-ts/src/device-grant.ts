import crypto from 'node:crypto';
import {base64url, derToRawEcdsaSignature, env, printBashExports, randomBase64Url} from './lib.js';

const KEYCLOAK_BASE_URL = env('KEYCLOAK_BASE_URL', 'http://localhost:9026');
const REALM = env('REALM', 'e2e-testing');
const DEVICE_LOGIN_ENDPOINT = `${KEYCLOAK_BASE_URL.replace(/\/$/, '')}/realms/${encodeURIComponent(REALM)}/device-public-key-login`;

const DEVICE_OS_DEFAULT = 'ios';
const DEVICE_MODEL_DEFAULT = 'simulator';
const POW_DIFFICULTY =
    Number(
        env(
            `PUBLIC_KEY_LOGIN_POW_DIFFICULTY_${REALM}`,
            env('PUBLIC_KEY_LOGIN_POW_DIFFICULTY', '4')
        )
    );

async function main() {
    const args = parseArgs(process.argv.slice(2));
    const {publicKey, privateKey} = crypto.generateKeyPairSync('ec', {namedCurve: 'prime256v1'});
    const publicJwk = publicKey.export({format: 'jwk'}) as {kty: 'EC'; crv: 'P-256'; x: string; y: string};
    if (publicJwk.kty !== 'EC' || publicJwk.crv !== 'P-256' || !publicJwk.x || !publicJwk.y) {
        throw new Error('Unexpected public JWK');
    }

    const deviceId = args.deviceId ?? `dev_${randomBase64Url(12)}`;
    const ts = Math.floor(Date.now() / 1000).toString();
    const nonce = randomBase64Url(16);
    const signaturePayload = canonicalDeviceSignaturePayload({deviceId, publicKey: stringifyPublicJwk(publicJwk), ts, nonce});
    const sig = signPayload(signaturePayload, privateKey);

    const body: Record<string, string> = {
        device_id: deviceId,
        public_key: signaturePayloadPublicKey(publicJwk),
        nonce,
        ts,
        sig,
        device_os: args.deviceOs ?? DEVICE_OS_DEFAULT,
        device_model: args.deviceModel ?? DEVICE_MODEL_DEFAULT,
    };
    if (args.clientId) {
        body.client_id = args.clientId;
    }
    if (args.deviceAppVersion) {
        body.device_app_version = args.deviceAppVersion;
    }
    if (args.powNonce) {
        body.pow_nonce = args.powNonce;
    }

    if (POW_DIFFICULTY > 0 && !body.pow_nonce) {
        console.log(`Solving PoW difficulty ${POW_DIFFICULTY} for realm ${REALM}...`);
        const powNonce = solvePowNonce(POW_DIFFICULTY, REALM, deviceId, ts, nonce);
        body.pow_nonce = powNonce;
    }

    console.log('Calling device-public-key-login with payload:', body);
    const response = await fetch(DEVICE_LOGIN_ENDPOINT, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(body),
    });

    const responseBody = await response.text();
    let parsed: Record<string, unknown> | null = null;
    try {
        parsed = JSON.parse(responseBody);
    } catch {
        parsed = null;
    }

    if (!response.ok) {
        const message = parsed?.error as string | undefined ?? response.statusText;
        throw new Error(`Device login failed (${response.status}): ${message}`);
    }

    console.log('Device login succeeded:', parsed ?? responseBody);
    printBashExports('KC_DEVICE_', {
        device_id: deviceId,
        public_key: signaturePayloadPublicKey(publicJwk),
        user_id: parsed?.user_id ?? '',
    });
}

function signPayload(payload: string, privateKey: crypto.KeyObject): string {
    const derSignature = crypto.sign('sha256', Buffer.from(payload, 'utf8'), privateKey);
    const rawSignature = derToRawEcdsaSignature(derSignature, 32);
    return base64url(rawSignature);
}

function canonicalDeviceSignaturePayload(data: {deviceId: string; publicKey: string; ts: string; nonce: string}): string {
    return JSON.stringify({
        nonce: data.nonce,
        deviceId: data.deviceId,
        ts: data.ts,
        publicKey: data.publicKey,
    });
}

function stringifyPublicJwk(jwk: {kty: string; crv: string; x: string; y: string}): string {
    return `{"crv":"${jwk.crv}","kty":"${jwk.kty}","x":"${jwk.x}","y":"${jwk.y}"}`;
}

function signaturePayloadPublicKey(jwk: {crv: string; kty: string; x: string; y: string}): string {
    return stringifyPublicJwk(jwk);
}

function solvePowNonce(
    difficulty: number,
    realm: string,
    deviceId: string,
    ts: string,
    nonce: string,
): string {
    const target = '0'.repeat(difficulty);
    let counter = 0;

    while (counter < 3_000_000) {
        const candidate = `pow_${counter.toString(16)}`;
        const material = `${realm}:${deviceId}:${ts}:${nonce}:${candidate}`;
        const hashHex = crypto.createHash('sha256').update(material).digest('hex');
        if (hashHex.startsWith(target)) {
            return candidate;
        }
        counter++;
    }

    throw new Error(`PoW solver exhausted for difficulty ${difficulty} (${counter} attempts)`);
}

function parseArgs(argv: string[]): {deviceId?: string; clientId?: string; deviceOs?: string; deviceModel?: string; deviceAppVersion?: string; powNonce?: string} {
    const out: {deviceId?: string; clientId?: string; deviceOs?: string; deviceModel?: string; deviceAppVersion?: string; powNonce?: string} = {};
    for (let i = 0; i < argv.length; i++) {
        const arg = argv[i];
        if (arg === '--device-id') out.deviceId = argv[++i];
        else if (arg === '--client-id') out.clientId = argv[++i];
        else if (arg === '--device-os') out.deviceOs = argv[++i];
        else if (arg === '--device-model') out.deviceModel = argv[++i];
        else if (arg === '--device-app-version') out.deviceAppVersion = argv[++i];
        else if (arg === '--pow-nonce') out.powNonce = argv[++i];
    }
    return out;
}

main().catch((error) => {
    console.error(error);
    process.exit(1);
});
