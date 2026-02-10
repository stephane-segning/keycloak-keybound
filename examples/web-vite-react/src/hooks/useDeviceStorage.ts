import { useEffect, useState } from 'react';
import { DeviceRecord, loadDeviceRecord, saveDeviceRecord } from '../lib/storage';
import { generateKeyPair } from '../lib/crypto';

export function useDeviceStorage() {
  const [device, setDevice] = useState<DeviceRecord | null>(null);

  useEffect(() => {
    (async () => {
      const stored = await loadDeviceRecord();
      if (stored) setDevice(stored);
    })();
  }, []);

  const ensureDevice = async () => {
    if (device?.deviceId && device.publicJwk && device.privateJwk) {
      return device;
    }
    const deviceId = crypto.randomUUID();
    const { publicJwk, privateJwk } = await generateKeyPair();
    const record: DeviceRecord = { deviceId, publicJwk, privateJwk };
    await saveDeviceRecord(record);
    setDevice(record);
    return record;
  };

  const setUserId = async (userId: string) => {
    const current = device ?? (await ensureDevice());
    const updated = { ...current, userId };
    await saveDeviceRecord(updated);
    setDevice(updated);
  };

  return { device, ensureDevice, setUserId };
}
