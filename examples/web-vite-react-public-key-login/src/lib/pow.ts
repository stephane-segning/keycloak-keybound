const HEX = '0123456789abcdef';

function toHex(bytes: Uint8Array): string {
    let result = '';
    for (const byte of bytes) {
        result += HEX[(byte >>> 4) & 0x0f];
        result += HEX[byte & 0x0f];
    }
    return result;
}

async function sha256Hex(input: string): Promise<string> {
    if (!globalThis.crypto?.subtle) {
        throw new Error('Missing WebCrypto subtle API for PoW');
    }
    const bytes = new TextEncoder().encode(input);
    const digest = await globalThis.crypto.subtle.digest('SHA-256', bytes);
    return toHex(new Uint8Array(digest));
}

function hasLeadingZeroNibbles(hex: string, difficulty: number): boolean {
    if (difficulty <= 0) return true;
    for (let index = 0; index < difficulty; index++) {
        if (hex[index] !== '0') {
            return false;
        }
    }
    return true;
}

export async function solvePow(params: {
    realm: string;
    deviceId: string;
    username: string;
    ts: string;
    nonce: string;
    difficulty: number;
}): Promise<string | undefined> {
    if (params.difficulty <= 0) {
        return undefined;
    }

    let counter = 0;
    while (counter < 3_000_000) {
        const powNonce = `pow_${counter.toString(16)}`;
        const material =
            `${params.realm}:${params.deviceId}:${params.username}:${params.ts}:${params.nonce}:${powNonce}`;
        const hash = await sha256Hex(material);
        if (hasLeadingZeroNibbles(hash, params.difficulty)) {
            return powNonce;
        }
        counter++;
    }
    throw new Error(`Unable to solve PoW with difficulty ${params.difficulty} within iteration budget`);
}
