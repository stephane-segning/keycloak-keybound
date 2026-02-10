import { useEffect, useState } from 'react';
import { RESOURCE_SERVER } from '../config';

export function ResourcePage() {
  const [output, setOutput] = useState<string>('No request yet');

  useEffect(() => {
    (async () => {
      try {
        const response = await fetch(`${RESOURCE_SERVER}/get`);
        const data = await response.json();
        setOutput(JSON.stringify(data, null, 2));
      } catch (error) {
        setOutput(`Request failed: ${(error as Error).message}`);
      }
    })();
  }, []);

  return (
    <section>
      <h2>Resource server call</h2>
      <pre>{output}</pre>
    </section>
  );
}
