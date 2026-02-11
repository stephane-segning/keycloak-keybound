# Keycloak Keybound

[![CI](https://github.com/stephane-segning/keycloak-keybound/actions/workflows/releases.yml/badge.svg)](https://github.com/stephane-segning/keycloak-keybound/actions/workflows/releases.yml)
[![License](https://img.shields.io/badge/license-MIT-blue?style=for-the-badge)](LICENSE)

_Keycloak Keybound_ is a community-ready plugin suite that turns Keycloak into a device-bound authentication powerhouse
with minimal changes to your tenant. It bundles authenticators, credential providers, protocol mappers, a custom grant,
and ready-to-run examples so teams can adopt frictionless, high-security device keys in one repository.

## Highlights

- **Device-bound security** – issue `device_key` credentials, enforce challenge-response flows, and attach auth logic
  directly to Keycloak authenticators.
- **Full plugin surface** – authenticators (enrollment, approval), credential provider, protocol mapper, grant handler,
  theme, API gateway extensions, and optional user storage.
- **Batteries included** – published docker compose stack, runnable examples (Node.js, Vite, Kotlin), and detailed docs
  for every module.
- **Designed for production** – contract-driven grant, signature verification helpers, and workflows that respect
  Keycloak SPI lifecycles.

## Plugin Modules & Flow

| Module                                       | Role                                                                                                    |
|----------------------------------------------|---------------------------------------------------------------------------------------------------------|
| `keycloak-keybound-authenticator-enrollment` | Runs DK1-DK10: ingest/verify, phone capture, approval-or-OTP branching, user resolution, and bind.      |
| `keycloak-keybound-credentials-device-key`   | Stores, validates, and rotates device keys within Keycloak credential API.                              |
| `keycloak-keybound-grant-device-key`         | Implements `urn:ssegning:params:oauth:grant-type:device_key` for token issuance without refresh tokens. |
| `keycloak-keybound-protocol-mapper`          | Maps device metadata into access tokens so downstream services can make policy decisions.               |
| `keycloak-keybound-theme`                    | Skin for login flows that surfaces device status to users.                                              |
| `keycloak-keybound-api-gateway-http`         | Custom endpoint for verifying device signatures from external services.                                 |
| `keycloak-keybound-user-storage-backend`     | Optional SPI for syncing devices from external systems (planned).                                       |

## Installation

### Prerequisites

- Java 17+ SDK (matching Keycloak's runtime).
- Gradle wrapper (`./gradlew`) already in repo.
- Target Keycloak 21+/Quarkus mode (compatible with the SPI contracts).

### Build the plugin

```bash
./gradlew clean build -x test
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

- `docs/USAGE.md` – setup, troubleshooting, and day-one guide.
- `docs/STRUCTURE.md` – module breakdown and interdependencies.
- `docs/WORKFLOWS/` – sequence diagrams for enrollment, approval, grants, and proposed flows.
- `docs/PLAN.md` – roadmap, implementation status, and contribution checkpoints.
- `docs/SECURITY.md` – risk review and mitigation notes.
- `docs/recommendations.md` – practical deployment suggestions.

## Contributing

Pull requests, issue reports, and architectural feedback are welcome. Please:

1. Read `docs/PLAN.md` before proposing structure changes.
2. Place tests or behavior demonstrations in `examples/` when touching runtime logic.
3. Keep docs ASCII and reference existing workflow diagrams.

## License

MIT © Keycloak Keybound contributors.
