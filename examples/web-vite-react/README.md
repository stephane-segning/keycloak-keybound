# Web Vite React Example

This example implements the public-key login flow in a browser app.

Stack:
- Vite
- React + TypeScript
- React Router
- IndexedDB via `idb-keyval`

Routes:
- `/` (status + device info)
- `/login` (start public-key redirect)
- `/callback` (reads the authorization code)
- `/session` (persist backend user ID, inspect stored key material)
- `/resource` (calls the resource server)

Keycloak config:
- Realm: `e2e-realm`
- Client ID: `web-vite`
- Redirect URI: `http://localhost:5173/callback`
- Web origin: `http://localhost:5173`

Environment overrides (prefixed `VITE_`):
- `VITE_KEYCLOAK_BASE_URL`, `VITE_REALM`, `VITE_CLIENT_ID`, `VITE_REDIRECT_URI`
- `VITE_RESOURCE_SERVER`

Paths such as `/login` compute PKCE + canonical signature parameters, store the code verifier in `sessionStorage`, and open the Keycloak authorization URL.
