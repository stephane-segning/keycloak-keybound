import crypto from 'node:crypto';

export function env(name: string, fallback: string): string {
  return process.env[name] ?? fallback;
}

export function base64url(input: Buffer): string {
  return input
    .toString('base64')
    .replace(/=/g, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_');
}

export function randomBase64Url(bytes: number): string {
  return base64url(crypto.randomBytes(bytes));
}

export function sha256Base64Url(input: string): string {
  const hash = crypto.createHash('sha256').update(input, 'utf8').digest();
  return base64url(hash);
}

export function toFormUrlEncoded(params: Record<string, string>): string {
  return new URLSearchParams(params).toString();
}

export function issuerUrl(keycloakBaseUrl: string, realm: string): string {
  return `${keycloakBaseUrl.replace(/\/$/, '')}/realms/${encodeURIComponent(realm)}`;
}

export function tokenEndpoint(issuer: string): string {
  return `${issuer}/protocol/openid-connect/token`;
}

export function userInfoEndpoint(issuer: string): string {
  return `${issuer}/protocol/openid-connect/userinfo`;
}

// Keycloak's DeviceKeyGrantType expects a raw ECDSA signature: R||S (not ASN.1 DER).
// It converts raw -> DER internally before verifying.
export function derToRawEcdsaSignature(der: Buffer, partSize: number): Buffer {
  // ASN.1: 30 len 02 lenR rBytes 02 lenS sBytes
  if (der.length < 8 || der[0] !== 0x30) {
    throw new Error('Invalid DER signature (expected SEQUENCE)');
  }
  let offset = 2;
  if (der[1] & 0x80) {
    const lenBytes = der[1] & 0x7f;
    offset = 2 + lenBytes;
  }
  if (der[offset] !== 0x02) {
    throw new Error('Invalid DER signature (expected INTEGER r)');
  }
  const rLen = der[offset + 1];
  const r = der.subarray(offset + 2, offset + 2 + rLen);
  offset = offset + 2 + rLen;
  if (der[offset] !== 0x02) {
    throw new Error('Invalid DER signature (expected INTEGER s)');
  }
  const sLen = der[offset + 1];
  const s = der.subarray(offset + 2, offset + 2 + sLen);

  const rPadded = leftPad(r, partSize);
  const sPadded = leftPad(s, partSize);
  return Buffer.concat([rPadded, sPadded]);
}

function leftPad(intBytes: Buffer, size: number): Buffer {
  // Strip leading 0x00 that DER may add to keep integer positive.
  let bytes = intBytes;
  while (bytes.length > 1 && bytes[0] === 0x00 && (bytes[1] & 0x80) === 0) {
    bytes = bytes.subarray(1);
  }
  if (bytes.length > size) {
    bytes = bytes.subarray(bytes.length - size);
  }
  const out = Buffer.alloc(size);
  bytes.copy(out, size - bytes.length);
  return out;
}

export function jwkThumbprintES256(publicJwk: { crv: string; kty: string; x: string; y: string }): string {
  // RFC 7638 thumbprint: SHA-256 over lexicographically-ordered members for EC: crv,kty,x,y
  const canonical = `{"crv":"${publicJwk.crv}","kty":"${publicJwk.kty}","x":"${publicJwk.x}","y":"${publicJwk.y}"}`;
  const digest = crypto.createHash('sha256').update(canonical, 'utf8').digest();
  return base64url(digest);
}

export function publicJwkStringForKeycloak(publicJwk: { crv: string; kty: string; x: string; y: string }): string {
  // Keycloak uses Jackson to serialize a Map coming from Gson. Map iteration order is typically key-sorted,
  // so we keep this deterministic with lexicographic order.
  return `{"crv":"${publicJwk.crv}","kty":"${publicJwk.kty}","x":"${publicJwk.x}","y":"${publicJwk.y}"}`;
}

export function printBashExports(prefix: string, obj: Record<string, unknown>): void {
  const toStr = (v: unknown) => (v == null ? '' : String(v));
  for (const [k, v] of Object.entries(obj)) {
    const name = `${prefix}${k}`.toUpperCase().replace(/[^A-Z0-9_]/g, '_');
    const value = toStr(v).replace(/'/g, `'\\''`);
    process.stdout.write(`export ${name}='${value}'\n`);
  }
}

