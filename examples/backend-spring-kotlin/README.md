# Backend Spring Kotlin Example

This service implements `openapi/backend.open-api.yml` for local development.

## Scope

- No security
- In-memory persistence
- Full endpoint coverage for enrollment, users, and devices

## Run

From repository root:

```bash
./gradlew :examples:backend-spring-kotlin:bootRun
```

Default local port:
- `http://localhost:18080`

## Dashboard

- Store dashboard: `http://localhost:18080/admin/stores`

## Related Docs

- `docs/USAGE.md`
- `docs/PLAN.md`
