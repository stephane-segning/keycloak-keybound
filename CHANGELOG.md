# Changelog

All notable changes to this project should be documented in this file.

## Unreleased

## [0.1.0] - 2026-02-11

- Documentation structure standardized: added `docs/README.md` as the index, added `EXAMPLES.md` for cross-example architecture, and normalized example READMEs plus the custom grant `user_id` wording.
- Approval polling now survives token validation by binding the signed payload (now carrying `tab_id`) to `RootAuthenticationSession` + tab-aware client sessions (`WaitForApprovalFormAuthenticator.kt`, `DeviceApprovalResource.kt`, `ApprovalPollingTokenClaims.kt`), updating `approval-wait.js` to treat HTTP `401` as `UNAUTHORIZED`, and filling the missing `msg(...)` keys in `messages/messages.properties`.
- Canonical signing payloads (`DeviceSignaturePayload`/`RequestSignaturePayload`) are shared between the frontend, authenticators, grants, and signed-resource filters, and the new `docs/SIGNING_AND_VERIFICATION.md` explains the protocol contracts and browser failure mapping with pseudocode.
- Added a protocol mapper regression test, aligned Kotlin toolchain documentation, and refreshed backend imports to stay consistent with the new flows.
