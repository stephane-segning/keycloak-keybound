# Unified Plan and Status

This file is the merged source of truth for:
- `docs/PLAN.md` (approval or device binding/grant plan)
- `EXAMPLES.md` (examples architecture plan)

Status legend:
- `DONE`: implemented and considered complete
- `PARTIAL`: implemented differently or still open
- `TODO`: not implemented yet
- `OUT`: intentionally out of scope for now

## 1) Approval Subflow and Endpoint

### 1.1 Implemented

- `DONE` ~~Add approval authenticators module (`keycloak-keybound-authenticator-approval`)~~
- `DONE` ~~Implement `StartApprovalRequestAuthenticator`~~
- `DONE` ~~Implement `WaitForApprovalFormAuthenticator`~~
- `DONE` ~~Add custom endpoint module (`keycloak-keybound-custom-endpoint`)~~
- `DONE` ~~Expose approval provider/factory with ID `device-approval`~~
- `DONE` ~~Add approval methods to `ApiGateway` and HTTP implementation~~
- `DONE` ~~Add wait template for approval flow (`approval-wait.ftl`)~~

### 1.2 Remaining

- `PARTIAL` Theme polling split:
  - ~~Inline polling JS in the wait template~~ is implemented
  - `TODO` externalize to `resources/js/device-approval.js` if we still want theme JS modularization
- `TODO` Add explicit endpoint-side rate-limiting policy docs/implementation (if required by production profile)

## 2) Device Binding, Credential Provider, Protocol Mapper, Grant

### 2.1 Implemented

- `DONE` ~~Define and use structured device credential/secret models~~
- `DONE` ~~Implement protocol mapper module (`keycloak-keybound-protocol-mapper`)~~
- `DONE` ~~Implement device-key custom grant module (`keycloak-keybound-grant-device-key`)~~
- `DONE` ~~Grant enforces signature checks (`ts`, `nonce`, signature verification, device status/ownership checks)~~
- `DONE` ~~Grant does not issue refresh token~~
- `DONE` ~~Grant contract moved to `user_id` (instead of username)~~

### 2.2 Remaining

- `PARTIAL` Credential provider lifecycle in Keycloak local store:
  - `OUT` ~~Persist device credentials primarily in local Keycloak credential store~~ (superseded by backend-authoritative persistence)
  - `TODO` decide if `createCredential`/`isValid` should remain intentionally backend-only or be completed for hybrid mode
- `PARTIAL` Mapper rollout:
  - ~~Mapper implementation~~ is complete
  - `TODO` ensure mapper is consistently configured in all realm imports/clients where claims are required

## 3) Example Applications (Merged from EXAMPLES)

### 3.1 Implemented

- `DONE` ~~Web example (`examples/web-vite-react`) with device keypair persistence and auth flow~~
- `DONE` ~~Web example custom-grant fallback when token is missing/expired~~
- `DONE` ~~Backend Spring example implementing OpenAPI contract (dev-focused, in-memory)~~
- `DONE` ~~Resource server Spring example with OAuth2 JWT validation~~
- `DONE` ~~Node TS example for auth-code + custom grant flow~~

### 3.2 Remaining

- `TODO` Keep example docs and scripts continuously aligned with runtime contract changes (especially grant parameters and claims expectations)
- `TODO` Add explicit E2E smoke scripts across examples (web -> keycloak -> backend -> resource) if we want one-command validation

## 4) Superseded Decisions (Struck Through)

- `OUT` ~~Custom grant should resolve user by `username`~~
- `OUT` ~~Custom grant should resolve user by `device_id` only~~
- `DONE` ~~Custom grant uses `user_id` and backend ownership verification~~
- `OUT` ~~Resource server example has no security enforcement~~
- `DONE` ~~Resource server validates JWT as OAuth2 resource server~~
- `OUT` ~~`EXAMPLES.md` as a separate planning authority~~ (merged here; now treated as compatibility entrypoint)

## 5) Now Out of Scope

- `OUT` ~~Full production-grade anti-abuse stack in examples (rate limits, abuse detection, fraud controls)~~
- `OUT` ~~Production database schema and migrations for example backend stores~~
- `OUT` ~~Maintaining two parallel identity contracts for custom grant (`username` and `user_id`)~~

## 6) Next Actionable Backlog

- `TODO` Decide and document final strategy for `DeviceKeyCredential` local-store methods (`createCredential`, `isValid`) in backend-authoritative mode.
- `TODO` Optional: move approval polling JS out of inline FTL into static theme asset.
- `TODO` Optional: add end-to-end smoke test script(s) that validate:
  - auth-code success
  - custom grant renewal path
  - resource server protected endpoint with device-bound claims.

## 7) Execution Checklist

Use this as the working implementation tracker.

- [ ] Decide credential-provider strategy:
  - [ ] Keep backend-authoritative mode and explicitly document `createCredential` / `isValid` behavior
  - [ ] Or implement hybrid/local-store behavior and align grant/mapper expectations
- [ ] Align mapper rollout:
  - [ ] Verify mapper present in all required realm imports
  - [ ] Verify client/client-scope mapping where `cnf.jkt` and `device_id` are required
- [ ] Approval flow frontend decision:
  - [ ] Keep inline polling JS in FTL and mark as intentional
  - [ ] Or extract polling JS to theme static asset (`resources/js/device-approval.js`)
- [ ] Add smoke validation path:
  - [ ] Auth-code login succeeds
  - [ ] Custom grant renewal succeeds with `user_id`
  - [ ] Resource server `/get` returns expected token/device claims
- [ ] Keep docs and examples in sync after each contract change:
  - [ ] `docs/USAGE.md`
  - [ ] `docs/WORKFLOWS/current-device-key-grant.md`
  - [ ] `examples/nodejs-ts`
  - [ ] `examples/web-vite-react`
