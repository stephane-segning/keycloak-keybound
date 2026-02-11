import {useCallback, useEffect, useState} from 'react';
import {RESOURCE_SERVER} from '../config';
import {ensureAccessToken} from '../lib/auth';
import {JsonDisplay} from "../components/json-display";

export const ResourcePage = () => {
    const [resourceOutput, setResourceOutput] = useState<Record<string, unknown>>({msg: 'No request yet'});
    const [approvalsOutput, setApprovalsOutput] = useState<Record<string, unknown>>({msg: 'No request yet'});
    const [loadingResource, setLoadingResource] = useState(false);
    const [loadingApprovals, setLoadingApprovals] = useState(false);

    const fetchResource = useCallback(async () => {
        setLoadingResource(true);
        try {
            // Ensures missing/expired access token is renewed with custom grant before request.
            const token = await ensureAccessToken();
            const response = await fetch(`${RESOURCE_SERVER}/get`, {
                headers: token ? {Authorization: `Bearer ${token}`} : {},
            });
            const data = await response.json();
            setResourceOutput(data);
        } catch (error) {
            setResourceOutput({
                msg: `Request failed: ${(error as Error).message}`,
                error,
            });
        } finally {
            setLoadingResource(false);
        }
    }, []);

    const fetchApprovals = useCallback(async () => {
        setLoadingApprovals(true);
        try {
            const token = await ensureAccessToken();
            const response = await fetch(`${RESOURCE_SERVER}/approvals`, {
                headers: token ? {Authorization: `Bearer ${token}`} : {},
            });
            const data = await response.json();
            setApprovalsOutput(data);
        } catch (error) {
            setApprovalsOutput({
                msg: `Approvals request failed: ${(error as Error).message}`,
                error,
            });
        } finally {
            setLoadingApprovals(false);
        }
    }, []);

    useEffect(() => {
        void fetchResource();
        void fetchApprovals();
    }, [fetchApprovals, fetchResource]);

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
                        disabled={loadingResource}
                    >
                        {loadingResource ? 'loading' : 'refresh'}
                    </button>
                </div>

                <div className="mt-4 overflow-auto p-2 text-xs">
                    <JsonDisplay src={resourceOutput} collapsed={false}/>
                </div>
            </article>

            <article className="border border-base-300 bg-base-100 p-5">
                <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                    <div>
                        <h2 className="text-base font-semibold">user approvals</h2>
                        <p className="text-sm text-base-content/80">Fetches {RESOURCE_SERVER}/approvals. The resource
                            server resolves your backend user id from JWT and requests approvals from backend.</p>
                    </div>
                    <button
                        className="btn btn-sm rounded-none border border-base-content bg-base-content text-base-100"
                        onClick={() => void fetchApprovals()}
                        disabled={loadingApprovals}
                    >
                        {loadingApprovals ? 'loading' : 'refresh'}
                    </button>
                </div>

                <div className="mt-4 overflow-auto p-2 text-xs">
                    <JsonDisplay src={approvalsOutput} collapsed={false}/>
                </div>
            </article>
        </section>
    );
};
