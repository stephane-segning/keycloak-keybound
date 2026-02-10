import { useEffect, useState } from 'react';
import { RESOURCE_SERVER } from '../config';
import { ensureAccessToken } from '../lib/auth';

export function ResourcePage() {
  const [output, setOutput] = useState<string>('No request yet');

  useEffect(() => {
    (async () => {
      try {
        const token = await ensureAccessToken();
        const response = await fetch(`${RESOURCE_SERVER}/get`, {
          headers: token ? { Authorization: `Bearer ${token}` } : {},
        });
        const data = await response.json();
        setOutput(JSON.stringify(data, null, 2));
      } catch (error) {
        setOutput(`Request failed: ${(error as Error).message}`);
      }
    })();
  }, []);

  return (
    <section className="space-y-4">
      <div className="card bg-base-100 shadow-md">
        <div className="card-body">
          <h2 className="card-title">Resource Server Call</h2>
          <p className="text-sm opacity-80">Requests `${RESOURCE_SERVER}/get` with bearer token if available.</p>
          <pre className="bg-base-200 rounded-lg p-3 overflow-auto text-xs">{output}</pre>
        </div>
      </div>
    </section>
  );
}
