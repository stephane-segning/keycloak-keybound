# Keycloak Keybound

Keycloak extensions and runnable examples for device-bound authentication.

## Quick Start

1. Build providers:

```bash
./gradlew build
```

2. Start local stack:

```bash
docker compose up --build
```

3. Open Keycloak:
- `http://localhost:9026`
- admin credentials are defined in `compose.yaml`

## Documentation

- Full docs index: `docs/README.md`
- Usage and local run guide: `docs/USAGE.md`
- Unified plan and examples status: `docs/PLAN.md`
- Runtime workflow diagrams: `docs/WORKFLOWS/README.md`
- Module structure: `docs/STRUCTURE.md`
- Security notes: `docs/SECURITY.md`

## Custom Device Grant (Current Contract)

- Grant type: `urn:ssegning:params:oauth:grant-type:device_key`
- Required parameters: `user_id`, `device_id`, `ts`, `nonce`, `sig`
- `user_id` is the OIDC subject (`sub`) from the auth-code flow
- Grant is access-token focused (no refresh token)
