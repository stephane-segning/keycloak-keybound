import { useEffect, useState } from 'react';
import { RESOURCE_SERVER } from '../config';
import { ensureAccessToken } from '../lib/auth';
import ReactJsonView from "@microlink/react-json-view";

export function ResourcePage() {
  const [output, setOutput] = useState<any>({msg: 'No request yet'});

  useEffect(() => {
    (async () => {
      try {
        const token = await ensureAccessToken();
        const response = await fetch(`${RESOURCE_SERVER}/get`, {
          headers: token ? { Authorization: `Bearer ${token}` } : {},
        });
        const data = await response.json();
        setOutput(data);
      } catch (error) {
        setOutput({
          msg: `Request failed: ${(error as Error).message}`,
          error,
        });
      }
    })();
  }, []);

  return (
    <section className="space-y-4">
      <div className="card bg-base-100 shadow-md">
        <div className="card-body">
          <h2 className="card-title">Resource Server Call</h2>
          <p className="text-sm opacity-80">Requests `${RESOURCE_SERVER}/get` with bearer token if available.</p>
          <div className="mockup-code w-full">
            <ReactJsonView src={output} theme="monokai" />
          </div>
        </div>
      </div>
    </section>
  );
}
