import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';

export function CallbackPage() {
  const [params] = useSearchParams();
  const [info, setInfo] = useState<Record<string, string>>({});

  useEffect(() => {
    const obj: Record<string, string> = {};
    params.forEach((value, key) => {
      obj[key] = value;
    });
    setInfo(obj);

    const message = {
      type: 'keybound-auth-callback',
      ...obj,
    };

    if (window.opener && !window.opener.closed) {
      window.opener.postMessage(message, window.location.origin);
      setTimeout(() => window.close(), 50);
      return;
    }

    if (window.parent !== window) {
      window.parent.postMessage(message, window.location.origin);
    }
  }, [params]);

  return (
    <section className="space-y-4">
      <div className="alert alert-info">
        <span>Authorization callback received. If opened as popup or iframe, result was sent to the app window.</span>
      </div>
      <div className="card bg-base-100 shadow-md">
        <div className="card-body">
          <h2 className="card-title">Callback payload</h2>
          <pre className="bg-base-200 rounded-lg p-3 overflow-auto text-xs">{JSON.stringify(info, null, 2)}</pre>
        </div>
      </div>
    </section>
  );
}
