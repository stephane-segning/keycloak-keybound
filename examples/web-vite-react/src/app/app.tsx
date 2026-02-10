import {NavLink, Route, Routes} from 'react-router-dom';
import {HomePage} from '../pages/home-page';
import {LoginPage} from '../pages/login-page';
import {CallbackPage} from '../pages/callback-page';
import {ResourcePage} from '../pages/resource-page';
import {SessionPage} from '../pages/session-page';

const navLinks = [
    {label: 'home', to: '/'},
    {label: 'login', to: '/login'},
    {label: 'resource', to: '/resource'},
    {label: 'session', to: '/session'},
];

export const App = () => (
    <div className="min-h-screen bg-base-200 text-base-content">
        <header className="border-b border-base-300 bg-base-100">
            <div
                className="mx-auto flex max-w-6xl flex-col gap-4 px-4 py-4 sm:px-6 md:flex-row md:items-center md:justify-between">
                <div>
                    <p className="text-xs uppercase tracking-[0.2em] text-base-content/70">Keybound</p>
                    <p className="text-lg font-semibold">Web Client</p>
                </div>
                <nav className="flex flex-wrap gap-2">
                    {navLinks.map((link) => (
                        <NavLink
                            key={link.to}
                            to={link.to}
                            className={({isActive}) =>
                                [
                                    'btn btn-sm rounded-none border border-base-300 bg-base-100 px-4 font-medium lowercase',
                                    isActive ? 'border-base-content bg-base-300 text-base-content' : 'text-base-content/80',
                                ].join(' ')
                            }
                        >
                            {link.label}
                        </NavLink>
                    ))}
                </nav>
            </div>
        </header>

        <main className="mx-auto w-full max-w-6xl px-4 py-8 sm:px-6">
            <Routes>
                <Route path="/" element={<HomePage/>}/>
                <Route path="/login" element={<LoginPage/>}/>
                <Route path="/callback" element={<CallbackPage/>}/>
                <Route path="/resource" element={<ResourcePage/>}/>
                <Route path="/session" element={<SessionPage/>}/>
            </Routes>
        </main>
    </div>
);
