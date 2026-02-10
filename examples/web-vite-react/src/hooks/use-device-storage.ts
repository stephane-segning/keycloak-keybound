import {useCallback, useEffect, useSyncExternalStore} from 'react';
import {generateKeyPair} from '../lib/crypto';
import {
    DeviceRecord,
    ensureDeviceStoreReady,
    getDeviceSnapshot,
    saveDeviceRecord,
    subscribeToDeviceChanges,
} from '../lib/device-db';

export const useDeviceStorage = () => {
    useEffect(() => {
        void ensureDeviceStoreReady();
    }, []);

    const device = useSyncExternalStore(
        (notify) => {
            const unsubscribe = subscribeToDeviceChanges(notify);
            return () => unsubscribe();
        },
        getDeviceSnapshot,
        getDeviceSnapshot
    );

    const ensureDevice = useCallback(
        async () => {
            await ensureDeviceStoreReady();
            if (device?.deviceId && device.publicJwk && device.privateJwk) {
                return device;
            }

            const deviceId = crypto.randomUUID();
            const {publicJwk, privateJwk} = await generateKeyPair();
            const record: DeviceRecord = {deviceId, publicJwk, privateJwk};
            await saveDeviceRecord(record);
            return record;
        },
        [device]
    );

    const setUserId = useCallback(async (userId: string) => {
        if (!userId) return;
        await ensureDeviceStoreReady();
        const current = getDeviceSnapshot();
        if (!current) return;
        await saveDeviceRecord({...current, userId});
    }, []);

    return {device, ensureDevice, setUserId};
};
