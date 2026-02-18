# Keycloak Keybound

[![CI](https://github.com/stephane-segning/keycloak-keybound/actions/workflows/releases.yml/badge.svg)](https://github.com/stephane-segning/keycloak-keybound/actions/workflows/releases.yml)
[![License](https://img.shields.io/badge/license-MIT-blue?style=for-the-badge)](LICENSE)

_Keycloak Keybound_ is a plugin suite that adds device-bound authentication to Keycloak (Quarkus). It brings together
a custom realm endpoint, a contract-driven grant, API gateway helpers, and optional backend storage so you can roll out
per-device keys with a clear, auditable security contract.

## Highlights

- **Device-bound security** – rely on the `device-public-key-login` endpoint plus the custom grant to verify signed
  device payloads instead of shipping extra authenticators.
- **Simplified stack** – focused on the custom grant, public-key endpoint, API gateway helpers, and optional backend
  storage rather than sprawling SPIs.
- **Batteries included** – published docker compose stack, runnable examples (Node.js, Vite, Kotlin), and detailed docs
  for every module.
- **Designed for production** – contract-driven grant, signature verification helpers, and workflows that respect
  Keycloak SPI lifecycles.

## Why Device-Bound Auth

Device-bound auth helps when you want more than "the user knows the password":

- Reduce risk from phishing or stolen credentials by requiring a per-device private key.
- Limit replay by signing requests with `nonce` and `ts`.
- Give relying services a stable device identifier and proof-of-possession signal via token claims.
- Keep policy and enforcement in Keycloak (flows, sessions, SPI contracts), not scattered across apps.

## Security Model (At A Glance)

Keycloak Keybound is designed around a simple contract:

- Each enrolled device holds a private key.
- Requests are signed over canonical payloads (see `docs/SIGNING_AND_VERIFICATION.md`).
- Keycloak verifies signatures and issues tokens that carry device context for downstream policy.

Threats it helps with (depending on your tenant policy and flow configuration):

- Stolen credentials, token exfiltration, and "login on a different device" abuse.
- Blind replays of signed requests (nonce and timestamp windowing).
- Drift between clients and verifiers by standardizing payload ADTs.

## Plugin Modules & Flow

| Module                                       | Role                                                                                                               |
|----------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| `keycloak-keybound-core`                     | Shared SPI helpers for device descriptors, API wiring, and signature verification common to all providers.          |
| `keycloak-keybound-custom-endpoint`          | Exposes the `device-public-key-login` realm endpoint that verifies signatures and coordinates enrollment metadata. |
| `keycloak-keybound-api-gateway-http`         | Talks to the backend enrollment + device lookup API (enrollment bind + lookup operations only).                    |
| `keycloak-keybound-grant-device-key`         | Implements `urn:ssegning:params:oauth:grant-type:device_key` for device-bound token issuance without refresh tokens. |

## Installation

### Prerequisites

- Java 21+ SDK.
- Gradle wrapper (`./gradlew`) already in repo.
- Target Keycloak Quarkus distribution (the examples and dependencies target Keycloak 26.x).

### Build the plugin

```bash
./gradlew clean build
```

Each module produces a provider JAR under `build/libs/`. Copy the ones you need into Keycloak's `providers/` directory
or keep them in a shared layer for custom container images.

### Deploy locally

1. Start Keycloak + dependencies:
   ```bash
   docker compose up --build
   ```
2. Open the admin console at `http://localhost:9026`, credentials stored in `compose.yaml`.
3. Upload the provider JARs (via the admin console or drop them into `providers/` before boot).
4. Point clients at the `device-public-key-login` endpoint, ensure the grant type is enabled, and bind the resulting user_id to its device metadata.

## Release Artifacts

GitHub Releases ship the built module JARs from `*/build/libs/*.jar`. Pick only the SPIs you use and copy them into
Keycloak's `providers/` directory.

## Quick Start Checklist

- [x] Build the entire suite: `./gradlew build`
- [x] Run the docker compose stack: `docker compose up --build`
- [x] Authenticate using the Node.js helper or the `web-vite-react-public-key-login` example.
- [x] Ensure clients send `device_os` and `device_model` (plus optional `device_app_version`) on each login.
- [ ] Extend the sample workflow to fit your tenant

## Device Grant Contract (core requirement)

| Field        | Description                                       |
|--------------|---------------------------------------------------|
| `grant_type` | `urn:ssegning:params:oauth:grant-type:device_key` |
| `user_id`    | Keycloak subject (`sub`) from the auth code flow  |
| `device_id`  | Identifier for the device bound to the user       |
| `ts`         | Unix timestamp when signature was produced        |
| `nonce`      | Replay-resistant random string                    |
| `sig`        | Signature over the payload with the device key    |
| Tokens       | Access token only (no refresh token issued)       |

## Public-Key Login Endpoint

Custom realm endpoint:

- `POST /realms/{realm}/device-public-key-login`

Required body fields:

- `device_id`
- `public_key`
- `nonce`
- `ts` (or `timestamp`)
- `sig`
- `device_os`
- `device_model`

Optional body fields:

- `device_app_version`
- `client_id` (accepted for traceability only; not used for enrollment binding)
- `pow_nonce` (required only when server-side PoW is enabled)

Keycloak resolves or creates the device-specific subject (tracking metadata attributes such as `device_os`, `device_model`, and `device_app_version`) before it calls the backend enrollment bind API.

PoW server setting:

- `PUBLIC_KEY_LOGIN_POW_DIFFICULTY_{realm}`: number of leading zero hex nibbles required in
  `SHA-256("${realm}:${device_id}:${username}:${ts}:${nonce}:${pow_nonce}")`

Example:

```bash
curl -X POST "http://localhost:9026/realms/e2e-testing/device-public-key-login" \
  -H "Content-Type: application/json" \
  -d '{
    "device_id": "dvc_123",
    "public_key": "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"...\",\"y\":\"...\"}",
    "nonce": "nce_456",
    "ts": "1739782800",
    "sig": "<base64url-compact-es256-signature>",
    "device_os": "ios",
    "device_model": "airpods",
    "device_app_version": "1.0.0"
  }'
```

## Examples & Recipes

- `examples/nodejs-ts` – device enrollment client in TypeScript. See its README.
- `examples/web-vite-react-public-key-login` – minimal React demo that authenticates with `device-public-key-login`.
- `examples/backend-spring-kotlin` – server-side validation of the device grant.
- `examples/resource-server-spring-kotlin` – resource protection sample using issued claims.
- `EXAMPLES.md` – guide tying the repo modules to the runnable projects.

## Documentation & Learning

- `docs/FULL_REVIEW.md` – merged implementation, code, security, and future-proofing review.
- `docs/recommendations.md` – practical deployment suggestions.
- `docs/SIGNING_AND_VERIFICATION.md` – canonical ADTs and pseudocode for signing and verification flows.

## Contributing

Pull requests, issue reports, and architectural feedback are welcome. Please:

1. Read `docs/FULL_REVIEW.md` before proposing structure changes.
2. Place tests or behavior demonstrations in `examples/` when touching runtime logic.
3. Keep docs ASCII and reference existing workflow diagrams.

## License

MIT © Keycloak Keybound contributors.
