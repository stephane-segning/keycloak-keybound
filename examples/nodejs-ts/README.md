# Node.js + TypeScript example

This example demonstrates generating a local device keypair, signing the canonical payload, and calling the `device-public-key-login` realm endpoint. The JSON response is dumped to stdout, and `--bash` pushes the payload metadata as `KC_DEVICE_*` exports.

## Environment variables

Defaults match `compose.yaml`.

- `KEYCLOAK_BASE_URL` (default: `http://localhost:9026`)
- `REALM` (default: `e2e-testing`)

## Install

```bash
npm install
```

## Device public-key login flow

```bash
npm run device-grant -- --bash
```

Optional arguments:
- `--device-id <value>` to use a fixed device identifier.
- `--client-id <value>` to include `client_id` in the JSON body.
- `--device-os <value>`, `--device-model <value>`, `--device-app-version <value>` to override metadata.
- `--pow-nonce <value>` when the realm enforces proof-of-work.
