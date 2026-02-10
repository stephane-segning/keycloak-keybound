import { Link, Route, Routes } from 'react-router-dom';
import { HomePage } from './routes/HomePage';
import { LoginPage } from './routes/LoginPage';
import { CallbackPage } from './routes/CallbackPage';
import { ResourcePage } from './routes/ResourcePage';

export function App() {
  return (
    <main className="min-h-screen p-6">
      <div className="max-w-6xl mx-auto space-y-4">
        <div className="navbar bg-base-100 rounded-xl shadow-md">
          <div className="flex-1">
            <span className="text-lg font-semibold px-2">Keybound Web Example</span>
          </div>
          <div className="flex-none gap-2">
            <Link className="btn btn-ghost btn-sm" to="/">Home</Link>
            <Link className="btn btn-ghost btn-sm" to="/login">Login</Link>
            <Link className="btn btn-ghost btn-sm" to="/callback">Callback</Link>
            <Link className="btn btn-ghost btn-sm" to="/resource">Resource</Link>
          </div>
        </div>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/callback" element={<CallbackPage />} />
          <Route path="/resource" element={<ResourcePage />} />
        </Routes>
      </div>
    </main>
  );
}
