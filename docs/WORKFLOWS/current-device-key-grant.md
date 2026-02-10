# Current: Device Key OAuth2 Grant Type

This is the `urn:ssegning:params:oauth:grant-type:device_key` flow implemented by `keycloak-keybound-grant-device-key`.

## Directed Graph

```mermaid
flowchart LR
  U[Actor (User)] -->|uses| C[Client]
  C -->|runs| JS[JS (React native/React)]
  JS -->|sign request (device key)| JS
  JS -->|token request| KC[Keycloak]
  KC -->|grant processing| KCP[Keycloak Plugin]
  KCP -->|lookup device + verify| BE[Backend]
  BE -->|public key + status| KCP
  KCP -->|tokens| KC
  KC -->|token response| JS
  JS -->|store tokens| C
  C -->|authenticated| U
```

```mermaid
sequenceDiagram
autonumber
actor Device
participant Token as Keycloak Token Endpoint
participant Grant as DeviceKeyGrantType
participant Api as ApiGateway (HTTP)
participant BE as Backend
participant KC as Keycloak Session/Token Manager

Device->>Token: POST /protocol/openid-connect/token\n(grant_type=device_key, user_id, device_id, ts, nonce, sig, ...)
Token->>Grant: process(context)

Grant->>KC: lookup user by user_id
KC-->>Grant: UserModel

Grant->>Api: lookupDevice(device_id)
Api->>BE: POST /v1/devices/lookup
BE-->>Api: {found, user_id, device, public_jwk}
Api-->>Grant: lookup result

Grant->>Grant: verify lookup.user_id == kcUser.id\nverify device status == ACTIVE
Grant->>Grant: verify signature using public_jwk\n(ts window + nonce replay protection)

Grant->>KC: create UserSession + ClientSession
Grant->>KC: mint access token with device-binding claims\n(device_id + cnf.jkt)
KC-->>Device: tokens (access + optional id)
```

Notes:
- Backend is authoritative for device binding, device status, and public key material.
- This custom grant is intended to be “access-token only” (no refresh token).
- The protocol mapper exists, but is not configured in the minimal dev realm import by default.
