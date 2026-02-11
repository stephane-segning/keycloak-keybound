# Resource Server Spring Kotlin Example

This service is a local httpbin-like resource server for token/debug validation.

## Scope

- JWT validation against Keycloak (OAuth2 Resource Server)
- Echo endpoints and request introspection

## Usage

- The resource client is registered in the `e2e-testing` as `resource-server`.
- Acquire tokens from `e2e-testing` (e.g., from the React example) and call this service at `http://localhost:18081`.
- Configure issuer when needed with `KEYCLOAK_ISSUER_URI` (default: `http://localhost:9026/realms/e2e-testing`).
- `GET /health` is public. `GET /get` requires a valid bearer token and returns token/keycloak details (`sub`, `azp`, `aud`, `scope`, `device_id`, `cnf`, timestamps).

## Run

From repository root:

```bash
./gradlew :examples:resource-server-spring-kotlin:bootRun
```

## Related Docs

- `docs/USAGE.md`
- `docs/PLAN.md`
