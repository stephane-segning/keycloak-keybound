// RFC 7636 verifier charset.
const randomString = (length: number) => {
    const allowed = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~';
    let result = '';
    const array = new Uint8Array(length);
    crypto.getRandomValues(array);
    array.forEach((num) => (result += allowed[num % allowed.length]));
    return result;
};

// Base64url encoder used by PKCE challenge generation.
const base64UrlEncode = (buffer: ArrayBuffer) => {
    const bytes = new Uint8Array(buffer);
    let binary = '';
    for (let i = 0; i < bytes.byteLength; i++) {
        binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
};

export function createCodeVerifier(): string {
    // 64 chars gives enough entropy while staying URL-safe.
    return randomString(64);
}

export async function createCodeChallenge(verifier: string): Promise<string> {
    // S256 PKCE transform: BASE64URL(SHA256(verifier)).
    const data = new TextEncoder().encode(verifier);
    const digest = await crypto.subtle.digest('SHA-256', data);
    return base64UrlEncode(digest);
}
