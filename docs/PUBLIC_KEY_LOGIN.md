---
title: Public-Key Login Endpoint
---

# Public-Key Login Docs

## Overview

- The Keycloak SPI `device-public-key-login` accepts signed device proof, enforces nonce/ts replay protection, optionally validates a client-supplied PoW, creates a backend user, binds the device key via the backend API gateway, and returns `user_id` plus creation flags instead of minting tokens. citekeycloak-keybound-custom-endpoint/src/main/kotlin/com/ssegning/keycloak/keybound/endpoint/PublicKeyLoginResource.kt:46
- The endpoint shares the same canonical payload contract documented in `docs/SIGNING_AND_VERIFICATION.md` and implemented by `PublicKeyLoginSignaturePayload`. citekeycloak-keybound-core/src/main/kotlin/com/ssegning/keycloak/keybound/core/models/PublicKeyLoginSignaturePayload.kt:6

## API contract

### Request schema

- Required fields: `device_id`, `public_key`, `nonce`, `ts` (or `timestamp`), `sig`, `device_os`, `device_model`. citedocs/SIGNING_AND_VERIFICATION.md:111
- Optional trace fields: `client_id` (stored as attribute `request_client_id`) and `pow_nonce` when PoW is enabled. citeREADME.md:123
- Optional metadata: `device_app_version`.

### Response schema

- Returns JSON `{ "user_id": ..., "created_user": bool, "credential_created": bool }` representing final user/credential state. citedocs/SIGNING_AND_VERIFICATION.md:127

## Server behavior

- Timestamp windows are governed by `NONCE_CACHE_TTL_{realm}` (default 300s) and nonces live in `SingleUseObjectProvider`. citekeycloak-keybound-custom-endpoint/src/main/kotlin/com/ssegning/keycloak/keybound/endpoint/PublicKeyLoginResource.kt:121
- Replay/PoW check: if `PUBLIC_KEY_LOGIN_POW_DIFFICULTY_{realm}` > 0, the endpoint computes `SHA-256("${realm}:${device_id}:${ts}:${nonce}:${pow_nonce}")` and requires a configurable number of leading zero hex nibbles before moving on to signature validation. citekeycloak-keybound-custom-endpoint/src/main/kotlin/com/ssegning/keycloak/keybound/endpoint/PublicKeyLoginResource.kt:125
- After signature verification, the handler rejects if `device_id` or `jkt` is already associated with a user, resolves or creates the Keycloak account bound to that device (tracking metadata attributes), and calls `ApiGateway.enrollmentBindForRealm` to persist the device record (`source=device-public-key-login`). citekeycloak-keybound-custom-endpoint/src/main/kotlin/com/ssegning/keycloak/keybound/endpoint/PublicKeyLoginResource.kt:203

## Proof-of-work (PoW)

- Server-controlled PoW difficulty is `PUBLIC_KEY_LOGIN_POW_DIFFICULTY_{realm}` (default 0). When set, PoW is validated before signature checks and the difficulty/nonce are stored as proof metadata. citeREADME.md:128
- Frontend/Python clients can solve PoW by iterating `pow_nonce` until the hash presents the required zero nibbles; the React demo ships `examples/web-vite-react-public-key-login/src/lib/pow.ts`. citeexamples/web-vite-react-public-key-login/src/lib/pow.ts:1
- The React client exposes the difficulty via `VITE_PUBLIC_LOGIN_POW_DIFFICULTY` and includes the solved nonce in the request payload. citeexamples/web-vite-react-public-key-login/src/config.ts:1 citeexamples/web-vite-react-public-key-login/src/lib/public-key-login.ts:27

## DoS and Abuse Mitigation

This endpoint is designed to be callable from untrusted clients (browsers/mobile), so treat it like an Internet-facing "public ingest" API.

- Keep PoW enabled in production (`PUBLIC_KEY_LOGIN_POW_DIFFICULTY_{realm} > 0`) and tune it to your client latency budget. PoW makes *valid* attempts expensive for attackers while staying cheap for the server to verify.
- Put Keycloak behind a reverse proxy / WAF and rate-limit requests to `POST /realms/*/device-public-key-login` (and usually `OPTIONS` too). Also enforce strict timeouts and a small request body limit.
- Add automated blocking on abuse signals (e.g., too many 4xx/401/409 per IP per minute, too many requests missing `pow_nonce`, too many distinct `device_id` per IP).
- Isolate blast radius: consider a dedicated Keycloak node/ingress for the enrollment endpoint so abuse doesn't starve admin/interactive auth.

Example nginx rate limit:

```nginx
limit_req_zone $binary_remote_addr zone=pkl:10m rate=2r/s;

location ~ ^/realms/[^/]+/device-public-key-login$ {
  limit_req zone=pkl burst=10 nodelay;
  client_max_body_size 8k;
  client_body_timeout 5s;
  client_header_timeout 5s;
  proxy_read_timeout 10s;
  proxy_pass http://keycloak;
}
```

## React example (`web-vite-react-public-key-login`)

- Provides `/public-login`, copies the original Vite React stack, and adds a UI to call the new realm resource after solving PoW (`examples/web-vite-react-public-key-login/src/pages/public-key-login-page.tsx`). citeexamples/web-vite-react-public-key-login/src/pages/public-key-login-page.tsx:1
- The example shows the device seed, optional `client_id`, current PoW difficulty, and displays the endpoint result.
- Build instruction remains `npm run build` inside the example folder. citeexamples/web-vite-react-public-key-login/README.md:1

## Running

- Build the new example with `cd examples/web-vite-react-public-key-login && npm install && npm run build`. citeexamples/web-vite-react-public-key-login/README.md:1
- Set `PUBLIC_KEY_LOGIN_POW_DIFFICULTY_{realm}` plus `VITE_PUBLIC_LOGIN_POW_DIFFICULTY` on the client to keep the endpoint hardened.

## Testing

- Kotlin: `./gradlew :keycloak-keybound-custom-endpoint:compileKotlin :keycloak-keybound-core:test`. citekeycloak-keybound-core/src/main/kotlin/com/ssegning/keycloak/keybound/core/spi/ApiGateway.kt:63
- React example: `cd examples/web-vite-react-public-key-login && npm run build`. citeexamples/web-vite-react-public-key-login/src/lib/public-key-login.ts:1
