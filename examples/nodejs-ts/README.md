# Node.js + TypeScript example

This example demonstrates:
- OIDC Authorization Code flow with PKCE against Keycloak
- calling the UserInfo endpoint
- starting a custom public-key browser auth flow
- then calling the custom grant type `urn:ssegning:params:oauth:grant-type:device_key`
- printing both token responses to bash exports

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
- `--grant-username test` to force the username sent to the custom grant request.
