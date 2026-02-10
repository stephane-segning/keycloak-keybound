import { Link } from 'react-router-dom';
import { useDeviceStorage } from '../hooks/useDeviceStorage';

export function HomePage() {
  const { device, ensureDevice } = useDeviceStorage();

  return (
    <section className="space-y-4">
      <div className="card bg-base-100 shadow-md">
        <div className="card-body">
          <h2 className="card-title">Device Metadata</h2>
          <pre className="bg-base-200 rounded-lg p-3 overflow-auto text-xs">{JSON.stringify(device ?? { ready: false }, null, 2)}</pre>
          <div className="card-actions">
            <button className="btn btn-primary" onClick={ensureDevice}>
              Ensure key pair + device_id
            </button>
          </div>
          <p className="text-sm opacity-80">
            Start public-key login from <Link className="link link-primary" to="/login">/login</Link>. The callback at{' '}
            <Link className="link link-primary" to="/callback">/callback</Link> is now forwarded automatically when used in iframe mode.
          </p>
        </div>
      </div>
    </section>
  );
}
