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
- `/callback` auth callback payload relay
- `/session` backend user-id binding
- `/resource` protected resource call + approvals view proxied by resource server

## File Layout

- `src/app/` app shell + routing
- `src/pages/` route pages (kebab-case filenames)
- `src/hooks/` app hooks
- `src/lib/` auth, crypto, and persistence logic
- `src/styles/` global Tailwind/DaisyUI stylesheet

## Keycloak Config

- Realm: `e2e-testing`
- Client ID: `web-vite`
- Redirect URI: `http://localhost:5173/callback`
- Web origin: `http://localhost:5173`

## Environment Overrides

- `VITE_KEYCLOAK_BASE_URL`, `VITE_REALM`, `VITE_CLIENT_ID`, `VITE_REDIRECT_URI`
- `VITE_RESOURCE_SERVER`

## Run

```bash
npm install
npm run dev
```

## Related Docs

- `docs/USAGE.md`
- `docs/PLAN.md`
