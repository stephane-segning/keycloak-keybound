# Web Vite React Public-Key Login

Minimal web client dedicated to the `device-public-key-login` realm resource. It generates a local device key, solves optional PoW, sends the signed payload, and shows the backend-issued `user_id` plus binding status.

## Highlights

- Pure-JS P-256 device key generation via `@noble/curves`.
- IndexedDB device store backed by `@tanstack/db` + `idb-keyval`.
- Single-screen Flow in `src/pages/public-key-login-page.tsx`.
- Manual device binding plus `Generate access token` button driven by the stored user ID.
- Tailwind + DaisyUI styling without additional routing or layout cruft.

## Keycloak requirements

- Register the `device-public-key-login` provider in your realm.
- Configure `KEYCLOAK_BASE_URL`, `REALM`, and optionally `CLIENT_ID` in `src/config.ts`.
- Mirror any PoW difficulty set via `PUBLIC_KEY_LOGIN_POW_DIFFICULTY_{realm}` by exporting the same number in `VITE_PUBLIC_LOGIN_POW_DIFFICULTY`.

## Run

```bash
cd examples/web-vite-react-public-key-login
npm install
npm run dev
```

The app runs on `http://localhost:5173` by default and immediately displays the public-key login experience.
