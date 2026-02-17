import {apiHttpClient} from "./http-client";

export type JsonObject = Record<string, unknown>;

export type ApprovalRecord = {
    request_id: string;
    user_id?: string;
    device_id?: string;
    status?: string;
    created_at?: string;
    decided_at?: string | null;
    decided_by_device_id?: string | null;
    message?: string | null;
};

export const fetchProtectedResource = async (resourceServer: string, token?: string): Promise<JsonObject> =>
    (
        await apiHttpClient.get<JsonObject>(
            `${resourceServer}/get`,
            token ? {headers: {Authorization: `Bearer ${token}`}} : undefined
        )
    ).data;

export const fetchApprovals = async (resourceServer: string, token?: string): Promise<JsonObject> =>
    (
        await apiHttpClient.get<JsonObject>(
            `${resourceServer}/approvals`,
            token ? {headers: {Authorization: `Bearer ${token}`}} : undefined
        )
    ).data;

export const approveRequest = async (resourceServer: string, requestId: string, token?: string): Promise<JsonObject> =>
    (
        await apiHttpClient.post<JsonObject>(
            `${resourceServer}/approvals/${encodeURIComponent(requestId)}/approve`,
            undefined,
            token ? {headers: {Authorization: `Bearer ${token}`}} : undefined
        )
    ).data;

export const parseApprovalRecords = (payload: JsonObject): ApprovalRecord[] => {
    const backend = payload.backend;
    if (!backend || typeof backend !== "object") {
        return [];
    }

    const approvals = (backend as { approvals?: unknown }).approvals;
    if (!Array.isArray(approvals)) {
        return [];
    }

    return approvals
        .filter((entry): entry is Record<string, unknown> => !!entry && typeof entry === "object")
        .map((entry) => ({
            request_id: String(entry.request_id ?? ""),
            user_id: typeof entry.user_id === "string" ? entry.user_id : undefined,
            device_id: typeof entry.device_id === "string" ? entry.device_id : undefined,
            status: typeof entry.status === "string" ? entry.status : undefined,
            created_at: typeof entry.created_at === "string" ? entry.created_at : undefined,
            decided_at: typeof entry.decided_at === "string" || entry.decided_at === null ? (entry.decided_at as string | null) : undefined,
            decided_by_device_id:
                typeof entry.decided_by_device_id === "string" || entry.decided_by_device_id === null
                    ? (entry.decided_by_device_id as string | null)
                    : undefined,
            message: typeof entry.message === "string" || entry.message === null ? (entry.message as string | null) : undefined,
        }))
        .filter((entry) => entry.request_id.length > 0);
};

export type ApprovalStreamState = "idle" | "connecting" | "connected" | "error";

export type ApprovalStreamHandlers = {
    onOpen: () => void;
    onMessage: (payload: JsonObject) => void;
    onError: (error?: unknown) => void;
    onClose: () => void;
};

export class ApprovalStreamClient {
    private socket: WebSocket | null = null;

    connect(resourceServer: string, token: string, handlers: ApprovalStreamHandlers): void {
        if (this.socket && this.socket.readyState <= WebSocket.OPEN) {
            return;
        }

        const websocketBase = resourceServer.replace(/^http/i, "ws");
        const socket = new WebSocket(`${websocketBase}/ws/approvals?access_token=${encodeURIComponent(token)}`);
        this.socket = socket;

        socket.onopen = () => handlers.onOpen();
        socket.onmessage = (event) => {
            try {
                const parsed = JSON.parse(event.data as string) as JsonObject;
                handlers.onMessage(parsed);
            } catch (error) {
                handlers.onError(error);
            }
        };
        socket.onerror = (event) => handlers.onError(event);
        socket.onclose = () => {
            if (this.socket === socket) {
                this.socket = null;
            }
            handlers.onClose();
        };
    }

    disconnect(): void {
        if (this.socket) {
            this.socket.close();
            this.socket = null;
        }
    }
}
