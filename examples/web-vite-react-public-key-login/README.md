# Web Vite React Example

Public-key login sample with flat UI, local Tailwind tooling, and TanStack DB-backed device storage.

## Stack

- Vite
- React + TypeScript
- React Router
- Tailwind CSS v4 + DaisyUI v5 (installed locally)
- TanStack DB + IndexedDB persistence through `idb-keyval`
- Pure-JS device crypto with `@noble/curves` (no WebCrypto dependency)
- Frontend ID generation with `@paralleldrive/cuid2` (`dvc_*`, `nce_*`, `stt_*`)

## Routes

- `/` landing hero + device dashboard
- `/login` popup login and code exchange
- `/callback` auth callback payload relay (debug fallback)
- `/session` backend user-id binding
- `/resource` protected resource call + approvals view proxied by resource server
- `/resource` also includes a simple `start live` button that subscribes to resource-server WebSocket approvals stream (`/ws/approvals`)
- `/public-login` manual public-key login call (new realm resource)

## File Layout

- `src/app/` app shell + routing
- `src/pages/` route pages (kebab-case filenames)
- `src/hooks/` app hooks
- `src/lib/` auth, crypto, and persistence logic
- `src/styles/` global Tailwind/DaisyUI stylesheet

## Keycloak Config

- Realm: `e2e-testing`
- Client ID: `web-vite`
- Redirect URI: `http://localhost:5173/popup-callback.html` (recommended for instant popup close)
- Optional debug Redirect URI: `http://localhost:5173/callback`
- Web origin: `http://localhost:5173`

## Public-Key Login Demo

- Navigate to `/public-login` to call `POST /realms/{realm}/device-public-key-login`.
- Enter the username you want to resolve/create (e.g., `alice`) and optionally a `client_id`.
- The page signs the payload with the locally stored device key and displays the endpoint response.

## Environment Overrides

- `VITE_KEYCLOAK_BASE_URL`, `VITE_REALM`, `VITE_CLIENT_ID`, `VITE_REDIRECT_URI`
- `VITE_RESOURCE_SERVER`
- `VITE_RESOURCE_SERVER_SIGNED` (defaults to `http://localhost:18082`)

## Run

```bash
npm install
npm run dev
```

## Related Docs

- `docs/USAGE.md`
- `docs/PLAN.md`
