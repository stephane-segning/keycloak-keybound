import {FormEvent, useCallback, useMemo, useState} from 'react';
import {useDeviceStorage} from '../hooks/use-device-storage';
import {
    PublicKeyLoginResponse,
    callPublicKeyLoginEndpoint,
} from '../lib/public-key-login';
import {requestDeviceKeyAccessToken, TokenResponse} from '../lib/auth';
import {PUBLIC_LOGIN_POW_DIFFICULTY} from '../config';

const STATUS_IDLE = 'idle';

export const PublicKeyLoginPage = () => {
    const {device, ensureDevice, setUserId} = useDeviceStorage();
    const [status, setStatus] = useState(STATUS_IDLE);
    const [result, setResult] = useState<PublicKeyLoginResponse | null>(null);
    const [clientId, setClientId] = useState('');
    const [error, setError] = useState<string | null>(null);
    const [token, setToken] = useState<TokenResponse | null>(null);
    const [tokenError, setTokenError] = useState<string | null>(null);
    const [tokenPending, setTokenPending] = useState(false);
    const storedUserId = device?.userId;
    const handleGenerateToken = useCallback(async () => {
        if (!storedUserId) return;
        setTokenPending(true);
        setTokenError(null);
        setToken(null);
        try {
            const tokens = await requestDeviceKeyAccessToken(storedUserId);
            setToken(tokens);
        } catch (err) {
            setTokenError((err as Error).message ?? 'Token request failed');
        } finally {
            setTokenPending(false);
        }
    }, [storedUserId]);

    const handleSubmit = useCallback(
        async (event: FormEvent<HTMLFormElement>) => {
            event.preventDefault();
            await ensureDevice();
            setStatus('pending');
            setError(null);
            setResult(null);
            setToken(null);
            setTokenError(null);

            try {
                const response = await callPublicKeyLoginEndpoint({
                    clientId: clientId || undefined,
                });
                setResult(response);
                setUserId(response.user_id);
                setStatus('success');
            } catch (err) {
                setError((err as Error).message ?? 'Request failed');
                setStatus('error');
            }
        },
        [clientId, ensureDevice, setUserId]
    );

    const deviceInfo = useMemo(
        () => device?.deviceId ?? 'waiting for device storage...',
        [device]
    );

    return (
        <section className="space-y-6">
            <div className="rounded border border-base-300 bg-base-100 p-6 shadow">
                <p className="text-sm text-base-content/70">Device seed</p>
                <p className="text-lg font-semibold">{deviceInfo}</p>
                <p className="mt-2 text-xs text-base-content/70">
                    PoW difficulty: {PUBLIC_LOGIN_POW_DIFFICULTY} leading zero hex nibbles
                </p>
            </div>

            <form className="grid gap-4" onSubmit={handleSubmit}>
                <label className="flex flex-col gap-1 text-sm font-medium text-base-content">
                    Optional client_id (trace only)
                    <input
                        value={clientId}
                        onChange={(event) => setClientId(event.target.value)}
                        className="input input-bordered w-full"
                        placeholder="frontend"
                    />
                </label>

                <button
                    type="submit"
                    className="btn btn-primary"
                    disabled={status === 'pending'}
                >
                    {status === 'pending' ? 'Solving PoW + calling endpoint...' : 'Call public-key login'}
                </button>
            </form>

            <div className="rounded border border-base-300 bg-base-100 p-6 shadow">
                <p className="text-sm text-base-content/70">Endpoint result</p>
                {status === 'success' && result ? (
                    <div className="space-y-1 text-sm">
                        <p>User ID: <span className="font-semibold">{result.user_id}</span></p>
                        <p>New user: <span className="font-semibold">{String(result.created_user)}</span></p>
                        <p>Credential created: <span className="font-semibold">{String(result.credential_created)}</span></p>
                    </div>
                ) : status === 'error' ? (
                    <p className="text-sm text-error">{error}</p>
                ) : (
                    <p className="text-sm text-base-content/60">Idle</p>
                )}
            </div>

            <div className="rounded border border-base-300 bg-base-100 p-6 shadow">
                <p className="text-sm text-base-content/70">Stored backend subject</p>
                <p className="text-lg font-semibold">{storedUserId ?? 'Not linked yet'}</p>
                <button
                    type="button"
                    className="btn btn-outline btn-sm mt-4"
                    disabled={!storedUserId || tokenPending}
                    onClick={handleGenerateToken}
                >
                    {tokenPending ? 'Requesting token...' : 'Generate access token'}
                </button>
                {token && (
                    <p className="mt-3 text-sm">
                        Access token snippet: <span className="font-mono text-xs">{token.access_token.slice(0, 40)}…</span>
                    </p>
                )}
                {tokenError && <p className="mt-2 text-sm text-error">{tokenError}</p>}
            </div>
        </section>
    );
};
