import {FormEvent, useCallback, useMemo, useState} from 'react';
import {useDeviceStorage} from '../hooks/use-device-storage';
import {
    PublicKeyLoginResponse,
    callPublicKeyLoginEndpoint,
} from '../lib/public-key-login';

const STATUS_IDLE = 'idle';

export const PublicKeyLoginPage = () => {
    const {device, ensureDevice} = useDeviceStorage();
    const [status, setStatus] = useState(STATUS_IDLE);
    const [result, setResult] = useState<PublicKeyLoginResponse | null>(null);
    const [username, setUsername] = useState('alice');
    const [clientId, setClientId] = useState('');
    const [error, setError] = useState<string | null>(null);

    const handleSubmit = useCallback(
        async (event: FormEvent<HTMLFormElement>) => {
            event.preventDefault();
            await ensureDevice();
            setStatus('pending');
            setError(null);
            setResult(null);

            try {
                const response = await callPublicKeyLoginEndpoint({
                    username,
                    clientId: clientId || undefined,
                });
                setResult(response);
                setStatus('success');
            } catch (err) {
                setError((err as Error).message ?? 'Request failed');
                setStatus('error');
            }
        },
        [clientId, ensureDevice, username]
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
            </div>

            <form className="grid gap-4" onSubmit={handleSubmit}>
                <label className="flex flex-col gap-1 text-sm font-medium text-base-content">
                    Username (Backend keybound user)
                    <input
                        value={username}
                        onChange={(event) => setUsername(event.target.value)}
                        className="input input-bordered w-full"
                        placeholder="user@example.com"
                        required
                    />
                </label>

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
                    {status === 'pending' ? 'Calling endpoint…' : 'Call public-key login'}
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
        </section>
    );
};
