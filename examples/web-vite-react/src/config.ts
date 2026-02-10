export const KEYCLOAK_REALM = import.meta.env.VITE_REALM ?? 'ssegning-keybound-wh-01';
export const KEYCLOAK_BASE_URL = import.meta.env.VITE_KEYCLOAK_BASE_URL ?? 'http://localhost:9026';
export const CLIENT_ID = import.meta.env.VITE_CLIENT_ID ?? 'node-cli';
export const REDIRECT_URI = import.meta.env.VITE_REDIRECT_URI ?? 'http://localhost:3005/callback';
export const RESOURCE_SERVER = import.meta.env.VITE_RESOURCE_SERVER ?? 'http://localhost:18081';
