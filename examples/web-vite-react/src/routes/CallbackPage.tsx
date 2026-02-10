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
  }, [params]);

  return (
    <section>
      <h2>Callback</h2>
      <p>This page reads the query params from Keycloak.</p>
      <pre>{JSON.stringify(info, null, 2)}</pre>
      <p>Use the code/nonce to continue the custom grant in a separate step.</p>
    </section>
  );
}
