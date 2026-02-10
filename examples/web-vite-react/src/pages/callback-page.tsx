import {useEffect, useState} from 'react';
import {useSearchParams} from 'react-router-dom';
import {JsonDisplay} from "../components/json-display";

export const CallbackPage = () => {
    const [params] = useSearchParams();
    const [info, setInfo] = useState<Record<string, string>>({});

    useEffect(() => {
        // Normalize callback query params into a plain object for UI + postMessage.
        const payloadObject: Record<string, string> = {};
        params.forEach((value, key) => {
            payloadObject[key] = value;
        });
        setInfo(payloadObject);

        const message = {
            type: 'keybound-auth-callback',
            ...payloadObject,
        };

        // Popup mode: send payload back to opener, then close quickly.
        if (window.opener && !window.opener.closed) {
            window.opener.postMessage(message, window.location.origin);
            setTimeout(() => window.close(), 50);
            return;
        }

        // Iframe mode fallback.
        if (window.parent !== window) {
            window.parent.postMessage(message, window.location.origin);
        }
    }, [params]);

    return (
        <section className="space-y-4">
            <article className="border border-base-300 bg-base-100 p-4">
                <p className="text-sm text-base-content/80">
                    Authorization callback received. If this page opened in popup/iframe mode, it already posted the
                    payload to
                    the parent window.
                </p>
            </article>

            <article className="border border-base-300 bg-base-100 p-4">
                <h2 className="text-base font-semibold">callback payload</h2>

                <div className="mt-4 overflow-auto p-2 text-xs">
                    <JsonDisplay src={info}/>
                </div>
            </article>
        </section>
    );
};
