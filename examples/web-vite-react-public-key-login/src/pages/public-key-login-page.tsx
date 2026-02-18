import {FormEvent, useCallback, useEffect, useMemo, useState} from 'react';
import {useDeviceStorage} from '../hooks/use-device-storage';
import {
    PublicKeyLoginResponse,
    callPublicKeyLoginEndpoint,
} from '../lib/public-key-login';
import {requestDeviceKeyAccessToken, TokenResponse} from '../lib/auth';
import {PUBLIC_LOGIN_POW_DIFFICULTY, RESOURCE_SERVER} from '../config';
import {fetchBackendUserRecord, lookupBackendDevice} from '../lib/backend';
import {fetchProtectedResource} from '../lib/resource-approvals';
import {fetchResourceHealth} from '../lib/resource-client';

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
    const [backendLoading, setBackendLoading] = useState(false);
    const [backendUserResult, setBackendUserResult] = useState<Record<string, unknown> | null>(null);
    const [backendDeviceResult, setBackendDeviceResult] = useState<Record<string, unknown> | null>(null);
    const [backendError, setBackendError] = useState<string | null>(null);
    const [backendDeviceError, setBackendDeviceError] = useState<string | null>(null);
    const [resourceLoading, setResourceLoading] = useState(false);
    const [resourceGetResult, setResourceGetResult] = useState<Record<string, unknown> | null>(null);
    const [resourceHealth, setResourceHealth] = useState<Record<string, unknown> | null>(null);
    const [resourceError, setResourceError] = useState<string | null>(null);
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

    useEffect(() => {
        if (!storedUserId) {
            setBackendUserResult(null);
            setBackendDeviceResult(null);
            setBackendError(null);
            setBackendDeviceError(null);
            setBackendLoading(false);
            return;
        }

        let cancelled = false;
        setBackendLoading(true);
        setBackendError(null);
        setBackendDeviceError(null);

        const loadBackend = async () => {
            try {
                const user = await fetchBackendUserRecord(storedUserId, token?.access_token);
                if (!cancelled) {
                    setBackendUserResult(user);
                }
            } catch (err) {
                if (!cancelled) {
                    setBackendUserResult(null);
                    setBackendError((err as Error).message ?? 'Backend user call failed');
                }
            }

            if (!cancelled && device?.deviceId) {
                try {
                    const lookup = await lookupBackendDevice(device.deviceId, token?.access_token);
                    if (!cancelled) {
                        setBackendDeviceResult(lookup);
                    }
                } catch (err) {
                    if (!cancelled) {
                        setBackendDeviceResult(null);
                        setBackendDeviceError((err as Error).message ?? 'Backend device lookup failed');
                    }
                }
            } else if (!device?.deviceId) {
                setBackendDeviceResult(null);
            }
        };

        loadBackend().finally(() => {
            if (!cancelled) {
                setBackendLoading(false);
            }
        });

        return () => {
            cancelled = true;
        };
    }, [storedUserId, device?.deviceId, token?.access_token]);

    useEffect(() => {
        if (!token?.access_token) {
            setResourceGetResult(null);
            setResourceHealth(null);
            setResourceError(null);
            setResourceLoading(false);
            return;
        }

        let cancelled = false;
        setResourceLoading(true);
        setResourceError(null);

        const loadResource = async () => {
            try {
                const [getResult, healthResult] = await Promise.all([
                    fetchProtectedResource(RESOURCE_SERVER, token.access_token),
                    fetchResourceHealth(),
                ]);
                if (!cancelled) {
                    setResourceGetResult(getResult);
                    setResourceHealth(healthResult);
                }
            } catch (err) {
                if (!cancelled) {
                    setResourceGetResult(null);
                    setResourceHealth(null);
                    setResourceError((err as Error).message ?? 'Resource server call failed');
                }
            } finally {
                if (!cancelled) {
                    setResourceLoading(false);
                }
            }
        };

        void loadResource();
        return () => {
            cancelled = true;
        };
    }, [token?.access_token]);

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
            <div className="rounded border border-base-300 bg-base-100 p-6 shadow">
                <p className="text-sm text-base-content/70">Backend example API</p>
                {backendLoading ? (
                    <p className="text-sm text-base-content/70">Loading backend data…</p>
                ) : backendError ? (
                    <p className="text-sm text-error">{backendError}</p>
                ) : (
                    <div className="space-y-3 text-sm">
                        <div>
                            <p className="text-xs text-base-content/70">User record</p>
                            {backendUserResult ? (
                                <pre className="mt-1 max-h-40 overflow-auto rounded border border-base-200 bg-base-100 p-2 text-[11px] text-base-content">
                                    {JSON.stringify(backendUserResult, null, 2)}
                                </pre>
                            ) : (
                                <p className="text-xs text-base-content/60">Link a user via public-key login to fetch this data.</p>
                            )}
                        </div>
                        <div>
                            <p className="text-xs text-base-content/70">Device lookup</p>
                            {backendDeviceResult ? (
                                <pre className="mt-1 max-h-40 overflow-auto rounded border border-base-200 bg-base-100 p-2 text-[11px] text-base-content">
                                    {JSON.stringify(backendDeviceResult, null, 2)}
                                </pre>
                            ) : (
                                <p className="text-xs text-base-content/60">
                                    {device?.deviceId
                                        ? 'Waiting for the backend to return details for this device.'
                                        : 'A device is being created; complete a login to see this lookup.'}
                                </p>
                            )}
                            {backendDeviceError && <p className="mt-1 text-xs text-error">{backendDeviceError}</p>}
                        </div>
                    </div>
                )}
            </div>
            <div className="rounded border border-base-300 bg-base-100 p-6 shadow">
                <p className="text-sm text-base-content/70">Resource server API ({RESOURCE_SERVER})</p>
                {resourceLoading ? (
                    <p className="text-sm text-base-content/70">Calling resource server…</p>
                ) : resourceError ? (
                    <p className="text-sm text-error">{resourceError}</p>
                ) : token ? (
                    <div className="space-y-3 text-sm">
                        <div>
                            <p className="text-xs text-base-content/70">GET /get response</p>
                            {resourceGetResult ? (
                                <pre className="mt-1 max-h-40 overflow-auto rounded border border-base-200 bg-base-100 p-2 text-[11px] text-base-content">
                                    {JSON.stringify(resourceGetResult, null, 2)}
                                </pre>
                            ) : (
                                <p className="text-xs text-base-content/60">Awaiting resource response…</p>
                            )}
                        </div>
                        <div>
                            <p className="text-xs text-base-content/70">GET /health response</p>
                            {resourceHealth ? (
                                <pre className="mt-1 max-h-20 overflow-auto rounded border border-base-200 bg-base-100 p-2 text-[11px] text-base-content">
                                    {JSON.stringify(resourceHealth, null, 2)}
                                </pre>
                            ) : (
                                <p className="text-xs text-base-content/60">Health endpoint is public and called after a token is available.</p>
                            )}
                        </div>
                    </div>
                ) : (
                    <p className="text-sm text-base-content/60">Generate an access token to call the resource server.</p>
                )}
            </div>
        </section>
    );
};
