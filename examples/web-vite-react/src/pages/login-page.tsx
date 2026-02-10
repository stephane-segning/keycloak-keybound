import {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {CLIENT_ID, KEYCLOAK_BASE_URL, KEYCLOAK_REALM, REDIRECT_URI} from '../config';
import {useDeviceStorage} from '../hooks/use-device-storage';
import {exchangeAuthorizationCode, extractUserId, fetchUserInfo, saveGrantUserId, saveTokens,} from '../lib/auth';
import {signPayload, stringifyPublicJwk} from '../lib/crypto';
import {createCodeChallenge, createCodeVerifier} from '../lib/pkce';
import {JsonDisplay} from "../components/json-display";

export const LoginPage = () => {
    const {device, ensureDevice, setUserId} = useDeviceStorage();
    const [authUrl, setAuthUrl] = useState('');
    const [status, setStatus] = useState('idle');
    const [result, setResult] = useState<Record<string, unknown> | null>(null);
    const completionLock = useRef(false);
    const popupRef = useRef<Window | null>(null);

    const loginUrl = useMemo(() => authUrl, [authUrl]);

    const completeCodeFlow = useCallback(
        async (code: string) => {
            // Guard against duplicate callback events from popup/tab races.
            if (completionLock.current) return;
            completionLock.current = true;
            setStatus('exchanging');

            try {
                // Complete OIDC code flow, then cache tokens/user identity for renewal.
                const tokens = await exchangeAuthorizationCode(code);
                const userInfo = await fetchUserInfo(tokens.access_token);
                saveTokens(tokens);

                const userId = extractUserId(tokens, userInfo);
                saveGrantUserId(userId);
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
        // Receives callback payload from /callback popup and finishes code exchange in this window.
        const handler = (event: MessageEvent) => {
            if (event.origin !== window.location.origin) return;
            const payload = event.data as {
                type?: string;
                code?: string;
                error?: string;
                error_description?: string;
            };
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

    useEffect(
        () => () => {
            if (popupRef.current && !popupRef.current.closed) {
                popupRef.current.close();
            }
        },
        []
    );

    const openPopup = useCallback((url: string) => {
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
    }, []);

    const handleStart = async () => {
        setStatus('preparing');
        // Ensure a persisted device identity exists before starting auth.
        const current = await ensureDevice();
        const ts = Math.floor(Date.now() / 1000).toString();
        const nonce = crypto.randomUUID();
        // Canonical payload signed by the device key and verified server-side.
        const canonical = JSON.stringify({
            deviceId: current.deviceId,
            publicKey: stringifyPublicJwk(current.publicJwk),
            ts,
            nonce,
        });
        const signature = await signPayload(current.privateJwk, canonical);
        // PKCE verifier/challenge pair for authorization code flow.
        const codeVerifier = createCodeVerifier();
        const codeChallenge = await createCodeChallenge(codeVerifier);
        sessionStorage.setItem('code_verifier', codeVerifier);
        sessionStorage.setItem('last_login_nonce', nonce);
        sessionStorage.setItem('last_login_ts', ts);

        // Custom auth params (device key, signature, metadata) are attached to /auth request.
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
            <article className="border border-base-300 bg-base-100 p-5">
                <h2 className="text-base font-semibold">public key login</h2>
                <p className="mt-2 text-sm text-base-content/80">
                    Start the authorization popup with PKCE and a signed device payload.
                </p>

                <div className="mt-4 flex flex-wrap gap-2">
                    <button className="btn rounded-none border border-base-content bg-base-content text-base-100"
                            onClick={handleStart}>
                        start login
                    </button>
                    {loginUrl && status !== 'awaiting-login' && (
                        <button className="btn rounded-none border border-base-300 bg-base-100"
                                onClick={() => openPopup(loginUrl)}>
                            reopen popup
                        </button>
                    )}
                    {loginUrl && (
                        <a className="btn rounded-none border border-base-300 bg-base-100" href={loginUrl}
                           target="_blank" rel="noreferrer">
                            open in new tab
                        </a>
                    )}
                </div>

                <div className="mt-4 flex flex-wrap gap-3 text-xs text-base-content/80">
                    <span className="border border-base-300 px-2 py-1">status: {status}</span>
                    {device && (
                        <span
                            className="font-mono">device: {device.deviceId} {device.userId ? `| user: ${device.userId}` : ''}</span>
                    )}
                </div>
            </article>

            {result && (
                <JsonDisplay
                    src={result}
                />
            )}
        </section>
    );
};
