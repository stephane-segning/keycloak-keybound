# Workflows (Sequence Diagrams)

This folder documents the runtime workflows for this project using Mermaid sequence diagrams.

Goals:
- Make current behavior explicit (what calls what, and why).
- Provide a shared reference before implementing the user-storage SPI.

Conventions:
- `KC` = Keycloak server runtime (custom providers installed).
- `ApiGateway` = `com.ssegning.keycloak.keybound.core.spi.ApiGateway` implementation (HTTP gateway).
- Backend calls are server-to-server (mTLS) and include `X-KC-*` headers injected by `SimpleCallFactory`.

## Files

- `current-*.md` describe the currently implemented flows per custom SPI group.
- `proposed-user-storage-spi.md` describes the intended future behavior for backend-backed users.

## Related Docs

- `docs/USAGE.md`
- `docs/STRUCTURE.md`
- `docs/README.md`
