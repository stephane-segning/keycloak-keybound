// Converts binary signatures into URL-safe base64 so they can be sent as query/form params.
const base64Url = (buffer: ArrayBuffer) => {
    const bytes = new Uint8Array(buffer);
    let binary = '';
    for (let i = 0; i < bytes.byteLength; i++) {
        binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
};

export async function generateKeyPair() {
    // Device-bound key pair used for signed login params and custom grant proof.
    const keyPair = await window.crypto.subtle.generateKey(
        {name: 'ECDSA', namedCurve: 'P-256'},
        true,
        ['sign', 'verify']
    );

    const publicJwk = (await window.crypto.subtle.exportKey('jwk', keyPair.publicKey)) as JsonWebKey;
    const privateJwk = (await window.crypto.subtle.exportKey('jwk', keyPair.privateKey)) as JsonWebKey;
    return {publicJwk, privateJwk};
}

export async function signPayload(privateJwk: JsonWebKey, payload: string) {
    // Re-import JWK each time to keep persisted key material interoperable.
    const key = await crypto.subtle.importKey(
        'jwk',
        privateJwk,
        {name: 'ECDSA', namedCurve: 'P-256'},
        false,
        ['sign']
    );
    const encoded = new TextEncoder().encode(payload);
    const signature = await crypto.subtle.sign({name: 'ECDSA', hash: 'SHA-256'}, key, encoded);
    return base64Url(signature);
}

export function stringifyPublicJwk(publicJwk: JsonWebKey) {
    // Keycloak flow currently expects public_key as a JSON string.
    return JSON.stringify(publicJwk);
}
