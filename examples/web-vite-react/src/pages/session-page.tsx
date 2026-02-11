import {useCallback, useEffect, useState} from 'react';
import {useDeviceStorage} from '../hooks/use-device-storage';
import {JsonDisplay} from "../components/json-display";

export const SessionPage = () => {
    const {device, setUserId} = useDeviceStorage();
    const [userIdInput, setUserIdInput] = useState(device?.userId ?? '');

    useEffect(() => {
        setUserIdInput(device?.userId ?? '');
    }, [device?.userId]);

    const handleSave = useCallback(async () => {
        if (!userIdInput) return;
        await setUserId(userIdInput);
    }, [setUserId, userIdInput]);

    return (
        <section className="space-y-4">
            <article className="border border-base-300 bg-base-100 p-5">
                <h2 className="text-base font-semibold">session metadata</h2>
                <p className="mt-2 text-sm text-base-content/80">Store the backend user identifier for custom grant
                    refresh.</p>

                <div className="mt-4 overflow-auto py-2 text-xs">
                    <JsonDisplay src={device ?? {ready: false}}/>
                </div>
            </article>

            <article className="border border-base-300 bg-base-100 p-5">
                <h3 className="text-sm font-semibold uppercase tracking-[0.16em] text-base-content/70">bind user id</h3>
                <label className="mt-3 flex flex-col gap-2 text-sm">
                    <span className="text-base-content/80">backend user id</span>
                    <input
                        className="input rounded-none border-base-300 bg-base-100"
                        value={userIdInput}
                        onChange={(event) => setUserIdInput(event.target.value)}
                        placeholder="backend-user-123"
                    />
                </label>
                <button
                    className="btn btn-sm mt-4 rounded-none border border-base-content bg-base-content text-base-100"
                    onClick={handleSave}
                    disabled={!userIdInput || device?.userId === userIdInput}
                >
                    save
                </button>
            </article>
        </section>
    );
};
