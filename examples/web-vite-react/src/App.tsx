import { Link, Route, Routes } from 'react-router-dom';
import { HomePage } from './routes/HomePage';
import { LoginPage } from './routes/LoginPage';
import { CallbackPage } from './routes/CallbackPage';
import { SessionPage } from './routes/SessionPage';
import { ResourcePage } from './routes/ResourcePage';

export function App() {
  return (
    <main>
      <h1>Keybound Web Example</h1>
      <nav>
        <Link to="/">Home</Link> | <Link to="/login">Login</Link> | <Link to="/callback">Callback</Link> |{' '}
        <Link to="/session">Session</Link> | <Link to="/resource">Resource</Link>
      </nav>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/callback" element={<CallbackPage />} />
        <Route path="/session" element={<SessionPage />} />
        <Route path="/resource" element={<ResourcePage />} />
      </Routes>
    </main>
  );
}
