# Current: API Gateway HTTP Client

This is the request path for any backend call made via `ApiGateway` (devices, enrollment, approvals, SMS).

## Directed Graph

```mermaid
flowchart LR
  U[Actor (User)] -->|uses| C[Client]
  C -->|invokes| JS[JS (React native/React)]
  JS -->|HTTP to KC| KC[Keycloak]
  KC -->|executes| KCP[Keycloak Plugin]
  KCP -->|ApiGateway call| BE[Backend]
  BE -->|response| KCP
  KCP -->|response| KC
  KC -->|response| JS
  JS -->|renders| C
  C -->|result| U
```

```mermaid
sequenceDiagram
autonumber
actor Caller as Keycloak Provider Code
participant Api as ApiGateway (HTTP)
participant Gen as OpenAPI Client (Generated)
participant SCF as SimpleCallFactory (OkHttp Call.Factory)
participant Ok as OkHttpClient (Shared Pool)
participant BE as Backend

Caller->>Api: apiGateway.someOperation(...)
Api->>Gen: call generated API method
Gen->>SCF: newCall(Request)
note over SCF: Resolve target base URL (realm-scoped)\nInject headers (X-KC-*, idempotency)\nPropagate/generate trace headers\nOptionally HMAC sign if secret configured\nRewrite URL host/base-path to realm base URL
SCF->>Ok: newCall(finalRequest)
Ok->>BE: HTTPS request (mTLS)
BE-->>Ok: Response
Ok-->>Gen: Response
Gen-->>Api: Model
Api-->>Caller: Result
```

Notes:
- Provider startup may happen with `session.context.realm == null`, so `HttpConfig` must be startup-safe.
- Realm-scoped base URL format is `BACKEND_HTTP_BASE_PATH_<realmName>` with fallback to `BACKEND_HTTP_BASE_PATH`.
- Connection reuse is achieved via a single shared `OkHttpClient` + `ConnectionPool`.
