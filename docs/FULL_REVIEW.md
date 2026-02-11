# Full Review (Merged)

Date: 2026-02-11

This file merges and replaces:
- `docs/CODE_REVIEW.md`
- `docs/SECURITY.md`
- `docs/PLAN.md`
- `docs/README.md`
- `docs/STRUCTURE.md`
- `docs/USAGE.md`
- `docs/WORKFLOWS/README.md`
- `docs/WORKFLOWS/current-api-gateway-http.md`
- `docs/WORKFLOWS/current-approval-authenticators.md`
- `docs/WORKFLOWS/current-credential-provider.md`
- `docs/WORKFLOWS/current-device-key-grant.md`
- `docs/WORKFLOWS/current-enrollment-authenticators.md`
- `docs/WORKFLOWS/current-protocol-mapper.md`
- `docs/WORKFLOWS/proposed-user-storage-spi.md`

`docs/recommendations.md` is intentionally kept separate.

Build verification performed:
- `./gradlew clean build` -> `BUILD SUCCESSFUL` on 2026-02-11.

## Requirement Status

- `DONE` ~~Client-side keypair generation and token endpoint usage are missing~~
  Evidence:
  - `examples/web-vite-react/src/hooks/use-device-storage.ts:34`
  - `examples/web-vite-react/src/lib/auth.ts:62`
  - `examples/nodejs-ts/src/device-grant.ts:42`
  - `examples/nodejs-ts/src/device-grant.ts:202`

- `PARTIAL` ~~Device credentials are locally managed in Keycloak store by default~~
  Current state:
  - Local create is blocked as intended: `keycloak-keybound-credentials-device-key/src/main/kotlin/credentials/DeviceKeyCredential.kt:27`
  - Reads/deletes are routed to backend APIs, but backend user-id resolution is inconsistent in credential provider (see open finding C-1).

- `DONE` ~~Device IDs are not first-class credential identifiers~~
  Evidence:
  - Grant requires `device_id`: `keycloak-keybound-grant-device-key/src/main/kotlin/grants/DeviceKeyGrantType.kt:59`
  - Token includes `device_id`: `keycloak-keybound-grant-device-key/src/main/kotlin/grants/DeviceKeyGrantType.kt:272`
  - Enrollment bind uses `deviceId` + `jkt`: `keycloak-keybound-authenticator-enrollment/src/main/kotlin/com/ssegning/keycloak/keybound/authenticator/enrollment/PersistDeviceCredentialAuthenticator.kt:48`

- `PARTIAL` ~~Java 21+ is uniformly enforced across all modules~~
  Current state:
  - Enforced in some modules: `keycloak-keybound-api-gateway-http/build.gradle.kts:33`, `keycloak-keybound-grant-device-key/build.gradle.kts:32`
  - Not explicitly enforced in other Keycloak plugin modules (see finding F-1).

## Code Review

### Implemented / Closed Items (struck through)

- `DONE` ~~Core package typo `authentcator`~~
  Evidence: current package path is `core/authenticator` (`keycloak-keybound-core/src/main/kotlin/com/ssegning/keycloak/keybound/core/authenticator/AbstractAuthenticator.kt:1`).

- `DONE` ~~`DEVICE_NONE_NOTE_NAME` typo~~
  Evidence: now `DEVICE_NONCE_NOTE_NAME` (`keycloak-keybound-authenticator-enrollment/src/main/kotlin/com/ssegning/keycloak/keybound/authenticator/enrollment/authenticator/AbstractKeyAuthenticator.kt:10`).

- `DONE` ~~Approval endpoint path mismatch (`device-approval-resource` vs `device-approval`)~~
  Evidence: `device-approval` (`keycloak-keybound-custom-endpoint/src/main/kotlin/com/ssegning/keycloak/keybound/endpoint/DeviceApprovalResourceProviderFactory.kt:11`).

- `DONE` ~~Unimplemented API gateway SMS methods~~
  Evidence: implemented methods in `keycloak-keybound-api-gateway-http/src/main/kotlin/Api.kt:35` and `keycloak-keybound-api-gateway-http/src/main/kotlin/Api.kt:75`.

- `DONE` ~~Custom grant was a TODO stub~~
  Evidence: full process implementation in `keycloak-keybound-grant-device-key/src/main/kotlin/grants/DeviceKeyGrantType.kt:40`.

- `DONE` ~~Grant event type wrong~~
  Evidence: `EventType.LOGIN` in `keycloak-keybound-grant-device-key/src/main/kotlin/grants/DeviceKeyGrantType.kt:38`.

- `DONE` ~~Theme polling script only inline in FTL~~
  Evidence: external script loaded from `keycloak-keybound-theme/src/main/resources/theme/base/login/approval-wait.ftl:26` and implemented in `keycloak-keybound-theme/src/main/resources/theme/base/login/resources/js/approval-wait.js:1`.

### Open Findings

- `HIGH` C-1: Credential provider uses Keycloak internal `user.id` where backend user id is expected.
  Affected:
  - `keycloak-keybound-credentials-device-key/src/main/kotlin/credentials/DeviceKeyCredential.kt:65`
  - `keycloak-keybound-credentials-device-key/src/main/kotlin/credentials/DeviceKeyCredential.kt:76`
  - `keycloak-keybound-credentials-device-key/src/main/kotlin/credentials/DeviceKeyCredential.kt:113`
  Impact:
  - Device listing/configuration detection can fail for federated users (`StorageId` ids).
  - Delete/disable may target wrong backend user id.
  Recommendation:
  - Resolve backend user id consistently (same strategy as `KeyboundUserResolver.resolveBackendUserId`).

- `MEDIUM` C-2: Protocol mapper note contract is mismatched with producers.
  Affected:
  - Mapper expects `jkt`: `keycloak-keybound-protocol-mapper/src/main/kotlin/com/ssegning/keycloak/keybound/mapper/DeviceBindingProtocolMapper.kt:19`
  - Grant sets `cnf.jkt`: `keycloak-keybound-grant-device-key/src/main/kotlin/grants/DeviceKeyGrantType.kt:250`
  Impact:
  - Mapper can silently produce no `cnf.jkt` claim unless another component sets `jkt` note.

- `MEDIUM` C-3: Enrollment workflow docs and runtime diverge around precheck.
  Evidence:
  - Runtime bind only: `keycloak-keybound-authenticator-enrollment/src/main/kotlin/com/ssegning/keycloak/keybound/authenticator/enrollment/PersistDeviceCredentialAuthenticator.kt:56`
  - Precheck API exists but unused by authenticators: `keycloak-keybound-api-gateway-http/src/main/kotlin/Api.kt:184`
  Impact:
  - Intended policy gate is backend-only at bind-time; operational expectation may differ.

- `LOW` C-4: Theme custom message keys are referenced, but no messages bundle is present in this theme package.
  Affected files:
  - `keycloak-keybound-theme/src/main/resources/theme/base/login/approval-wait.ftl:4`
  - `keycloak-keybound-theme/src/main/resources/theme/base/login/enroll-collect-phone.ftl:4`
  - `keycloak-keybound-theme/src/main/resources/theme/base/login/enroll-verify-phone.ftl:4`
  Impact:
  - Depending on inherited theme messages, UI may show raw key names.

## Security Review

### Implemented / Closed Items (struck through)

- `DONE` ~~Canonical string ambiguity from raw concatenation~~
  Evidence: canonical JSON map serialization used in:
  - `keycloak-keybound-authenticator-enrollment/src/main/kotlin/com/ssegning/keycloak/keybound/authenticator/enrollment/VerifySignedBlobAuthenticator.kt:66`
  - `keycloak-keybound-grant-device-key/src/main/kotlin/grants/DeviceKeyGrantType.kt:189`

- `DONE` ~~Algorithm confusion via user-controlled `alg` in JWK~~
  Evidence: strict server-side `ES256` in:
  - `keycloak-keybound-authenticator-enrollment/src/main/kotlin/com/ssegning/keycloak/keybound/authenticator/enrollment/VerifySignedBlobAuthenticator.kt:76`
  - `keycloak-keybound-grant-device-key/src/main/kotlin/grants/DeviceKeyGrantType.kt:205`

- `DONE` ~~No nonce replay protection~~
  Evidence:
  - Enrollment flow nonce cache: `keycloak-keybound-authenticator-enrollment/src/main/kotlin/com/ssegning/keycloak/keybound/authenticator/enrollment/VerifySignedBlobAuthenticator.kt:54`
  - Grant flow nonce cache: `keycloak-keybound-grant-device-key/src/main/kotlin/grants/DeviceKeyGrantType.kt:163`

- `DONE` ~~Unbounded signed blob input persisted in auth session~~
  Evidence: max length guard in `keycloak-keybound-authenticator-enrollment/src/main/kotlin/com/ssegning/keycloak/keybound/authenticator/enrollment/IngestSignedDeviceBlobAuthenticator.kt:42`.

- `DONE` ~~Phone ownership never verified prior to account creation~~
  Evidence: OTP challenge/verification path in `keycloak-keybound-authenticator-enrollment/src/main/kotlin/com/ssegning/keycloak/keybound/authenticator/enrollment/SendValidateOtpAuthenticator.kt:20` and `keycloak-keybound-authenticator-enrollment/src/main/kotlin/com/ssegning/keycloak/keybound/authenticator/enrollment/SendValidateOtpAuthenticator.kt:79`.

### Open Findings

- `MEDIUM` S-1: Approval polling token is not bound to user/session/client context.
  Affected:
  - Token content includes only `request_id` + `exp`: `keycloak-keybound-authenticator-enrollment/src/main/kotlin/com/ssegning/keycloak/keybound/authenticator/enrollment/WaitForApprovalFormAuthenticator.kt:98`
  - Endpoint validates signature/exp and uses `request_id`: `keycloak-keybound-custom-endpoint/src/main/kotlin/com/ssegning/keycloak/keybound/endpoint/DeviceApprovalResource.kt:35`
  Impact:
  - Any holder of a valid polling token can query approval status until expiry.
  Recommendation:
  - Add `sid`, `client_id`, and/or `sub` claims and enforce match in endpoint.

- `LOW` S-2: Nonce replay keys are global string keys without realm scoping.
  Affected:
  - `avoid-replay:$nonce`: `keycloak-keybound-authenticator-enrollment/src/main/kotlin/com/ssegning/keycloak/keybound/authenticator/enrollment/VerifySignedBlobAuthenticator.kt:51`
  - `device-grant-replay:$nonce`: `keycloak-keybound-grant-device-key/src/main/kotlin/grants/DeviceKeyGrantType.kt:164`
  Impact:
  - Cross-realm nonce collisions can cause false rejections under shared cache backends.

- `LOW` S-3: Example/dev configuration ships plaintext bootstrap credentials.
  Affected:
  - `compose.yaml:2`
  - `compose.yaml:8`
  Recommendation:
  - Keep as dev-only and document stronger production secret handling explicitly.

## Future-Proofing Review

### Implemented / Closed Items (struck through)

- `DONE` ~~Approval subflow and custom endpoint are missing~~
  Evidence:
  - Start approval authenticator: `keycloak-keybound-authenticator-enrollment/src/main/kotlin/com/ssegning/keycloak/keybound/authenticator/enrollment/StartApprovalRequestAuthenticator.kt:18`
  - Wait authenticator: `keycloak-keybound-authenticator-enrollment/src/main/kotlin/com/ssegning/keycloak/keybound/authenticator/enrollment/WaitForApprovalFormAuthenticator.kt:17`
  - Endpoint factory: `keycloak-keybound-custom-endpoint/src/main/kotlin/com/ssegning/keycloak/keybound/endpoint/DeviceApprovalResourceProviderFactory.kt:7`

- `DONE` ~~Custom device-key grant is not operational~~
  Evidence: `keycloak-keybound-grant-device-key/src/main/kotlin/grants/DeviceKeyGrantType.kt:40`.

- `DONE` ~~`user_id` grant contract migration is not implemented~~
  Evidence: required parameter and validation in `keycloak-keybound-grant-device-key/src/main/kotlin/grants/DeviceKeyGrantType.kt:63`.

- `DONE` ~~Client examples do not demonstrate keypair + custom-grant renewal~~
  Evidence:
  - Browser example renewal: `examples/web-vite-react/src/lib/auth.ts:212`
  - Node CLI custom grant flow: `examples/nodejs-ts/src/device-grant.ts:114`

- `DONE` ~~Top-level docs conflict with Java baseline and current architecture~~
  Evidence:
  - Root README now states Java 21+: `README.md:37`
  - User storage module now documented as implemented: `README.md:31`

### Open Findings

- `MEDIUM` F-1: Java 21 requirement is not consistently enforced across all plugin subprojects.
  Evidence:
  - Enforced in two modules only: `keycloak-keybound-api-gateway-http/build.gradle.kts:33`, `keycloak-keybound-grant-device-key/build.gradle.kts:32`
  - Missing toolchain declarations in modules like:
    - `keycloak-keybound-core/build.gradle.kts:4`
    - `keycloak-keybound-authenticator-enrollment/build.gradle.kts:1`
    - `keycloak-keybound-custom-endpoint/build.gradle.kts:1`
    - `keycloak-keybound-credentials-device-key/build.gradle.kts:1`

- `LOW` F-3: Minimal realm imports do not preconfigure the protocol mapper.
  Impact:
  - Device claims via mapper depend on manual admin setup.

- `LOW` F-4: No meaningful automated test coverage in provider modules.
  Evidence:
  - Build output shows mostly `NO-SOURCE` tests.

## Prioritized Backlog

1. Fix credential-provider user-id mapping to backend ids (`C-1`) before relying on admin-side device operations.
2. Unify mapper/session-note contract (`C-2`) and decide whether mapper or grant is authoritative for `cnf`/`device_id` claims.
3. Enforce Java 21 toolchain in all plugin modules (`F-1`).
4. Decide whether enrollment precheck must be an explicit Keycloak-side gate or remain backend bind-only (`C-3`).
5. Harden approval polling token context binding (`S-1`).
