import {PublicKeyLoginPage} from '../pages/public-key-login-page';

export const App = () => (
    <div className="min-h-screen bg-base-200 text-base-content">
        <header className="border-b border-base-300 bg-base-100">
            <div className="mx-auto px-4 py-6 text-center">
                <p className="text-xs uppercase tracking-[0.4em] text-base-content/60">Keybound</p>
                <p className="text-2xl font-semibold">Public-Key Login Example</p>
                <p className="text-sm text-base-content/70">Demonstrates the `device-public-key-login` custom realm endpoint.</p>
            </div>
        </header>

        <main className="mx-auto flex w-full max-w-3xl flex-col gap-8 px-4 py-10">
            <PublicKeyLoginPage/>
        </main>
    </div>
);
