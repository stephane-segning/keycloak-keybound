# Current: Approval Authenticators (New Device Approval)

This represents the approval branch implemented by `keycloak-keybound-authenticator-enrollment` plus the custom endpoint in `keycloak-keybound-custom-endpoint`.

## Directed Graph

```mermaid
flowchart LR
  U[Actor (User)] -->|attempts sensitive action| C[Client]
  C -->|runs| JS[JS (React native/React)]
  JS -->|starts auth/approval flow| KC[Keycloak]
  KC -->|runs approval authenticators| KCP[Keycloak Plugin]
  KCP -->|create + poll approval| BE[Backend]
  BE -->|approval status| KCP
  KCP -->|challenge/continue| KC
  KC -->|polling UI| JS
  JS -->|polls KC endpoint| KC
  KC -->|serves status| JS
  JS -->|renders| C
  C -->|approved/denied| U
```

```mermaid
sequenceDiagram
autonumber
actor Browser
participant KC as Keycloak (Auth Flow)
participant Start as StartApprovalRequestAuthenticator
participant Wait as WaitForApprovalFormAuthenticator
participant Endpoint as Realm REST Endpoint\n(device-approval)
participant Api as ApiGateway (HTTP)
participant BE as Backend

Browser->>KC: Start auth flow (user already identified)

KC->>Start: authenticate()
note over Start: Read auth session notes (device_id, public_key)\nCompute jkt + parse publicJwk\nCreate approval request in backend
Start->>Api: createApprovalRequest(userId, deviceDescriptor)
Api->>BE: POST /v1/approvals
BE-->>Api: {request_id}
Api-->>Start: request_id
Start-->>KC: save request_id in auth session notes

KC->>Wait: authenticate()
note over Wait: Render waiting page\nCreate signed polling token (realm key)\nExpose pollingUrl + token to UI
Wait-->>Browser: approval-wait.ftl

loop Poll until decision
  Browser->>Endpoint: GET /realms/{realm}/device-approval/status?token=...
  Endpoint->>KC: decode/validate polling token (signature + exp)
  Endpoint->>Api: checkApprovalStatus(request_id)
  Api->>BE: GET /v1/approvals/{request_id}
  BE-->>Api: status
  Api-->>Endpoint: status
  Endpoint-->>Browser: {status}
end

Browser->>KC: POST form submit / continue
KC->>Wait: action()
Wait->>Api: checkApprovalStatus(request_id)
Api->>BE: GET /v1/approvals/{request_id}
BE-->>Api: status
Api-->>Wait: status
alt APPROVED
  Wait-->>KC: success
else DENIED/EXPIRED
  Wait-->>KC: failure
else PENDING
  Wait-->>KC: re-challenge
end
```

Notes:
- The polling endpoint is Keycloak-local, but backend is the source of truth for approval status.
