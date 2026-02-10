import { useState } from 'react';
import { useDeviceStorage } from '../hooks/useDeviceStorage';

export function SessionPage() {
  const { device, setUserId } = useDeviceStorage();
  const [userId, setUserIdInput] = useState(device?.userId ?? '');

  const handleSave = async () => {
    if (!userId) return;
    await setUserId(userId);
  };

  return (
    <section>
      <h2>Session</h2>
      <pre>{JSON.stringify(device ?? { ready: false }, null, 2)}</pre>
      <label>
        User ID (backend):
        <input value={userId} onChange={(event) => setUserIdInput(event.target.value)} />
      </label>
      <button onClick={handleSave}>Save user id</button>
    </section>
  );
}
