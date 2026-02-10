import JsonView from '@microlink/react-json-view';
import {useCallback, useEffect, useState} from 'react';
import {RESOURCE_SERVER} from '../config';
import {ensureAccessToken} from '../lib/auth';

export const ResourcePage = () => {
    const [output, setOutput] = useState<Record<string, unknown>>({msg: 'No request yet'});
    const [loading, setLoading] = useState(false);

    const fetchResource = useCallback(async () => {
        setLoading(true);
        try {
            const token = await ensureAccessToken();
            const response = await fetch(`${RESOURCE_SERVER}/get`, {
                headers: token ? {Authorization: `Bearer ${token}`} : {},
            });
            const data = await response.json();
            setOutput(data);
        } catch (error) {
            setOutput({
                msg: `Request failed: ${(error as Error).message}`,
                error,
            });
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        void fetchResource();
    }, [fetchResource]);

    return (
        <section className="space-y-4">
            <article className="border border-base-300 bg-base-100 p-5">
                <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                    <div>
                        <h2 className="text-base font-semibold">protected resource</h2>
                        <p className="text-sm text-base-content/80">Fetches {RESOURCE_SERVER}/get with an active bearer
                            token.</p>
                    </div>
                    <button
                        className="btn btn-sm rounded-none border border-base-content bg-base-content text-base-100"
                        onClick={() => void fetchResource()}
                        disabled={loading}
                    >
                        {loading ? 'loading' : 'refresh'}
                    </button>
                </div>

                <div className="mt-4 overflow-auto p-3 text-xs">
                    <JsonView src={output} theme="bright" collapsed={1} displayDataTypes={false}
                              style={{background: 'none'}} enableClipboard={false}/>
                </div>
            </article>
        </section>
    );
};
