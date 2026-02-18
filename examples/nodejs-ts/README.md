# Node.js + TypeScript example

This example demonstrates:
- OIDC Authorization Code flow with PKCE against Keycloak
- calling the UserInfo endpoint
- calling the custom grant type `urn:ssegning:params:oauth:grant-type:device_key` after login
- printing both token responses to bash exports

Current custom grant contract:
- required fields include `user_id`, `device_id`, `ts`, `nonce`, `sig`
- `user_id` defaults to OIDC `sub` from UserInfo

## Environment variables

Defaults match `compose.yaml`.

- `KEYCLOAK_BASE_URL` (default: `http://localhost:9026`)
- `REALM` (default: `ssegning-keybound-wh-01`)
- `CLIENT_ID` (default: `node-cli`)
- `REDIRECT_URI` (default: `http://localhost:3005/callback`)

## Install

```bash
npm install
```

## Auth Code + UserInfo

```bash
npm run auth-code
```

## Public-key browser flow (prints bash exports)

```bash
npm run device-grant -- --username test --bash
```

Optional:
- `--grant-user-id <value>` to force the `user_id` sent to the custom grant request.
