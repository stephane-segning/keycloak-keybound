type Hashable = string | ArrayBuffer | Uint8Array;

async function digestSha256(input: Hashable): Promise<Uint8Array> {
    const buffer =
        typeof input === 'string' ? new TextEncoder().encode(input) : input instanceof ArrayBuffer ? new Uint8Array(input) : input;

    if (globalThis.crypto?.subtle) {
        const arrayBuffer = buffer.buffer.slice(buffer.byteOffset, buffer.byteOffset + buffer.byteLength) as ArrayBuffer;
        const digest = await globalThis.crypto.subtle.digest('SHA-256', arrayBuffer);
        return new Uint8Array(digest);
    }

    const {createHash} = await import('node:crypto');
    const hash = createHash('sha256').update(buffer).digest();
    return new Uint8Array(hash);
}

function toHex(bytes: Uint8Array): string {
    return Array.from(bytes)
        .map((byte) => byte.toString(16).padStart(2, '0'))
        .join('');
}

export interface PublicKeyLoginPayloadData {
    nonce: string;
    deviceId: string;
    username: string;
    ts: string;
    publicKey: string;
}

export function canonicalPublicKeyPayload(data: PublicKeyLoginPayloadData): string {
    return JSON.stringify({
        nonce: data.nonce,
        deviceId: data.deviceId,
        username: data.username,
        ts: data.ts,
        publicKey: data.publicKey,
    });
}

export interface PowParams {
    realm: string;
    deviceId: string;
    username: string;
    ts: string;
    nonce: string;
    difficulty: number;
}

export async function solvePowNonce(params: PowParams): Promise<string | undefined> {
    if (params.difficulty <= 0) {
        return undefined;
    }

    let counter = 0;
    while (counter < 3_000_000) {
        const candidate = `pow_${counter.toString(16)}`;
        const material =
            `${params.realm}:${params.deviceId}:${params.username}:${params.ts}:${params.nonce}:${candidate}`;
        const hashHex = toHex(await digestSha256(material));
        if (hashHex.startsWith('0'.repeat(params.difficulty))) {
            return candidate;
        }
        counter++;
    }

    throw new Error(`PoW solver exhausted (${params.difficulty} difficulty)`);
}

export function buildPublicKeyLoginBody(params: {
    username: string;
    deviceId: string;
    publicKey: string;
    nonce: string;
    ts: string;
    sig: string;
    clientId?: string;
    powNonce?: string;
}): Record<string, string> {
    const body: Record<string, string> = {
        username: params.username,
        device_id: params.deviceId,
        public_key: params.publicKey,
        nonce: params.nonce,
        ts: params.ts,
        sig: params.sig,
    };
    if (params.clientId) body.client_id = params.clientId;
    if (params.powNonce) body.pow_nonce = params.powNonce;
    return body;
}

export interface DeviceSignaturePayloadData {
    deviceId: string;
    publicKey: string;
    ts: string;
    nonce: string;
}

export function canonicalDeviceSignaturePayload(data: DeviceSignaturePayloadData): string {
    return JSON.stringify({
        deviceId: data.deviceId,
        publicKey: data.publicKey,
        ts: data.ts,
        nonce: data.nonce,
    });
}

export class TokenLock {
    private pending: Promise<unknown> | null = null;

    async run<T>(worker: () => Promise<T>): Promise<T> {
        if (this.pending) {
            await this.pending;
        }
        const promise = worker();
        this.pending = promise;
        try {
            return await promise;
        } finally {
            this.pending = null;
        }
    }
}
