# Usage Guide

This repository contains Keycloak extensions for "device-bound" authentication and a backend API gateway.

This guide covers:
1. A Node.js + TypeScript example that:
   - generates an EC P-256 keypair
   - runs the OIDC Authorization Code (PKCE) flow (browser redirect)
   - exchanges the code for tokens
   - calls the UserInfo endpoint
   - calls the custom `device_key` grant and prints tokens in a bash-friendly format
2. A WireMock setup with example stubs for `openapi/backend.open-api.yml`.

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

### Run the custom `device_key` grant

This calls Keycloak's token endpoint with:
- `grant_type=urn:ssegning:params:oauth:grant-type:device_key`
- `device_id`, `ts`, `nonce`, `sig`, `username`

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

The grant implementation is in:
- `keycloak-keybound-grant-device-key/src/main/kotlin/grants/DeviceKeyGrantType.kt`
- grant type id: `urn:ssegning:params:oauth:grant-type:device_key`

In dev mode with WireMock, the example can also register a per-device stub in WireMock so Keycloak can resolve:
- `POST /v1/devices/lookup`

Run:

```bash
cd examples/nodejs-ts
npm run device-grant -- --username test --bash
```

This prints `export ...` lines suitable for bash.

## WireMock stubs for the backend API

WireMock is mounted from `./.docker/wiremock` into the container.

Example mappings are stored in:
- `./.docker/wiremock/mappings`

The stubs are intentionally simple (stateless) and meant for local development.

Notes:
- `docker compose` starts WireMock with `--global-response-templating` so mappings can echo request fields.
- The Node.js `device-grant` script can register a per-device `POST /v1/devices/lookup` stub through `http://localhost:8080/__admin`.

If you need a realistic CRUD backend, implement `openapi/backend.open-api.yml` in a real service.
