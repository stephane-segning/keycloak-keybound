import JsonView from '@microlink/react-json-view';
import {Link} from 'react-router-dom';
import {useDeviceStorage} from '../hooks/use-device-storage';
import {JsonDisplay} from "../components/json-display";

export const HomePage = () => {
    const {device, ensureDevice} = useDeviceStorage();
    const deviceReady = Boolean(device?.publicJwk && device?.privateJwk);

    return (
        <section className="space-y-8">
            <div className="hero border border-base-300 bg-base-100">
                <div className="hero-content w-full max-w-none px-4 py-10 sm:px-8 sm:py-14">
                    <div className="w-full space-y-6">
                        <div className="space-y-2">
                            <p className="text-xs uppercase tracking-[0.2em] text-base-content/70">landing</p>
                            <h1 className="text-3xl font-semibold sm:text-4xl">Device-first public key login</h1>
                            <p className="max-w-3xl text-sm text-base-content/80 sm:text-base">
                                This client keeps device keys in TanStack DB and persists them into IndexedDB through
                                idb-keyval for
                                resilient authentication flows.
                            </p>
                        </div>

                        <div className="flex flex-wrap gap-3">
                            <button
                                className="btn rounded-none border border-base-content bg-base-content px-5 text-base-100"
                                onClick={() => void ensureDevice()}>
                                {deviceReady ? 'device ready' : 'create device metadata'}
                            </button>
                            <Link className="btn rounded-none border border-base-300 bg-base-100 px-5" to="/login">
                                start login
                            </Link>
                            <Link className="btn rounded-none border border-base-300 bg-base-100 px-5" to="/session">
                                edit session
                            </Link>
                        </div>
                    </div>
                </div>
            </div>

            <div className="grid gap-4 lg:grid-cols-2">
                <article className="border border-base-300 bg-base-100 p-5">
                    <h2 className="text-base font-semibold">device status</h2>
                    <dl className="mt-4 grid gap-2 text-sm">
                        <div>
                            <dt className="text-xs uppercase tracking-[0.16em] text-base-content/60">device id</dt>
                            <dd className="font-mono text-xs sm:text-sm">{device?.deviceId ?? 'not created'}</dd>
                        </div>
                        <div>
                            <dt className="text-xs uppercase tracking-[0.16em] text-base-content/60">user id</dt>
                            <dd>{device?.userId ?? 'not assigned'}</dd>
                        </div>
                        <div>
                            <dt className="text-xs uppercase tracking-[0.16em] text-base-content/60">key type</dt>
                            <dd>{device?.publicJwk?.kty ?? 'unknown'}</dd>
                        </div>
                    </dl>
                </article>

                <article className="border border-base-300 bg-base-100 p-5">
                    <h2 className="text-base font-semibold">routes</h2>
                    <div className="mt-4 flex flex-wrap gap-2">
                        <Link className="btn btn-sm rounded-none border border-base-300 bg-base-100" to="/callback">
                            callback payload
                        </Link>
                        <Link className="btn btn-sm rounded-none border border-base-300 bg-base-100" to="/resource">
                            protected resource
                        </Link>
                    </div>
                </article>
            </div>

            <article className="border border-base-300 bg-base-100 p-5">
                <h2 className="text-base font-semibold">stored snapshot</h2>
                <div className="mt-4 overflow-auto p-2 text-xs">
                    <JsonDisplay src={device ?? {ready: false}} />
                </div>
            </article>
        </section>
    );
};
