export const KEYCLOAK_REALM = import.meta.env.VITE_REALM ?? 'e2e-testing';
export const KEYCLOAK_BASE_URL = import.meta.env.VITE_KEYCLOAK_BASE_URL ?? 'http://localhost:9026';
export const CLIENT_ID = import.meta.env.VITE_CLIENT_ID ?? 'web-vite';
export const REDIRECT_URI = import.meta.env.VITE_REDIRECT_URI ?? 'http://localhost:5173/callback';
export const RESOURCE_SERVER = import.meta.env.VITE_RESOURCE_SERVER ?? 'http://localhost:18081';
