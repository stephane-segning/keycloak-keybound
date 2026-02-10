import { get, set } from 'idb-keyval';

export type DeviceRecord = {
  deviceId: string;
  publicJwk: JsonWebKey;
  privateJwk: JsonWebKey;
  userId?: string;
};

const STORAGE_KEY = 'keybound-device';

export async function loadDeviceRecord(): Promise<DeviceRecord | null> {
  return (await get<DeviceRecord>(STORAGE_KEY)) ?? null;
}

export async function saveDeviceRecord(record: DeviceRecord): Promise<void> {
  await set(STORAGE_KEY, record);
}
