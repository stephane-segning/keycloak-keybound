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
- `examples/web-vite-react-public-key-login/src/lib/crypto.ts`
- `examples/web-vite-react-public-key-login/src/lib/id.ts`
- `examples/web-vite-react-public-key-login/src/lib/device-db.ts`
- `examples/web-vite-react-public-key-login/src/pages/public-key-login-page.tsx`
- `examples/web-vite-react-public-key-login/src/lib/auth.ts`

## 2) Make `device_os` required

- Enforce `device_os` in the enrollment ingest step and fail fast if missing.
- Keep passing `device_os` to the backend via enrollment bind attributes.
- Use `device_os` (with `device_model`) to build user-visible device labels.

Current implementation:
- `examples/backend-spring-kotlin/src/main/kotlin/com/ssegning/keycloak/keybound/examples/backend/store/BackendDataStore.kt`
- Enforce `device_os` in your enrollment flow during ingest/bind to keep the metadata available for backend labels.

## 3) Uniqueness and prefixed backend IDs

- Treat persisted device key material as unique using `jkt` and `device_id` checks at bind time.
- Do not rely only on precheck; enforce uniqueness again in bind for race-safety and direct API safety.
- Generate backend IDs with explicit prefixes:
  - `usr_*` for users
  - `dvc_*` for device record IDs

Current implementation:
- `examples/backend-spring-kotlin/src/main/kotlin/com/ssegning/keycloak/keybound/examples/backend/store/BackendDataStore.kt`

## 4) Recommended production hardening

- Add true DB unique constraints:
  - unique index on `device_id`
  - unique index on `jkt`
- Add server-generated `device_record_id` as primary key and keep client `device_id` as external identifier.
- Track `last_seen_at` on every successful lookup/grant usage.
- Allow user-managed labels while storing raw platform/model metadata separately.

## 5) Proof-of-work tuning for public-key login

- Adjust `PUBLIC_LOGIN_POW_DIFFICULTY` per realm via environment overrides (e.g., `PUBLIC_LOGIN_POW_DIFFICULTY_{REALM}`) and align it with your latency budget. The value counts *leading zero hex nibbles* in the SHA-256 hash of the material described in `docs/SIGNING_AND_VERIFICATION.md`, so each increment multiplies the attack cost by four bits.
- Typical production values sit between **4** (16 zero bits, `1 / 2^16` probability, ~65k hash attempts) and **8** (32 zero bits, `1 / 2^32`, ~4 billion attempts). Start at 4–5 for mobile devices and raise it toward 7–8 for highly sensitive realms or when the client population can tolerate the extra work.
- Measure the actual PoW latency on representative devices before enforcing higher difficulties, and provide clear feedback in the UI when the client is solving a challenge so users understand the wait time.
- Keep the TTL for `nonce` usage (default `NONCE_CACHE_TTL_{REALM}` → 300s) longer than the expected PoW solve time to avoid rejected submissions during retries.

## 6) Stable JWK Serialization for Signatures

- When embedding a JWK as a string within a signature payload (e.g., `DeviceSignaturePayload`), ensure the field order is deterministic.
- Always sort JWK keys alphabetically (`crv`, `kty`, `x`, `y`) before serializing to JSON.
- Use ordered map structures (e.g., `BTreeMap` in Rust, `TreeMap` in Java/Kotlin) or explicit key sorting in JavaScript to prevent signature mismatches caused by non-deterministic field ordering.

## 7) Rate Limiting and Edge Hardening

Assume `POST /realms/{realm}/device-public-key-login` is Internet-facing and implement defense-in-depth:

- Keep PoW enabled and tune difficulty per realm.
- Rate-limit the endpoint at the edge (reverse proxy / WAF) and cap request size/timeouts.
- Add automated blocking for obvious abuse patterns (high error rates, missing PoW, high device_id churn per IP).
- Consider isolating this endpoint behind a dedicated ingress / Keycloak node so abuse doesn't impact admin and interactive login traffic.
