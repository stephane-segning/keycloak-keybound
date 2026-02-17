# Recommendations

This document captures implementation recommendations for device-bound enrollment and grant flows.

## 1) Frontend crypto and IDs

- Use pure-JS EC cryptography in the frontend through `@noble/curves` (P-256 ECDSA) instead of direct WebCrypto calls.
- Keep signature format as raw `R || S` (64 bytes) + Base64URL, matching server verification expectations.
- Generate frontend IDs with CUID2 (`@paralleldrive/cuid2`) instead of UUID:
  - `dvc_*` for `device_id`
  - `nce_*` for nonce
  - `stt_*` for OAuth `state`

Current implementation:
- `examples/web-vite-react/src/lib/crypto.ts`
- `examples/web-vite-react/src/lib/id.ts`
- `examples/web-vite-react/src/hooks/use-device-storage.ts`
- `examples/web-vite-react/src/pages/login-page.tsx`
- `examples/web-vite-react/src/lib/auth.ts`

## 2) Make `device_os` required

- Enforce `device_os` in the enrollment ingest step and fail fast if missing.
- Keep passing `device_os` to the backend via enrollment bind attributes.
- Use `device_os` (with `device_model`) to build user-visible device labels.

Current implementation:
- `keycloak-keybound-authenticator-enrollment/src/main/kotlin/com/ssegning/keycloak/keybound/authenticator/enrollment/IngestSignedDeviceBlobAuthenticator.kt`
- `keycloak-keybound-authenticator-enrollment/src/main/kotlin/com/ssegning/keycloak/keybound/authenticator/enrollment/PersistDeviceCredentialAuthenticator.kt`
- `examples/backend-spring-kotlin/src/main/kotlin/com/ssegning/keycloak/keybound/examples/backend/store/BackendDataStore.kt`

## 3) Uniqueness and prefixed backend IDs

- Treat persisted device key material as unique using `jkt` and `device_id` checks at bind time.
- Do not rely only on precheck; enforce uniqueness again in bind for race-safety and direct API safety.
- Generate backend IDs with explicit prefixes:
  - `usr_*` for users
  - `dvc_*` for device record IDs
  - `apr_*` for approvals
  - `sms_*` for SMS challenge hashes

Current implementation:
- `examples/backend-spring-kotlin/src/main/kotlin/com/ssegning/keycloak/keybound/examples/backend/store/BackendDataStore.kt`
- `examples/backend-spring-kotlin/src/main/resources/templates/store-dashboard.ftl`

## 4) Recommended production hardening

- Add true DB unique constraints:
  - unique index on `device_id`
  - unique index on `jkt`
- Add server-generated `device_record_id` as primary key and keep client `device_id` as external identifier.
- Track `last_seen_at` on every successful lookup/grant usage.
- Allow user-managed labels while storing raw platform/model metadata separately.

## 5) Stable JWK Serialization for Signatures

- When embedding a JWK as a string within a signature payload (e.g., `DeviceSignaturePayload`), ensure the field order is deterministic.
- Always sort JWK keys alphabetically (`crv`, `kty`, `x`, `y`) before serializing to JSON.
- Use ordered map structures (e.g., `BTreeMap` in Rust, `TreeMap` in Java/Kotlin) or explicit key sorting in JavaScript to prevent signature mismatches caused by non-deterministic field ordering.
