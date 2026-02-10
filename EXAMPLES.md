# Examples Architecture

This document defines the architecture for three runnable examples:

1. `examples/web-vite-react` (frontend client)
2. `examples/backend-spring-kotlin` (backend implementing `openapi/backend.open-api.yml`)
3. `examples/resource-server-spring-kotlin` (httpbin-like resource server)

## Goals

- Provide end-to-end runnable samples around the current Keycloak extensions.
- Keep examples independent from the provider modules.
- Stay contract-first with `openapi/backend.open-api.yml`.

## 1) Web Client (`examples/web-vite-react`)

Stack:
- Vite
- React + TypeScript
- React Router
- `idb-keyval` for local persistence

Responsibilities:
- Generate EC P-256 keypair using WebCrypto.
- Persist `device_id`, `public_jwk`, `private_jwk`, then `user_id` (later) in IndexedDB.
- Start browser authorization request with:
  - PKCE (`code_challenge`, `code_verifier`)
  - `device_id`
  - `public_key`
  - `ts`, `nonce`, `sig`
  - `user_hint`, `aud`, optional device metadata
- Handle auth callback and code exchange.
- Call UserInfo.
- Call custom grant `urn:ssegning:params:oauth:grant-type:device_key`.
- Call resource server with resulting token.

Routing:
- `/` overview/status
- `/login` start auth
- `/callback` handle code
- `/session` token + userinfo details
- `/resource` resource server call

Storage model (IndexedDB keys):
- `device_id`
- `public_jwk`
- `private_jwk`
- `user_id`

## 2) Backend API (`examples/backend-spring-kotlin`)

Stack:
- Spring Boot 3
- Kotlin
- Gradle Kotlin DSL
- No security filter/authentication for this example

Responsibilities:
- Implement the backend API contract from `openapi/backend.open-api.yml`.
- Provide deterministic local behavior for all endpoints:
  - enrollments (precheck/bind)
  - approvals (create/get/cancel)
  - users (create/get/update/delete/search)
  - devices (list/disable/lookup)
  - sms (send/confirm)

Implementation approach:
- Generate interfaces/models from OpenAPI (or mirror same schema classes manually if needed).
- Implement services behind controllers.
- Use in-memory stores (`ConcurrentHashMap`) for:
  - users + indexes
  - device bindings
  - approval requests
  - sms challenge state
  - idempotency keys

Notes:
- `enrollmentBind` idempotent for same binding.
- conflict for binding to another user.
- `lookupDevice` authoritative for grant checks.

## 3) Resource Server (`examples/resource-server-spring-kotlin`)

Stack:
- Spring Boot 3
- Kotlin
- Gradle Kotlin DSL
- No security enforcement for this example

Purpose:
- Act like an httpbin-style echo server for token/debug validation.

Endpoints (initial target):
- `GET /get`
- `POST /post`
- `ANY /anything/**`
- `GET /headers`
- `GET /status/{code}`
- `GET /delay/{seconds}`
- `GET /jwt` (decode and echo bearer token payload for local debugging)

## Integration Plan

Docker Compose update (later phase):
- Add backend service: `api-backend-example`
- Add resource service: `resource-httpbin-example`
- Point Keycloak backend base URL to the backend example service.
- Keep WireMock as fallback profile for lightweight stubs.

## Delivery Phases

Phase 1 (current):
- Create architecture doc.
- Scaffold folder structure and starter files.

Phase 2:
- Implement backend endpoints and in-memory services.

Phase 3:
- Implement web flow and IndexedDB persistence.

Phase 4:
- Implement resource server endpoints and compose wiring.
