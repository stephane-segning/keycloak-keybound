import { Link, Route, Routes } from 'react-router-dom';

function Placeholder({ title }: { title: string }) {
  return <h2>{title}</h2>;
}

export function App() {
  return (
    <main>
      <h1>Keybound Web Example</h1>
      <nav>
        <Link to="/">Home</Link> | <Link to="/login">Login</Link> | <Link to="/callback">Callback</Link> |{' '}
        <Link to="/session">Session</Link> | <Link to="/resource">Resource</Link>
      </nav>
      <Routes>
        <Route path="/" element={<Placeholder title="Home" />} />
        <Route path="/login" element={<Placeholder title="Login" />} />
        <Route path="/callback" element={<Placeholder title="Callback" />} />
        <Route path="/session" element={<Placeholder title="Session" />} />
        <Route path="/resource" element={<Placeholder title="Resource" />} />
      </Routes>
    </main>
  );
}
