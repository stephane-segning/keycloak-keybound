import { Link } from 'react-router-dom';
import { useDeviceStorage } from '../hooks/useDeviceStorage';

export function HomePage() {
  const { device, ensureDevice } = useDeviceStorage();

  return (
    <section>
      <h2>Device Metadata</h2>
      <pre>{JSON.stringify(device ?? { ready: false }, null, 2)}</pre>
      <button onClick={ensureDevice}>Ensure key pair + device_id</button>
      <p>
        After the device exists you can start the public-key redirect from <Link to="/login">/login</Link>, handle
        the callback at <Link to="/callback">/callback</Link>, and then hydrate the session details in{' '}
        <Link to="/session">/session</Link>.
      </p>
    </section>
  );
}
