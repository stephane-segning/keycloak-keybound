# Keycloak Keybound

[![CI](https://github.com/stephane-segning/keycloak-keybound/actions/workflows/releases.yml/badge.svg)](https://github.com/stephane-segning/keycloak-keybound/actions/workflows/releases.yml)
[![License](https://img.shields.io/badge/license-MIT-blue?style=for-the-badge)](LICENSE)

_Keycloak Keybound_ is a plugin suite that adds device-bound authentication to Keycloak (Quarkus). It brings together
enrollment authenticators, a device-key credential type, a protocol mapper, a custom grant, a custom endpoint, and
examples so you can roll out per-device keys with a clear, auditable security contract.

## Highlights

- **Device-bound security** – issue `device_key` credentials, enforce challenge-response flows, and attach auth logic
  directly to Keycloak authenticators.
- **Full plugin surface** – authenticators (enrollment, approval), credential provider, protocol mapper, grant handler,
  theme, API gateway extensions, and optional user storage.
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

| Module                                       | Role                                                                                                    |
|----------------------------------------------|---------------------------------------------------------------------------------------------------------|
| `keycloak-keybound-authenticator-enrollment` | Runs DK1-DK10: ingest/verify, phone capture, approval-or-OTP branching, user resolution, and bind.      |
| `keycloak-keybound-credentials-device-key`   | Stores, validates, and rotates device keys within Keycloak credential API.                              |
| `keycloak-keybound-grant-device-key`         | Implements `urn:ssegning:params:oauth:grant-type:device_key` for token issuance without refresh tokens. |
| `keycloak-keybound-protocol-mapper`          | Maps device metadata into access tokens so downstream services can make policy decisions.               |
| `keycloak-keybound-theme`                    | Skin for login flows that surfaces device status to users.                                              |
| `keycloak-keybound-api-gateway-http`         | Custom endpoint for verifying device signatures from external services.                                 |
| `keycloak-keybound-user-storage-backend`     | User Storage SPI backed by backend APIs for externalized user CRUD/search.                              |

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
4. Enable the authenticators in a custom flow, register device credentials, and wire the custom grant type into your
   clients.

## Release Artifacts

GitHub Releases ship the built module JARs from `*/build/libs/*.jar`. Pick only the SPIs you use and copy them into
Keycloak's `providers/` directory.

## Quick Start Checklist

- [x] Build the entire suite: `./gradlew build`
- [x] Run the docker compose stack: `docker compose up --build`
- [x] Authenticate using the Node.js, Vite, or Spring Kotlin examples in `examples/`
- [ ] Extend the sample workflow or adapt the authenticator to your tenant

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

## Examples & Recipes

- `examples/nodejs-ts` – device enrollment + approval client in TypeScript. See its README.
- `examples/web-vite-react` – frontend flows with React + Pinia via Keycloak adapter.
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
