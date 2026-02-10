# Current: Protocol Mapper (cnf.jkt)

This describes the device-binding protocol mapper behavior.

## Directed Graph

```mermaid
flowchart LR
  U[Actor (User)] -->|uses| C[Client]
  C -->|runs| JS[JS (React native/React)]
  JS -->|token request| KC[Keycloak]
  KC -->|token minting| KCP[Keycloak Plugin]
  KCP -->|adds cnf.jkt claim| KC
  KC -->|token response| JS
  JS -->|store tokens| C
  C -->|authenticated| U
```

```mermaid
sequenceDiagram
autonumber
participant Grant as DeviceKeyGrantType
participant KC as Keycloak Token Manager
participant Mapper as DeviceBindingProtocolMapper

Grant->>KC: set userSession note "cnf.jkt" = jkt
KC->>Mapper: transformAccessToken/transformIDToken
Mapper->>KC: read userSession note "cnf.jkt"
Mapper-->>KC: add claim cnf.jkt to token
```

Notes:
- The mapper only decorates tokens; it does not validate devices.
