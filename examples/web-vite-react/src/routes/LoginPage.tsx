import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useDeviceStorage } from '../hooks/useDeviceStorage';
import { KEYCLOAK_BASE_URL, KEYCLOAK_REALM, CLIENT_ID, REDIRECT_URI } from '../config';
import { createCodeChallenge, createCodeVerifier } from '../lib/pkce';
import { signPayload, stringifyPublicJwk } from '../lib/crypto';
import { exchangeAuthorizationCode, extractUserId, fetchUserInfo, saveTokens } from '../lib/auth';

export function LoginPage() {
  const { device, ensureDevice, setUserId } = useDeviceStorage();
  const [authUrl, setAuthUrl] = useState<string>('');
  const [status, setStatus] = useState<string>('idle');
  const [result, setResult] = useState<Record<string, unknown> | null>(null);
  const completionLock = useRef(false);
  const popupRef = useRef<Window | null>(null);

  const loginUrl = useMemo(() => authUrl, [authUrl]);

  const completeCodeFlow = useCallback(
    async (code: string) => {
      if (completionLock.current) return;
      completionLock.current = true;
      setStatus('exchanging');

      try {
        const tokens = await exchangeAuthorizationCode(code);
        const userInfo = await fetchUserInfo(tokens.access_token);
        saveTokens(tokens);

        const userId = extractUserId(tokens, userInfo);
        if (userId) {
          await ensureDevice();
          await setUserId(userId);
        }

        if (popupRef.current && !popupRef.current.closed) {
          popupRef.current.close();
        }

        setResult({
          userId,
          userInfo,
          tokenType: tokens.token_type,
          scope: tokens.scope,
        });
        setStatus('complete');
      } catch (error) {
        setStatus(`error: ${(error as Error).message}`);
      } finally {
        completionLock.current = false;
      }
    },
    [ensureDevice, setUserId]
  );

  useEffect(() => {
    const handler = (event: MessageEvent) => {
      if (event.origin !== window.location.origin) return;
      const payload = event.data as { type?: string; code?: string; error?: string; error_description?: string };
      if (payload?.type !== 'keybound-auth-callback') return;

      if (payload.error) {
        setStatus(`error: ${payload.error_description ?? payload.error}`);
        return;
      }
      if (!payload.code) {
        setStatus('error: missing code in callback');
        return;
      }
      void completeCodeFlow(payload.code);
    };

    window.addEventListener('message', handler);
    return () => window.removeEventListener('message', handler);
  }, [completeCodeFlow]);

  useEffect(() => {
    return () => {
      if (popupRef.current && !popupRef.current.closed) {
        popupRef.current.close();
      }
    };
  }, []);

  const openPopup = useCallback(
    (url: string) => {
      const width = 480;
      const height = 760;
      const left = Math.max(0, Math.floor((window.screen.width - width) / 2));
      const top = Math.max(0, Math.floor((window.screen.height - height) / 2));
      const features = `popup=yes,width=${width},height=${height},left=${left},top=${top},resizable=yes,scrollbars=yes`;
      const popup = window.open(url, 'keybound-login', features);
      if (!popup) {
        setStatus('error: popup blocked by browser');
        return;
      }
      popupRef.current = popup;
      setStatus('awaiting-login');
    },
    []
  );

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
    setResult(null);
    openPopup(url);
  };

  return (
    <section className="space-y-4">
      <div className="card bg-base-100 shadow-md">
        <div className="card-body">
          <h2 className="card-title">Public-Key Login</h2>
          <p className="text-sm opacity-70">Starts the browser flow in a popup and auto-completes the code exchange.</p>
          <div className="card-actions">
            <button className="btn btn-primary" onClick={handleStart}>
              Start browser login
            </button>
            {loginUrl && status !== 'awaiting-login' && (
              <button className="btn btn-outline" onClick={() => openPopup(loginUrl)}>
                Re-open popup
              </button>
            )}
            {loginUrl && (
              <a className="btn btn-outline" href={loginUrl} target="_blank" rel="noreferrer">
                Open in new tab
              </a>
            )}
          </div>
          <div className="mt-2">
            <span className="badge badge-info">Status: {status}</span>
          </div>
        </div>
      </div>

      {result && (
        <div className="card bg-base-100 shadow-md">
          <div className="card-body">
            <h3 className="font-semibold">Code flow completed</h3>
            <pre className="bg-base-200 rounded-lg p-3 overflow-auto text-xs">{JSON.stringify(result, null, 2)}</pre>
          </div>
        </div>
      )}

      {device && (
        <div className="text-xs opacity-80">
          Stored device: <span className="font-mono">{device.deviceId}</span> {device.userId ? `| userId: ${device.userId}` : ''}
        </div>
      )}
    </section>
  );
}
