# Keycloak Keybound

Keycloak extensions and runnable examples for device-bound authentication flows.

## Start Here

- Usage guide: `docs/USAGE.md`
- Examples architecture: `EXAMPLES.md`
- Workflow docs: `docs/WORKFLOWS/README.md`

## Custom Device Grant

- Grant type: `urn:ssegning:params:oauth:grant-type:device_key`
- Required request fields: `user_id`, `device_id`, `ts`, `nonce`, `sig`
- `user_id` is expected to be the OIDC subject (`sub`) from the auth-code login.
