import { useMemo, useState } from 'react';
import { useDeviceStorage } from '../hooks/useDeviceStorage';
import { KEYCLOAK_BASE_URL, KEYCLOAK_REALM, CLIENT_ID, REDIRECT_URI } from '../config';
import { createCodeChallenge, createCodeVerifier } from '../lib/pkce';
import { signPayload, stringifyPublicJwk } from '../lib/crypto';

const makeParamString = (record: ReturnType<typeof stringifyPublicJwk>) => record;

export function LoginPage() {
  const { device, ensureDevice } = useDeviceStorage();
  const [authUrl, setAuthUrl] = useState<string>('');
  const [status, setStatus] = useState<string>('idle');

  const loginUrl = useMemo(() => authUrl, [authUrl]);

  const handleStart = async () => {
    setStatus('preparing');
    const current = await ensureDevice();
    const ts = Math.floor(Date.now() / 1000).toString();
    const nonce = crypto.randomUUID();
    const canonical = JSON.stringify({
      deviceId: current.deviceId,
      publicKey: stringifyPublicJwk(current.publicJwk),
      ts,
      nonce,
    });
    const signature = await signPayload(current.privateJwk, canonical);
    const codeVerifier = createCodeVerifier();
    const codeChallenge = await createCodeChallenge(codeVerifier);
    sessionStorage.setItem('code_verifier', codeVerifier);
    sessionStorage.setItem('last_login_nonce', nonce);
    sessionStorage.setItem('last_login_ts', ts);

    const params = new URLSearchParams({
      scope: 'openid profile email',
      response_type: 'code',
      client_id: CLIENT_ID,
      redirect_uri: REDIRECT_URI,
      code_challenge: codeChallenge,
      code_challenge_method: 'S256',
      state: crypto.randomUUID(),
      device_id: current.deviceId,
      public_key: stringifyPublicJwk(current.publicJwk),
      ts,
      nonce,
      sig: signature,
      action: 'login',
      aud: CLIENT_ID,
      device_os: 'web',
      device_model: 'vite-react',
      user_hint: current.userId ?? '',
    });

    const url = `${KEYCLOAK_BASE_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/auth?${params.toString()}`;
    setAuthUrl(url);
    setStatus('ready');
  };

  return (
    <section>
      <h2>Public-Key Login</h2>
      <button onClick={handleStart}>Generate redirect and open Keycloak</button>
      <p>Status: {status}</p>
      {loginUrl && (
        <p>
          <a href={loginUrl} target="_blank" rel="noreferrer">
            Open authorization URL
          </a>
        </p>
      )}
      {loginUrl && (
        <details>
          <summary>Copyable URL</summary>
          <pre>{loginUrl}</pre>
        </details>
      )}
    </section>
  );
}
