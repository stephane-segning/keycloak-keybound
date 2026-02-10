# Proposed: User Storage SPI (Backend-Backed Users)

Goal:
- Backend is the source of truth for users (create/update/(de)activate/query by id and by attributes).
- Keycloak is used for admin workflows, sessions, and token issuance, but resolves users through a User Storage Provider.

This document focuses on sequence diagrams; it does not prescribe exact Keycloak interfaces yet.

## Directed Graph

```mermaid
flowchart LR
  U[Actor (User)] -->|admin/operator| C[Client]
  C -->|admin actions| KC[Keycloak]
  KC -->|resolve/query users| KCP[Keycloak Plugin]
  KCP -->|User Storage SPI calls| BE[Backend]
  BE -->|user records| KCP
  KCP -->|UserModel + search results| KC
  KC -->|admin UI response| C
  C -->|admin sees users| U
```

## Admin: Create / Update User

```mermaid
sequenceDiagram
autonumber
actor Admin
participant AdminUI as Keycloak Admin UI
participant KC as Keycloak Admin REST
participant USP as UserStorageProvider (custom)
participant BE as Backend

Admin->>AdminUI: Create or update user
AdminUI->>KC: POST/PUT user payload
KC->>USP: create/update user via SPI
USP->>BE: POST/PUT /v1/users (backend user record)
BE-->>USP: user_id + canonical user
USP-->>KC: UserModel (backed by external id)
KC-->>AdminUI: success
```

## Admin: Query Users (Search by Attribute)

```mermaid
sequenceDiagram
autonumber
actor Admin
participant AdminUI as Keycloak Admin UI
participant KC as Keycloak Admin REST
participant USP as UserStorageProvider (custom)
participant BE as Backend

Admin->>AdminUI: Search users (email/phone/external id)
AdminUI->>KC: GET users?search=...
KC->>USP: queryUsers(criteria)
USP->>BE: GET /v1/users:search (by attributes)
BE-->>USP: list of backend users
USP-->>KC: stream/list of UserModels
KC-->>AdminUI: results
```

## Auth Flow: Find or Create User Using Backend

This shows how enrollment could evolve once users are backend-backed.

```mermaid
sequenceDiagram
autonumber
actor Browser
participant KC as Keycloak (Auth Flow)
participant Phone as CollectPhoneFormAuthenticator
participant User as FindOrCreateUserAuthenticator (updated)
participant USP as UserStorageProvider (custom)
participant BE as Backend

Browser->>KC: start enrollment flow
KC->>Phone: collect/verify phone via backend
Phone-->>KC: success (verified phone)

KC->>User: authenticate()
note over User: Use verified attributes (phone/email)\nFind or create user in backend
User->>USP: find user by attribute
USP->>BE: GET /v1/users:search?phone=...
BE-->>USP: user or none
alt user exists
  USP-->>User: UserModel
else missing
  User->>USP: create user
  USP->>BE: POST /v1/users
  BE-->>USP: created user
  USP-->>User: UserModel
end
User-->>KC: context.user set
```

## Key Design Points (for implementation)

- Keycloak requires a `UserModel` for sessions/tokens; the provider must map backend `user_id` to Keycloak user identity consistently.
- For admin-only usage, the provider must support query/search efficiently (avoid full scans).
- Prefer an immutable external id (`backend_user_id`) and store it as the Keycloak “external id” or username mapping.
