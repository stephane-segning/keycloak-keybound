# Current: Enrollment Authenticators (Device Enrollment + Approval Branching)

This represents the enrollment authentication flow implemented by `keycloak-keybound-authenticator-enrollment`.

Assumptions:
- A device constructs a signed blob containing at least `device_id`, `public_key` (JWK), `ts`, `nonce`, `sig`, `device_os`.
- Keycloak flow steps are modular authenticators.
- Device credential persistence is backend-first (Keycloak is not storing device credentials locally).

## Directed Graph

```mermaid
flowchart LR
  U[Actor (User)] -->|opens enrollment link| C[Client]
  C -->|runs| JS[JS (React native/React)]
  JS -->|generates keypair + signs blob| JS
  JS -->|sends enrollment request| KC[Keycloak]
  KC -->|runs authenticators| KCP[Keycloak Plugin]
  KCP -->|route to approval or OTP then bind device| BE[Backend]
  BE -->|policy + bind result| KCP
  KCP -->|flow success/fail| KC
  KC -->|UI response| JS
  JS -->|show status| C
  C -->|status| U
```

```mermaid
sequenceDiagram
autonumber
actor Device
actor Admin as Admin/Operator
participant Browser
participant KC as Keycloak (Auth Flow)
participant Ingest as IngestSignedDeviceBlobAuthenticator
participant Verify as VerifySignedBlobAuthenticator
participant Phone as CollectPhoneFormAuthenticator
participant CheckUser as CheckUserByPhoneAuthenticator
participant Route as RouteEnrollmentPathAuthenticator
participant StartApproval as StartApprovalRequestAuthenticator
participant WaitApproval as WaitForApprovalFormAuthenticator
participant Otp as SendValidateOtpAuthenticator
participant FindOrCreate as FindOrCreateUserAuthenticator
participant Persist as PersistDeviceCredentialAuthenticator
participant Api as ApiGateway (HTTP)
participant BE as Backend

Device->>Browser: Open enrollment URL with query params\n(device_id, public_key, ts, nonce, sig, device_os, ...)
Browser->>KC: GET /auth/... (start flow)

KC->>Ingest: authenticate()
note over Ingest: Read request params\nStore into auth session notes
Ingest-->>KC: success

KC->>Verify: authenticate()
note over Verify: Check ts window\nPrevent nonce replay\nVerify signature over canonical JSON
Verify-->>KC: success / failure

KC->>Phone: authenticate()
note over Phone: Render phone form\nCollect phone only
Phone-->>KC: challenge(form)

Browser->>KC: POST phone
KC->>Phone: action()
Phone-->>KC: success

KC->>CheckUser: authenticate()
note over CheckUser: Resolve existing user by collected phone
CheckUser-->>KC: success (context.user may be set)

KC->>Route: authenticate()
note over Route: if existing user has device credentials -> approval path\nelse -> otp path
Route-->>KC: success

alt approval path
  KC->>StartApproval: authenticate()
  StartApproval->>Api: createApprovalRequest(user, device)
  Api->>BE: POST /v1/approvals
  BE-->>Api: {request_id}
  Api-->>StartApproval: request_id
  StartApproval-->>KC: success

  KC->>WaitApproval: authenticate()
  WaitApproval-->>Browser: approval-wait.ftl

  loop until approved/denied/expired
    Browser->>KC: poll approval status endpoint
    KC->>Api: checkApprovalStatus(request_id)
    Api->>BE: GET /v1/approvals/{request_id}
    BE-->>Api: status
    Api-->>KC: status
  end
else otp path
  KC->>Otp: authenticate()
  Otp->>Api: sendSmsAndGetHash(...)
  Api->>BE: POST /v1/sms/send
  BE-->>Api: {hash}
  Api-->>Otp: hash
  Otp-->>KC: challenge(enroll-verify-phone.ftl)

  Browser->>KC: POST otp
  KC->>Otp: action()
  Otp->>Api: confirmSmsCode(...)
  Api->>BE: POST /v1/sms/confirm
  BE-->>Api: {confirmed}
  Api-->>Otp: confirmed
  Otp-->>KC: success / failure

  KC->>FindOrCreate: authenticate()
  note over FindOrCreate: Find or create user from verified phone
  FindOrCreate-->>KC: set context.user
end

KC->>Persist: authenticate()
note over Persist: Compute JKT thumbprint from public JWK\nPrecheck policy then bind device to user in backend
Persist->>Api: enrollmentPrecheck(user, device)
Api->>BE: POST /v1/enrollments/precheck
BE-->>Api: decision
Api-->>Persist: decision
alt decision == ALLOW
  Persist->>Api: enrollmentBind(user, device, attributes, proof)
  Api->>BE: POST /v1/enrollments/bind
  BE-->>Api: bound
  Api-->>Persist: true
  Persist-->>KC: success
else decision != ALLOW
  Persist-->>KC: failure (access denied)
end

KC-->>Browser: Enrollment complete / error
```

Notes:
- Flow sequence is now DK1 -> DK2 -> DK3 -> DK4 -> DK5, then either:
  DK6 -> DK7 -> DK10 (approval path) or DK8 -> DK9 -> DK10 (OTP path).
