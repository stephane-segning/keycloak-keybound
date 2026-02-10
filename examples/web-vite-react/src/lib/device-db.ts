import {
    createCollection,
    type DeleteMutationFnParams,
    type InsertMutationFnParams,
    type OperationType,
    type PendingMutation,
    type SyncConfig,
    type UpdateMutationFnParams,
} from '@tanstack/db';
import {del, get, set} from 'idb-keyval';

export type DeviceRecord = {
    deviceId: string;
    publicJwk: JsonWebKey;
    privateJwk: JsonWebKey;
    userId?: string;
};

type StoragePayload = Record<string, DeviceRecord>;

const STORAGE_KEY = 'keybound-device';

let readyForPersist = false;
let pendingPersistDuringInit = false;
let persistQueue: Promise<void> = Promise.resolve();
let lastPersistedAt: number | null = null;
let lastPersistError: unknown = null;
const persistListeners = new Set<() => void>();
let hydrationResolve: (() => void) | null = null;
const hydrationPromise = new Promise<void>((resolve) => {
    hydrationResolve = resolve;
});

function notifyPersistListeners() {
    persistListeners.forEach((listener) => {
        try {
            listener();
        } catch (error) {
            console.error('[deviceDb] persist listener failed', error);
        }
    });
}

async function loadStoredState(): Promise<StoragePayload> {
    try {
        return (await get<StoragePayload>(STORAGE_KEY)) ?? ({} as StoragePayload);
    } catch (error) {
        console.error('[deviceDb] failed to read stored devices', error);
        lastPersistError = error;
        notifyPersistListeners();
        return {} as StoragePayload;
    }
}

async function persistState() {
    if (!readyForPersist) {
        pendingPersistDuringInit = true;
        return;
    }

    try {
        const entries = Array.from(deviceCollection.state.values()) as DeviceRecord[];
        if (entries.length === 0) {
            await del(STORAGE_KEY);
        } else {
            const payload: StoragePayload = {};
            for (const record of entries) {
                payload[record.deviceId] = record;
            }
            await set(STORAGE_KEY, payload);
        }
        lastPersistedAt = Date.now();
        lastPersistError = null;
    } catch (error) {
        console.error('[deviceDb] failed to persist device store', error);
        lastPersistError = error;
    } finally {
        notifyPersistListeners();
    }
}

function schedulePersist() {
    persistQueue = persistQueue
        .then(() => persistState())
        .catch((error) => {
            console.error('[deviceDb] persistence queue error', error);
        });
}

function markHydrated() {
    readyForPersist = true;
    if (pendingPersistDuringInit) {
        pendingPersistDuringInit = false;
        schedulePersist();
    }
    hydrationResolve?.();
    hydrationResolve = null;
}

function createIdbSync() {
    let syncBegin: (() => void) | null = null;
    let syncWrite: ((message: { type: OperationType; value: DeviceRecord }) => void) | null = null;
    let syncCommit: (() => void) | null = null;

    const sync: SyncConfig<DeviceRecord, string> = {
        sync: ({begin, write, commit, markReady}) => {
            syncBegin = begin;
            syncWrite = write;
            syncCommit = commit;

            void (async () => {
                try {
                    const records = await loadStoredState();
                    const entries = Object.values(records) as DeviceRecord[];
                    if (entries.length > 0) {
                        begin();
                        entries.forEach((item) => {
                            write({type: 'insert', value: item});
                        });
                        commit();
                    }
                } catch (error) {
                    console.error('[deviceDb] failed to hydrate devices', error);
                    lastPersistError = error;
                    notifyPersistListeners();
                } finally {
                    markReady();
                    markHydrated();
                }
            })();

            return () => {
            };
        },
    };

    const confirmOperationsSync = (mutations: Array<PendingMutation<DeviceRecord>>) => {
        if (!syncBegin || !syncWrite || !syncCommit) {
            return;
        }

        syncBegin();
        const write = syncWrite!;
        const commit = syncCommit!;
        mutations.forEach((mutation) => {
            write({type: mutation.type, value: mutation.modified});
        });
        commit();

        schedulePersist();
    };

    return {sync, confirmOperationsSync};
}

const {sync, confirmOperationsSync} = createIdbSync();

const handleInsert = async (params: InsertMutationFnParams<DeviceRecord, string>) => {
    confirmOperationsSync(params.transaction.mutations);
};

const handleUpdate = async (params: UpdateMutationFnParams<DeviceRecord, string>) => {
    confirmOperationsSync(params.transaction.mutations);
};

const handleDelete = async (params: DeleteMutationFnParams<DeviceRecord, string>) => {
    confirmOperationsSync(params.transaction.mutations);
};

const deviceCollection = createCollection<DeviceRecord, string>({
    id: 'keybound-devices',
    getKey: (item) => item.deviceId,
    sync,
    startSync: true,
    gcTime: 0,
    onInsert: handleInsert,
    onUpdate: handleUpdate,
    onDelete: handleDelete,
});

deviceCollection.subscribeChanges(() => {
    schedulePersist();
});

export function ensureDeviceStoreReady() {
    return hydrationPromise;
}

export async function loadDeviceRecord(): Promise<DeviceRecord | null> {
    await ensureDeviceStoreReady();
    const first = deviceCollection.state.values().next();
    return first.done ? null : first.value;
}

export async function saveDeviceRecord(record: DeviceRecord): Promise<void> {
    await ensureDeviceStoreReady();
    if (deviceCollection.state.has(record.deviceId)) {
        const tx = deviceCollection.update(record.deviceId, (draft) => {
            Object.assign(draft, record);
        });
        await tx.isPersisted.promise;
        return;
    }
    const tx = deviceCollection.insert(record);
    await tx.isPersisted.promise;
}

export function getDeviceSnapshot(): DeviceRecord | null {
    const first = deviceCollection.state.values().next();
    return first.done ? null : first.value;
}

export function getDeviceStoreCount() {
    return deviceCollection.state.size;
}

export function getDeviceStoreMetadata() {
    return {
        lastPersistedAt,
        lastError: lastPersistError,
    };
}

export function subscribeToDeviceChanges(listener: () => void) {
    const subscription = deviceCollection.subscribeChanges(() => listener(), {includeInitialState: true});
    return () => subscription.unsubscribe();
}

export function subscribeToPersistEvents(listener: () => void) {
    persistListeners.add(listener);
    return () => persistListeners.delete(listener);
}

void ensureDeviceStoreReady();
