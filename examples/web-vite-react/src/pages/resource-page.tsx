import {useCallback, useEffect, useRef, useState} from 'react';
import {RESOURCE_SERVER, RESOURCE_SERVER_SIGNED} from '../config';
import {ensureAccessToken} from '../lib/auth';
import {JsonDisplay} from "../components/json-display";
import {
    ApprovalStreamClient,
    ApprovalStreamState,
    approveRequest as approveRequestById,
    fetchApprovals as fetchApprovalsFromResource,
    fetchProtectedResource,
    parseApprovalRecords
} from "../lib/resource-approvals";

export const ResourcePage = () => {
    const [resourceOutput, setResourceOutput] = useState<Record<string, unknown>>({msg: 'No request yet'});
    const [signedResourceOutput, setSignedResourceOutput] = useState<Record<string, unknown>>({msg: 'No request yet'});
    const [combinedResourceOutput, setCombinedResourceOutput] = useState<Record<string, unknown>>({msg: 'No request yet'});
    const [approvalsOutput, setApprovalsOutput] = useState<Record<string, unknown>>({msg: 'No request yet'});
    const [loadingResource, setLoadingResource] = useState(false);
    const [loadingSignedResource, setLoadingSignedResource] = useState(false);
    const [loadingBothResources, setLoadingBothResources] = useState(false);
    const [loadingApprovals, setLoadingApprovals] = useState(false);
    const [approvingRequestId, setApprovingRequestId] = useState<string | null>(null);
    const [approvalsLiveState, setApprovalsLiveState] = useState<ApprovalStreamState>('idle');
    const approvalsStreamClientRef = useRef<ApprovalStreamClient>(new ApprovalStreamClient());

    const fetchResource = useCallback(async () => {
        setLoadingResource(true);
        try {
            // Standard resource server call (bearer token only).
            const data = await fetchProtectedResource(RESOURCE_SERVER);
            setResourceOutput(data as Record<string, unknown>);
        } catch (error) {
            setResourceOutput({
                msg: `Request failed: ${(error as Error).message}`,
                error,
            });
        } finally {
            setLoadingResource(false);
        }
    }, []);

    const fetchSignedResource = useCallback(async () => {
        setLoadingSignedResource(true);
        try {
            // Signed resource server call (bearer token + PoP headers via axios interceptor).
            const data = await fetchProtectedResource(RESOURCE_SERVER_SIGNED);
            setSignedResourceOutput(data as Record<string, unknown>);
        } catch (error) {
            setSignedResourceOutput({
                msg: `Signed request failed: ${(error as Error).message}`,
                error,
            });
        } finally {
            setLoadingSignedResource(false);
        }
    }, []);

    const fetchBothResources = useCallback(async () => {
        setLoadingBothResources(true);
        try {
            const [resource, signedResource] = await Promise.all([
                fetchProtectedResource(RESOURCE_SERVER),
                fetchProtectedResource(RESOURCE_SERVER_SIGNED),
            ]);
            setResourceOutput(resource as Record<string, unknown>);
            setSignedResourceOutput(signedResource as Record<string, unknown>);
            setCombinedResourceOutput({
                queried_at: new Date().toISOString(),
                resource_server: {
                    url: RESOURCE_SERVER,
                    response: resource,
                },
                resource_server_signed: {
                    url: RESOURCE_SERVER_SIGNED,
                    response: signedResource,
                },
            });
        } catch (error) {
            setCombinedResourceOutput({
                msg: `Combined query failed: ${(error as Error).message}`,
                error,
            });
        } finally {
            setLoadingBothResources(false);
        }
    }, []);

    const fetchApprovals = useCallback(async () => {
        setLoadingApprovals(true);
        try {
            const data = await fetchApprovalsFromResource(RESOURCE_SERVER);
            setApprovalsOutput(data as Record<string, unknown>);
        } catch (error) {
            setApprovalsOutput({
                msg: `Approvals request failed: ${(error as Error).message}`,
                error,
            });
        } finally {
            setLoadingApprovals(false);
        }
    }, []);

    const stopLiveApprovals = useCallback(() => {
        approvalsStreamClientRef.current.disconnect();
        setApprovalsLiveState('idle');
    }, []);

    const startLiveApprovals = useCallback(async () => {
        setApprovalsLiveState('connecting');
        try {
            const token = await ensureAccessToken();
            if (!token) {
                throw new Error('No access token available');
            }
            approvalsStreamClientRef.current.connect(RESOURCE_SERVER, token, {
                onOpen: () => setApprovalsLiveState('connected'),
                onMessage: (payload) => setApprovalsOutput(payload as Record<string, unknown>),
                onError: () => {
                    setApprovalsLiveState('error');
                    setApprovalsOutput({
                        msg: 'Live approvals message parsing failed',
                    });
                },
                onClose: () => setApprovalsLiveState((current) => current === 'error' ? 'error' : 'idle'),
            });
        } catch (error) {
            setApprovalsLiveState('error');
            setApprovalsOutput({
                msg: `Live approvals subscription failed: ${(error as Error).message}`,
                error,
            });
        }
    }, []);

    const approveRequest = useCallback(async (requestId: string) => {
        setApprovingRequestId(requestId);
        try {
            await approveRequestById(RESOURCE_SERVER, requestId);
            await fetchApprovals();
        } catch (error) {
            setApprovalsOutput((previous) => ({
                ...previous,
                approve_error: `Approval update failed: ${(error as Error).message}`,
                failed_request_id: requestId,
            }));
        } finally {
            setApprovingRequestId(null);
        }
    }, [fetchApprovals]);

    useEffect(() => {
        void fetchBothResources();
        void fetchApprovals();
    }, [fetchApprovals, fetchBothResources]);

    useEffect(() => () => stopLiveApprovals(), [stopLiveApprovals]);

    const approvalRecords = parseApprovalRecords(approvalsOutput);

    return (
        <section className="space-y-4">
            <article className="border border-base-300 bg-base-100 p-5">
                <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                    <div>
                        <h2 className="text-base font-semibold">both resource servers</h2>
                        <p className="text-sm text-base-content/80">Runs both queries in one action: {RESOURCE_SERVER}/get and {RESOURCE_SERVER_SIGNED}/get.</p>
                    </div>
                    <button
                        className="btn btn-sm rounded-none border border-base-content bg-base-content text-base-100"
                        onClick={() => void fetchBothResources()}
                        disabled={loadingBothResources}
                    >
                        {loadingBothResources ? 'loading' : 'query both'}
                    </button>
                </div>

                <div className="mt-4 overflow-auto p-2 text-xs">
                    <JsonDisplay src={combinedResourceOutput} collapsed={false}/>
                </div>
            </article>

            <article className="border border-base-300 bg-base-100 p-5">
                <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                    <div>
                        <h2 className="text-base font-semibold">protected resource</h2>
                        <p className="text-sm text-base-content/80">Fetches {RESOURCE_SERVER}/get (classic resource
                            server, bearer token).</p>
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
                        <h2 className="text-base font-semibold">protected resource (signed)</h2>
                        <p className="text-sm text-base-content/80">Fetches {RESOURCE_SERVER_SIGNED}/get with axios
                            middleware that adds bearer token plus PoP headers (`x-public-key`, `x-signature`,
                            `x-signature-timestamp`).</p>
                    </div>
                    <button
                        className="btn btn-sm rounded-none border border-base-content bg-base-content text-base-100"
                        onClick={() => void fetchSignedResource()}
                        disabled={loadingSignedResource}
                    >
                        {loadingSignedResource ? 'loading' : 'refresh'}
                    </button>
                </div>

                <div className="mt-4 overflow-auto p-2 text-xs">
                    <JsonDisplay src={signedResourceOutput} collapsed={false}/>
                </div>
            </article>

            <article className="border border-base-300 bg-base-100 p-5">
                <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                    <div>
                        <h2 className="text-base font-semibold">user approvals</h2>
                        <p className="text-sm text-base-content/80">Fetches {RESOURCE_SERVER}/approvals. The resource
                            server resolves your backend user id from JWT and requests approvals from backend. Start
                            live mode to subscribe through {RESOURCE_SERVER.replace(/^http/i, 'ws')}/ws/approvals.</p>
                    </div>
                    <div className="flex flex-wrap items-center gap-2">
                        <button
                            className="btn btn-sm rounded-none border border-base-content bg-base-content text-base-100"
                            onClick={() => void (approvalsLiveState === 'connected' || approvalsLiveState === 'connecting'
                                ? stopLiveApprovals()
                                : startLiveApprovals())}
                            disabled={approvalsLiveState === 'connecting'}
                        >
                            {approvalsLiveState === 'connected' || approvalsLiveState === 'connecting'
                                ? 'stop live'
                                : 'start live'}
                        </button>
                        <button
                            className="btn btn-sm rounded-none border border-base-content bg-base-content text-base-100"
                            onClick={() => void fetchApprovals()}
                            disabled={loadingApprovals}
                        >
                            {loadingApprovals ? 'loading' : 'refresh'}
                        </button>
                    </div>
                </div>

                <p className="mt-3 text-xs uppercase tracking-wide text-base-content/70">stream status: {approvalsLiveState}</p>
                <div className="mt-4 overflow-x-auto">
                    <table className="table table-xs">
                        <thead>
                        <tr>
                            <th>request</th>
                            <th>device</th>
                            <th>status</th>
                            <th>action</th>
                        </tr>
                        </thead>
                        <tbody>
                        {approvalRecords.length === 0 ? (
                            <tr>
                                <td colSpan={4} className="text-base-content/70">No approvals found</td>
                            </tr>
                        ) : approvalRecords.map((approval) => {
                            const canApprove = approval.status === 'PENDING';
                            const isApproving = approvingRequestId === approval.request_id;
                            return (
                                <tr key={approval.request_id}>
                                    <td className="font-mono">{approval.request_id}</td>
                                    <td className="font-mono">{approval.device_id ?? '-'}</td>
                                    <td>{approval.status ?? '-'}</td>
                                    <td>
                                        <button
                                            className="btn btn-xs rounded-none border border-base-content bg-base-content text-base-100"
                                            onClick={() => void approveRequest(approval.request_id)}
                                            disabled={!canApprove || !!approvingRequestId}
                                        >
                                            {isApproving ? 'approving' : 'approve'}
                                        </button>
                                    </td>
                                </tr>
                            );
                        })}
                        </tbody>
                    </table>
                </div>
                <div className="mt-4 overflow-auto p-2 text-xs">
                    <JsonDisplay src={approvalsOutput} collapsed={false}/>
                </div>
            </article>
        </section>
    );
};
