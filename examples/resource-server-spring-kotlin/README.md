# Resource Server Spring Kotlin Example

This service is a local httpbin-like resource server for token/debug validation.

Scope:
- No security enforcement
- Echo endpoints and request introspection

Usage:
- The resource client is registered in the `e2e-realm` as `resource-server`.
- Acquire tokens from `e2e-realm` (e.g., from the React example) and call this service at `http://localhost:18081`.
