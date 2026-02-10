# Current: Credential Provider (Admin-Facing Device Credential)

This describes how the `device-key-credential-provider` behaves now that backend is the source of truth.

Scope:
- Reading: backend lookup/list is used.
- Deleting: translates to backend disable.
- Creating/updating: intentionally not supported via Keycloak credential APIs.

## Directed Graph

```mermaid
flowchart LR
  U[Actor (User)] -->|admin actions| C[Client]
  C -->|admin UI requests| KC[Keycloak]
  KC -->|calls| KCP[Keycloak Plugin]
  KCP -->|list/lookup/disable device| BE[Backend]
  BE -->|device records| KCP
  KCP -->|renderable state| KC
  KC -->|admin UI response| C
  C -->|admin sees changes| U
```

```mermaid
sequenceDiagram
autonumber
actor Admin
participant AdminUI as Keycloak Admin UI
participant KC as Keycloak Server
participant Cred as DeviceKeyCredential (CredentialProvider)
participant Api as ApiGateway (HTTP)
participant BE as Backend

Admin->>AdminUI: View user credentials / devices
AdminUI->>KC: Request credential configuration state
KC->>Cred: isConfiguredFor(user, type)
Cred->>Api: listUserDevices(userId, includeDisabled=false)
Api->>BE: GET /v1/users/{user_id}/devices?include_revoked=false
BE-->>Api: devices
Api-->>Cred: devices
Cred-->>KC: configured? (any ACTIVE)

Admin->>AdminUI: Disable device credential
AdminUI->>KC: Delete credential (by id)
KC->>Cred: deleteCredential(user, credentialId)
Cred->>Api: disableDevice(userId, deviceId)
Api->>BE: POST /v1/users/{user_id}/devices/{device_id}/disable
BE-->>Api: ok
Api-->>Cred: true
Cred-->>KC: success
```

Notes:
- For admin display, Keycloak may call methods that assume local storage; this provider intentionally routes to backend.
