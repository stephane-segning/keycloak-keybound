# Current: Enrollment Authenticators (Device Enrollment)

This represents the enrollment authentication flow implemented by `keycloak-keybound-authenticator-enrollment`.

Assumptions:
- A device constructs a signed blob containing at least `device_id`, `public_key` (JWK), `ts`, `nonce`, `sig`.
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
  KCP -->|OTP + bind device| BE[Backend]
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
participant User as FindOrCreateUserAuthenticator
participant Persist as PersistDeviceCredentialAuthenticator
participant Api as ApiGateway (HTTP)
participant BE as Backend

Device->>Browser: Open enrollment URL with query params\n(device_id, public_key, ts, nonce, sig, ...)
Browser->>KC: GET /auth/... (start flow)

KC->>Ingest: authenticate()
note over Ingest: Read request params\nStore into auth session notes
Ingest-->>KC: success

KC->>Verify: authenticate()
note over Verify: Check ts window\nPrevent nonce replay\nVerify signature over canonical JSON
Verify-->>KC: success / failure

KC->>Phone: authenticate()
note over Phone: Render phone form\nSend OTP via backend\nConfirm OTP
Phone->>Api: sendSmsAndGetHash(...)
Api->>BE: POST /v1/sms/send
BE-->>Api: {hash}
Api-->>Phone: hash
Phone-->>KC: challenge(form)

Browser->>KC: POST phone + otp
KC->>Phone: action()
Phone->>Api: confirmSmsCode(...)
Api->>BE: POST /v1/sms/confirm
BE-->>Api: {confirmed}
Api-->>Phone: confirmed
Phone-->>KC: success / failure

KC->>User: authenticate()
note over User: Find or create user\n(currently Keycloak-local user model)
User-->>KC: set context.user

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
- Once the user-storage SPI is implemented, `FindOrCreateUserAuthenticator` can delegate to backend-backed users.
