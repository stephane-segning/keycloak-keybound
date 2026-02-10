# Node.js + TypeScript example

This example demonstrates:
- OIDC Authorization Code flow with PKCE against Keycloak
- calling the UserInfo endpoint
- starting a custom public-key browser auth flow and printing tokens to bash

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
