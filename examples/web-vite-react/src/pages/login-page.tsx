import {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {useDeviceStorage} from '../hooks/use-device-storage';
import {
    closePopup,
    registerWindowMessageListener
} from '../lib/browser-runtime';
import {
    buildAuthorizationUrl,
    completeAuthByCode,
    openLoginPopup,
    readAuthCallbackPayload,
} from "../lib/login-flow";
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
                const {tokens, userInfo, userId} = await completeAuthByCode(code);
                if (userId) {
                    await ensureDevice();
                    await setUserId(userId);
                }

                closePopup(popupRef.current);

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
            const payload = readAuthCallbackPayload(event);
            if (!payload) return;

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

        return registerWindowMessageListener(handler);
    }, [completeCodeFlow]);

    useEffect(
        () => () => {
            closePopup(popupRef.current);
        },
        []
    );

    const openPopup = useCallback((url: string) => {
        const popup = openLoginPopup(url);
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
        const url = await buildAuthorizationUrl(current);
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
