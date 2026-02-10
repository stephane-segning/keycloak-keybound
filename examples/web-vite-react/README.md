# Web Vite React Example

Public-key login sample with flat UI, local Tailwind tooling, and TanStack DB-backed device storage.

Stack:
- Vite
- React + TypeScript
- React Router
- Tailwind CSS v4 + DaisyUI v5 (installed locally)
- TanStack DB + IndexedDB persistence through `idb-keyval`

Routes:
- `/` landing hero + device dashboard
- `/login` popup login and code exchange
- `/callback` auth callback payload relay
- `/session` backend user-id binding
- `/resource` protected resource call

File layout:
- `src/app/` app shell + routing
- `src/pages/` route pages (kebab-case filenames)
- `src/hooks/` app hooks
- `src/lib/` auth, crypto, and persistence logic
- `src/styles/` global Tailwind/DaisyUI stylesheet

Keycloak config:
- Realm: `e2e-realm`
- Client ID: `web-vite`
- Redirect URI: `http://localhost:5173/callback`
- Web origin: `http://localhost:5173`

Environment overrides (prefixed `VITE_`):
- `VITE_KEYCLOAK_BASE_URL`, `VITE_REALM`, `VITE_CLIENT_ID`, `VITE_REDIRECT_URI`
- `VITE_RESOURCE_SERVER`
