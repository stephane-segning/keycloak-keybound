import {useCallback, useState} from 'react';
import {useDeviceStorage} from '../hooks/use-device-storage';
import {JsonDisplay} from '../components/json-display';
import {DeviceLoginResult, performDeviceLogin} from '../lib/login-flow';

const STATUS_IDLE = 'idle';
const STATUS_PREPARING = 'preparing';
const STATUS_AUTHENTICATING = 'authenticating';
const STATUS_COMPLETE = 'complete';

export const LoginPage = () => {
    const {device, ensureDevice, setUserId} = useDeviceStorage();
    const [status, setStatus] = useState(STATUS_IDLE);
    const [result, setResult] = useState<DeviceLoginResult | null>(null);

    const handleLogin = useCallback(async () => {
        setStatus(STATUS_PREPARING);
        await ensureDevice();
        setStatus(STATUS_AUTHENTICATING);
        setResult(null);

        try {
            const loginResult = await performDeviceLogin();
            await setUserId(loginResult.userId);
            setResult(loginResult);
            setStatus(STATUS_COMPLETE);
        } catch (error) {
            setStatus(`error: ${(error as Error).message}`);
        }
    }, [ensureDevice, setUserId]);

    const isBusy = status === STATUS_PREPARING || status === STATUS_AUTHENTICATING;

    return (
        <section className="space-y-4">
            <article className="border border-base-300 bg-base-100 p-5">
                <h2 className="text-base font-semibold">device-backed login</h2>
                <p className="mt-2 text-sm text-base-content/80">
                    Calls `/device-public-key-login` with the local device key, then requests an access token via the
                    `urn:ssegning:params:oauth:grant-type:device_key` grant.
                </p>

                <div className="mt-4 flex flex-wrap gap-2">
                    <button
                        className="btn rounded-none border border-base-content bg-base-content text-base-100"
                        onClick={handleLogin}
                        disabled={isBusy}
                    >
                        {isBusy ? 'logging in…' : 'start login'}
                    </button>
                </div>

                <div className="mt-4 flex flex-wrap gap-3 text-xs text-base-content/80">
                    <span className="border border-base-300 px-2 py-1">status: {status}</span>
                    {device && (
                        <span className="font-mono">
                            device: {device.deviceId}{device.userId ? ` | user: ${device.userId}` : ''}
                        </span>
                    )}
                </div>
            </article>

            {result && (
                <article className="border border-base-300 bg-base-100 p-5">
                    <h3 className="text-sm font-semibold uppercase tracking-[0.16em] text-base-content/70">last login</h3>
                    <div className="mt-4 overflow-auto text-xs">
                        <JsonDisplay
                            src={{
                                userId: result.userId,
                                tokens: result.tokens,
                                publicKeyLogin: result.publicKeyLogin,
                            }}
                            collapsed={false}
                        />
                    </div>
                </article>
            )}
        </section>
    );
};
