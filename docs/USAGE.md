# Usage Guide

This repository contains Keycloak extensions for "device-bound" authentication and a backend API gateway.

Documentation map:
- `docs/README.md`
- `docs/STRUCTURE.md`
- `docs/WORKFLOWS/README.md`
- `docs/PLAN.md`

This guide covers:
1. A Node.js + TypeScript example that:
   - generates an EC P-256 keypair
   - starts the custom public-key browser flow with `device_id + public_key + ts + nonce + sig + pkce`
   - exchanges the code for tokens
   - calls the UserInfo endpoint
   - prints tokens in a bash-friendly format
2. A WireMock setup with example stubs for `openapi/backend.open-api.yml`.
3. Where to find the flow-level documentation (sequence diagrams).

For deeper “what calls what” documentation (authenticators, grant, protocol mapper, user-storage), see:
- `docs/WORKFLOWS/README.md`
- `docs/WORKFLOWS/current-enrollment-authenticators.md`
- `docs/WORKFLOWS/current-device-key-grant.md`
- `docs/WORKFLOWS/current-protocol-mapper.md`

## Prerequisites

- Docker (to run Keycloak + WireMock)
- Java 21 (to build the providers)
- Node.js 18+ (for the TypeScript example; Node 20+ recommended)

## Start the dev stack

1. Build provider jars:

```bash
./gradlew build
```

2. Start containers:

```bash
docker compose up
```

Keycloak will be available at `http://localhost:9026`.

WireMock will be available at `http://localhost:8080`.

## E2E realm

- The new [realm.e2e.json](../.docker/keycloak-config/realm.e2e.json) file defines clients for the Vite React UI (`web-vite`) and the httpbin-style resource server, and binds the realm to the Spring backend via `BACKEND_HTTP_BASE_PATH_e2e-testing`.
- Build the backend example first: `./gradlew :examples:backend-spring-kotlin:bootJar`.
- Compose now runs both WireMock (default user-mock mode) and `api-backend-example` (the Spring Boot mock backend) alongside Keycloak; use `docker compose up --build`.

## Keycloak bootstrap credentials

From `compose.yaml`:
- admin username: `admin`
- admin password: `password`

## Configure a test OIDC client

You need an OIDC client with:
- Standard flow enabled (Authorization Code)
- PKCE S256 allowed (default)
- Redirect URI: `http://localhost:3005/callback`

Recommended client settings:
- Client ID: `node-cli`
- Client type: public (no client secret)

For the local `docker compose` stack, `node-cli` is imported by default via `./.docker/keycloak-config/realm.theme.vymalo-wh-01.json`.

The same realm import also binds `node-cli` to a custom browser flow:
- `ingest-signed-device-blob`
- `verify-signed-blob`
- `find-or-create-user`
- `persist-device-credential`

## Node.js + TypeScript example

The example lives in `examples/nodejs-ts`.

### Install dependencies

```bash
cd examples/nodejs-ts
npm install
```

### Run the OIDC standard flow + UserInfo

This starts a local callback server on `http://localhost:3005/callback` and prints an authorization URL.

```bash
cd examples/nodejs-ts
npm run auth-code
```

Expected output:
- Authorization URL to open in a browser
- Token response JSON
- UserInfo JSON (includes `sub`)

### Run the direct public-key browser flow, then custom grant

This starts the browser auth request directly with custom parameters:
- `user_hint`
- `device_id`, `public_key`, `ts`, `nonce`, `sig`
- `pkce` (`code_challenge` / `code_verifier`)
- required `device_os`
- optional `action`, `aud`, `device_model`

`sig` is:
- ECDSA P-256 signature over a canonical JSON string
- encoded as raw `R||S` (64 bytes) and then Base64URL encoded

The canonical JSON payload is:

```json
{
  "deviceId": "...",
  "publicKey": "{\"crv\":\"P-256\",\"kty\":\"EC\",\"x\":\"...\",\"y\":\"...\"}",
  "ts": "...",
  "nonce": "..."
}
```

Important:
- `publicKey` is a JSON *string*. If the backend returns extra JWK fields (e.g. `kid`, `use`, `alg`) the signature payload changes.
- For predictable signatures, keep the backend `public_jwk` minimal (`kty`, `crv`, `x`, `y`).

The authenticators used by the custom flow are in:
- `keycloak-keybound-authenticator-enrollment/src/main/kotlin/com/ssegning/keycloak/keybound/authenticator/enrollment/IngestSignedDeviceBlobAuthenticator.kt`
- `keycloak-keybound-authenticator-enrollment/src/main/kotlin/com/ssegning/keycloak/keybound/authenticator/enrollment/VerifySignedBlobAuthenticator.kt`
- `keycloak-keybound-authenticator-enrollment/src/main/kotlin/com/ssegning/keycloak/keybound/authenticator/enrollment/FindOrCreateUserAuthenticator.kt`
- `keycloak-keybound-authenticator-enrollment/src/main/kotlin/com/ssegning/keycloak/keybound/authenticator/enrollment/PersistDeviceCredentialAuthenticator.kt`

Run:

```bash
cd examples/nodejs-ts
npm run device-grant -- --username test --bash
```

What this script does now:
- completes browser login (`authorization_code` + PKCE)
- calls UserInfo
- performs a second token request using the custom grant type `urn:ssegning:params:oauth:grant-type:device_key`
- prints both token responses and bash exports (the custom grant does not issue a refresh token)

Optional:
- `--grant-user-id <value>` to force the `user_id` used in the custom grant request.

## WireMock stubs for the backend API

WireMock is mounted from `./.docker/wiremock` into the container.

Example mappings are stored in:
- `./.docker/wiremock/mappings`

The stubs are intentionally simple (stateless) and meant for local development.

Notes:
- `docker compose` starts WireMock with `--global-response-templating` so mappings can echo request fields.
- the default `devices-lookup` mock returns `found=true` with an `ACTIVE` device and `public_jwk=null`;
  in local mock mode, the custom grant uses the `public_key` form parameter for signature verification fallback.

If you need a realistic CRUD backend, implement `openapi/backend.open-api.yml` in a real service.

## Troubleshooting

### `jwt.io` cannot verify the signature

`jwt.io` often cannot auto-fetch keys from a local `iss` (localhost). For Keycloak, the JWKS endpoint is:

- `http://localhost:9026/realms/ssegning-keybound-wh-01/protocol/openid-connect/certs`

To verify a token:
- decode the JWT header and note the `kid`
- fetch the JWKS and pick the matching key
- paste the public key/JWK into your verifier

### Standard auth-code flow fails with “missing device notes”

The minimal realm import sets:
- `"browserFlow": "keybound-publickey-browser-flow"`

That means *all* browser logins require the custom device signature parameters. If you want both:
- keep a normal browser flow as the realm default
- bind `keybound-publickey-browser-flow` only to specific clients (requires a richer realm import than the minimal one)
