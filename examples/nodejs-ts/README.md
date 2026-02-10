# Node.js + TypeScript example

This example demonstrates:
- OIDC Authorization Code flow with PKCE against Keycloak
- calling the UserInfo endpoint
- calling the custom `device_key` grant and printing tokens to bash

## Environment variables

Defaults match `compose.yaml`.

- `KEYCLOAK_BASE_URL` (default: `http://localhost:9026`)
- `REALM` (default: `ssegning-keybound-wh-01`)
- `CLIENT_ID` (default: `node-cli`)
- `REDIRECT_URI` (default: `http://localhost:3005/callback`)
- `WIREMOCK_ADMIN` (default: `http://localhost:8080/__admin`)

## Install

```bash
npm install
```

## Auth Code + UserInfo

```bash
npm run auth-code
```

## Custom grant (prints bash exports)

```bash
npm run device-grant -- --username test --bash
```

