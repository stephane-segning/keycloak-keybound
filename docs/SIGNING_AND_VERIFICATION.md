# Signing and Verification Contracts

This document defines the canonical payload contracts used for cryptographic signing and verification.
It is the source of truth for cross-module consistency between frontend clients, Keycloak providers, and backend examples.

## 1) Device Proof Signature (Enrollment + Device Grant)

### Abstract Data Type

`DeviceSignaturePayload`

```text
type DeviceSignaturePayload = {
  deviceId: String
  publicKey: String   // JSON string form of the EC public JWK
  ts: String          // unix epoch seconds serialized as string
  nonce: String       // single-use random string
}
```

### Canonical Serialization

Canonical JSON object order:

1. `deviceId`
2. `publicKey`
3. `ts`
4. `nonce`

Producer and verifier both serialize this ADT before signature operations:

- Frontend: `examples/web-vite-react/src/lib/canonical-payloads.ts`
- Keycloak: `keycloak-keybound-core/src/main/kotlin/com/ssegning/keycloak/keybound/core/models/DeviceSignaturePayload.kt`

### Signing Pseudocode (Frontend)

```text
input:
  privateJwk: EcP256PrivateJwk
  payload: DeviceSignaturePayload

canonical = payload.toCanonicalJson()
messageBytes = UTF8(canonical)
sigCompact = ECDSA_P256_SIGN_COMPACT(privateJwk, messageBytes)   // r||s, 64 bytes
sig = BASE64URL_NO_PADDING(sigCompact)

output:
  sig
```

### Verification Pseudocode (Keycloak)

```text
input:
  deviceId, publicKeyJwkString, ts, nonce, sig

assert abs(nowEpochSeconds - parseLong(ts)) <= ttl
assert singleUseStore.putIfAbsent("replay:<realm>:<nonce>", ttl) == true

payload = DeviceSignaturePayload(
  deviceId=deviceId,
  publicKey=publicKeyJwkString,
  ts=ts,
  nonce=nonce
)
canonical = payload.toCanonicalJson()
messageBytes = UTF8(canonical)

publicKey = PARSE_JWK_TO_PUBLIC_KEY(publicKeyJwkString)
signatureBytes = BASE64_OR_BASE64URL_DECODE(sig)
assert ECDSA_P256_VERIFY(publicKey, messageBytes, signatureBytes)
```

### Implementations

- Enrollment verification: implement the verification logic in your chosen Keycloak flow.
- Device grant verification: `keycloak-keybound-grant-device-key/src/main/kotlin/grants/DeviceKeyGrantType.kt`

## 1b) Public-Key Login Signature (Custom Endpoint)

### Abstract Data Type

`PublicKeyLoginSignaturePayload`

```text
type PublicKeyLoginSignaturePayload = {
  nonce: String
  deviceId: String
  ts: String          // unix epoch seconds serialized as string
  publicKey: String   // JSON string form of the EC public JWK
}
```

### Canonical Serialization

Canonical JSON object order:

1. `nonce`
2. `deviceId`
3. `ts`
4. `publicKey`

Shared model:

- Keycloak: `keycloak-keybound-core/src/main/kotlin/com/ssegning/keycloak/keybound/core/models/PublicKeyLoginSignaturePayload.kt`

### Endpoint Contract

`POST /realms/{realm}/device-public-key-login`

Required JSON fields:

- `device_id`
- `public_key`
- `nonce`
- `ts` (or alias `timestamp`)
- `sig`

Optional JSON fields:

- `client_id` (accepted for traceability only; not used for enrollment binding)
- `pow_nonce` (required when PoW is enabled via `PUBLIC_KEY_LOGIN_POW_DIFFICULTY_{realm}`)

Successful response:

```text
{
  "user_id": "<keycloak-user-id>",
  "created_user": <boolean>,
  "credential_created": <boolean>
}
```

### Verification Pseudocode (Keycloak Custom Endpoint)

```text
input:
  device_id, public_key, nonce, ts, sig

assert abs(nowEpochSeconds - parseLong(ts)) <= ttl
assert singleUseStore.putIfAbsent("public-key-login-replay:<realm>:<nonce>", ttl) == true
if powDifficulty > 0:
  assert SHA256("<realm>:<device_id>:<ts>:<nonce>:<pow_nonce>") has required leading zero nibbles

payload = PublicKeyLoginSignaturePayload(
  nonce=nonce,
  deviceId=device_id,
  ts=ts,
  publicKey=public_key
)
canonical = payload.toCanonicalJson()
messageBytes = UTF8(canonical)

publicKey = PARSE_JWK_TO_PUBLIC_KEY(public_key)
signatureBytes = BASE64_OR_BASE64URL_DECODE(sig)
assert length(signatureBytes) == 64
assert ECDSA_P256_VERIFY(publicKey, messageBytes, signatureBytes)
```

### Implementation

- Verifier + user/device materialization: `keycloak-keybound-custom-endpoint/src/main/kotlin/com/ssegning/keycloak/keybound/endpoint/PublicKeyLoginResource.kt`

## 2) HTTP Request Signature (Signed Resource Server Example)

### Abstract Data Type

`RequestSignaturePayload`

```text
type RequestSignaturePayload = {
  method: String
  path: String
  query: String
  timestamp: String   // unix epoch seconds serialized as string
}
```

### Canonical Serialization

Canonical string format:

```text
UPPER(method) + "\n" + path + "\n" + query + "\n" + timestamp
```

Producer and verifier both build this ADT:

- Frontend: `examples/web-vite-react/src/lib/canonical-payloads.ts`
- Backend example: `examples/resource-server-spring-kotlin-signed/src/main/kotlin/com/ssegning/keycloak/keybound/examples/resource/signed/RequestSignaturePayload.kt`

### Signing Pseudocode (Frontend)

```text
input:
  privateJwk: EcP256PrivateJwk
  payload: RequestSignaturePayload

canonical = payload.toCanonicalString()
messageBytes = UTF8(canonical)
sigCompact = ECDSA_P256_SIGN_COMPACT(privateJwk, messageBytes)   // r||s, 64 bytes
sig = BASE64URL_NO_PADDING(sigCompact)

headers:
  x-public-key = JSON(publicJwk)
  x-signature-timestamp = payload.timestamp
  x-signature = sig
```

### Verification Pseudocode (Backend Example)

```text
input:
  jwt.cnf.jkt, x-public-key, x-signature-timestamp, x-signature

assert abs(nowEpochSeconds - parseLong(x-signature-timestamp)) <= maxClockSkew

publicJwk = PARSE_JSON(x-public-key)
thumbprint = JWK_SHA256_THUMBPRINT(publicJwk)
assert thumbprint == jwt.cnf.jkt

payload = RequestSignaturePayload(
  method=request.method,
  path=request.path,
  query=request.queryString or "",
  timestamp=x-signature-timestamp
)
canonical = payload.toCanonicalString()
messageBytes = UTF8(canonical)

sigCompact = BASE64URL_DECODE(x-signature)
assert length(sigCompact) == 64
sigDer = ECDSA_COMPACT_TO_DER(sigCompact)
assert ECDSA_P256_VERIFY_DER(publicJwk, messageBytes, sigDer)
```

### Implementation

- Backend filter: `examples/resource-server-spring-kotlin-signed/src/main/kotlin/com/ssegning/keycloak/keybound/examples/resource/signed/SignatureVerificationFilter.kt`

## Contract Change Rules

When changing any signature payload:

1. Update the ADT in this document first.
2. Update all producers and verifiers in the same commit.
3. Keep canonical serialization deterministic.
4. Add or update tests for every changed verifier.
