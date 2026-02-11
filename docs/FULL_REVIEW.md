# Full Review (Merged)

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
  - Reads/deletes are routed to backend APIs; backend user-id resolution now aligns with backend ids.

- `DONE` ~~Device IDs are not first-class credential identifiers~~
  Evidence:
  - Grant requires `device_id`: `keycloak-keybound-grant-device-key/src/main/kotlin/grants/DeviceKeyGrantType.kt:59`
  - Token includes `device_id`: `keycloak-keybound-grant-device-key/src/main/kotlin/grants/DeviceKeyGrantType.kt:272`
  - Enrollment bind uses `deviceId` + `jkt`: `keycloak-keybound-authenticator-enrollment/src/main/kotlin/com/ssegning/keycloak/keybound/authenticator/enrollment/PersistDeviceCredentialAuthenticator.kt:48`

- `PARTIAL` ~~Java 21+ is uniformly enforced across all modules~~
  Current state:
  - Enforced in some modules: `keycloak-keybound-api-gateway-http/build.gradle.kts:33`, `keycloak-keybound-grant-device-key/build.gradle.kts:32`
  - Not explicitly enforced in other Keycloak plugin modules (see finding F-1).

## PR #3 Comment Coverage

Source PR:
- `https://github.com/stephane-segning/keycloak-keybound/pull/3`

High-level review threads:
- `INFO` PR summary thread (Gemini): `3883684546` (issue comment), `3784124711` (review body). Incorporated below in Code/Security/Future-Proofing findings.
- `INFO` Codex review wrapper thread: `3784130396` (non-actionable wrapper text only).

Inline review comments (`pulls/3/comments`) mapped to this file:
- `DONE` ~~PII logging concerns (all still reproducible in current code)~~: `2792650646`, `2792650651`, `2792650654`, `2792650660`, `2792650663`, `2792650666`, `2792650670`, `2792650674`, `2792650678`, `2792650680`, `2792650683`, `2792650689`, `2792650694`, `2792650697`.
  Resolution note: logs now avoid phone/username/email values in auth and API flows.
- `DONE` ~~Workflow docs mention precheck while runtime removed precheck~~: `2792650698`.
  Resolution note: old workflow docs were removed; consolidated state is documented in this file (`C-3`).
- `DONE` ~~Phone lookup ambiguity in backend example store~~: `2792650700`.
  Resolution note: example lookup now only matches phone attributes.
- `DONE` ~~Readability suggestions for fully qualified names/imports in `Api.kt` and `CheckUserByPhoneAuthenticator.kt`~~: `2792650704`, `2792650719`, `2792650726`.
  Resolution note: import alias used for `EnrollmentPath`.
 - `DONE` ~~Approval-path routing can fail instead of OTP fallback when backend/user mapping is out of sync~~: `2792656255`.
  Resolution note: fallback to OTP when no Keycloak user is resolved.

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

- `DONE` ~~Theme custom message keys are referenced, but no messages bundle is present in this theme package.~~
  Evidence: messages bundle added at `keycloak-keybound-theme/src/main/resources/theme/base/login/messages/messages.properties:1`.

- `DONE` ~~Backend example phone lookup is overly broad and can match username/email.~~
  Evidence: matching limited to phone attributes in `examples/backend-spring-kotlin/src/main/kotlin/com/ssegning/keycloak/keybound/examples/backend/store/BackendDataStore.kt:451`.

- `DONE` ~~Readability issues from fully qualified names where import aliases/simple imports would be clearer.~~
  Evidence: alias used in `keycloak-keybound-api-gateway-http/src/main/kotlin/Api.kt:12` and `keycloak-keybound-authenticator-enrollment/src/main/kotlin/com/ssegning/keycloak/keybound/authenticator/enrollment/CheckUserByPhoneAuthenticator.kt:4`.

- `DONE` ~~Protocol mapper note contract is mismatched with producers.~~
  Evidence: mapper now accepts `cnf.jkt` in `keycloak-keybound-protocol-mapper/src/main/kotlin/com/ssegning/keycloak/keybound/mapper/DeviceBindingProtocolMapper.kt:21`.

- `DONE` ~~Enrollment path can select approval even when no Keycloak user is resolvable.~~
  Evidence: OTP fallback enforced in `keycloak-keybound-authenticator-enrollment/src/main/kotlin/com/ssegning/keycloak/keybound/authenticator/enrollment/CheckUserByPhoneAuthenticator.kt:40`.

- `DONE` ~~Credential provider uses Keycloak internal `user.id` where backend user id is expected.~~
  Evidence: credential provider now resolves backend user id in `keycloak-keybound-credentials-device-key/src/main/kotlin/credentials/DeviceKeyCredential.kt:67`.

### Open Findings

- `MEDIUM` C-3: Enrollment precheck API exists but is not part of runtime authenticator execution.
  Evidence:
  - Runtime bind only: `keycloak-keybound-authenticator-enrollment/src/main/kotlin/com/ssegning/keycloak/keybound/authenticator/enrollment/PersistDeviceCredentialAuthenticator.kt:56`
  - Precheck API exists but unused by authenticators: `keycloak-keybound-api-gateway-http/src/main/kotlin/Api.kt:184`
  Impact:
  - Policy gate is backend-only at bind-time; if precheck gating is desired in-auth-flow it is currently missing.

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

- `DONE` ~~PII is logged in cleartext across API gateway and enrollment authenticators.~~
  Evidence: logs now avoid phone/username/email values in `keycloak-keybound-api-gateway-http/src/main/kotlin/Api.kt:60` and key enrollment authenticators.

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

1. ~~Fix enrollment routing fallback so unresolved users cannot enter approval path (`C-5`).~~
2. ~~Remove/mask PII from auth and API logs (`S-4`).~~
3. ~~Fix credential-provider user-id mapping to backend ids (`C-1`) before relying on admin-side device operations.~~
4. ~~Unify mapper/session-note contract (`C-2`) and decide whether mapper or grant is authoritative for `cnf`/`device_id` claims.~~
5. Enforce Java 21 toolchain in all plugin modules (`F-1`).
6. ~~Restrict backend phone lookup matching to dedicated phone attributes (`C-6`).~~
7. Harden approval polling token context binding (`S-1`).
