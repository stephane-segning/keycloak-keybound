import {p256} from '@noble/curves/nist.js';
import {utf8ToBytes} from '@noble/hashes/utils.js';

const toBase64 = (bytes: Uint8Array): string => {
    if (typeof btoa !== 'function') {
        throw new Error('Missing base64 encoder in runtime');
    }
    let binary = '';
    for (const byte of bytes) {
        binary += String.fromCharCode(byte);
    }
    return btoa(binary);
};

const fromBase64 = (value: string): Uint8Array => {
    if (typeof atob !== 'function') {
        throw new Error('Missing base64 decoder in runtime');
    }
    const binary = atob(value);
    const bytes = new Uint8Array(binary.length);
    for (let index = 0; index < binary.length; index++) {
        bytes[index] = binary.charCodeAt(index);
    }
    return bytes;
};

const toBase64Url = (bytes: Uint8Array): string =>
    toBase64(bytes).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

const fromBase64Url = (value: string): Uint8Array => {
    const normalized = value.replace(/-/g, '+').replace(/_/g, '/');
    const padding = normalized.length % 4 === 0 ? '' : '='.repeat(4 - (normalized.length % 4));
    return fromBase64(normalized + padding);
};

const parsePrivateKeyBytes = (privateJwk: JsonWebKey): Uint8Array => {
    if (!privateJwk.d || privateJwk.kty !== 'EC' || privateJwk.crv !== 'P-256') {
        throw new Error('Unsupported private JWK format: expected EC P-256 key');
    }
    const privateBytes = fromBase64Url(privateJwk.d);
    if (privateBytes.length !== 32) {
        throw new Error('Invalid EC private key length');
    }
    return privateBytes;
};

export const generateKeyPair = async () => {
    const privateBytes = p256.utils.randomSecretKey();
    const publicBytes = p256.getPublicKey(privateBytes, false);
    const publicJwk: JsonWebKey = {
        kty: 'EC',
        crv: 'P-256',
        x: toBase64Url(publicBytes.slice(1, 33)),
        y: toBase64Url(publicBytes.slice(33, 65)),
    };
    const privateJwk: JsonWebKey = {
        ...publicJwk,
        d: toBase64Url(privateBytes),
    };
    return {publicJwk, privateJwk};
};

export const signPayload = async (privateJwk: JsonWebKey, payload: string) => {
    const privateBytes = parsePrivateKeyBytes(privateJwk);
    const signature = p256.sign(utf8ToBytes(payload), privateBytes, {format: 'compact', lowS: true});
    return toBase64Url(signature);
};

export const stringifyPublicJwk = (publicJwk: JsonWebKey) => JSON.stringify(publicJwk);
