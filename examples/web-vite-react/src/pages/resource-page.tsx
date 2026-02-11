import {useCallback, useEffect, useRef, useState} from 'react';
import {RESOURCE_SERVER} from '../config';
import {ensureAccessToken} from '../lib/auth';
import {JsonDisplay} from "../components/json-display";

export const ResourcePage = () => {
    const [resourceOutput, setResourceOutput] = useState<Record<string, unknown>>({msg: 'No request yet'});
    const [approvalsOutput, setApprovalsOutput] = useState<Record<string, unknown>>({msg: 'No request yet'});
    const [loadingResource, setLoadingResource] = useState(false);
    const [loadingApprovals, setLoadingApprovals] = useState(false);
    const [approvalsLiveState, setApprovalsLiveState] = useState<'idle' | 'connecting' | 'connected' | 'error'>('idle');
    const approvalsSocketRef = useRef<WebSocket | null>(null);

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

    const stopLiveApprovals = useCallback(() => {
        if (approvalsSocketRef.current) {
            approvalsSocketRef.current.close();
            approvalsSocketRef.current = null;
        }
        setApprovalsLiveState('idle');
    }, []);

    const startLiveApprovals = useCallback(async () => {
        if (approvalsSocketRef.current && approvalsSocketRef.current.readyState <= WebSocket.OPEN) {
            return;
        }

        setApprovalsLiveState('connecting');
        try {
            const token = await ensureAccessToken();
            if (!token) {
                throw new Error('No access token available');
            }

            const websocketBase = RESOURCE_SERVER.replace(/^http/i, 'ws');
            const socket = new WebSocket(`${websocketBase}/ws/approvals?access_token=${encodeURIComponent(token)}`);
            approvalsSocketRef.current = socket;

            socket.onopen = () => {
                setApprovalsLiveState('connected');
            };

            socket.onmessage = (event) => {
                try {
                    setApprovalsOutput(JSON.parse(event.data as string) as Record<string, unknown>);
                } catch {
                    setApprovalsOutput({
                        msg: 'Live approvals message parsing failed',
                        raw: String(event.data ?? ''),
                    });
                }
            };

            socket.onerror = () => {
                setApprovalsLiveState('error');
            };

            socket.onclose = () => {
                if (approvalsSocketRef.current === socket) {
                    approvalsSocketRef.current = null;
                    setApprovalsLiveState((current) => current === 'error' ? 'error' : 'idle');
                }
            };
        } catch (error) {
            setApprovalsLiveState('error');
            setApprovalsOutput({
                msg: `Live approvals subscription failed: ${(error as Error).message}`,
                error,
            });
        }
    }, []);

    useEffect(() => {
        void fetchResource();
        void fetchApprovals();
    }, [fetchApprovals, fetchResource]);

    useEffect(() => () => stopLiveApprovals(), [stopLiveApprovals]);

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
                <div className="mt-4 overflow-auto p-2 text-xs">
                    <JsonDisplay src={approvalsOutput} collapsed={false}/>
                </div>
            </article>
        </section>
    );
};
