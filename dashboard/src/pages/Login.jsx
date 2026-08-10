import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, setSession } from "../api";
import ThemeToggle from "../components/ThemeToggle";

export default function Login() {
  const nav = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  async function onSubmit(e) {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const { token, ...user } = await api.login(email, password);
      setSession(token, user);
      nav("/overview");
    } catch (err) {
      setError(err.message || "Login failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="relative flex min-h-screen items-center justify-center px-4">
      <ThemeToggle className="absolute right-4 top-4" />
      <div className="w-full max-w-sm animate-fade-up">
        <div className="mb-8 flex flex-col items-center text-center">
          <img
            src="/logo.png"
            alt="10K Ultra"
            className="mb-4 h-24 w-24 rounded-3xl object-contain shadow-gold"
          />
          <h1 className="text-2xl font-bold tracking-tight text-gold-ivory">10K Ultra</h1>
          <p className="text-sm text-gold-muted">Control Panel</p>
        </div>
        <form onSubmit={onSubmit} className="card space-y-4">
          <div>
            <label className="mb-1 block text-sm text-gold-muted">Email</label>
            <input
              type="email"
              required
              autoComplete="username"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="input"
              placeholder="admin@10kultra.tv"
            />
          </div>
          <div>
            <label className="mb-1 block text-sm text-gold-muted">Password</label>
            <input
              type="password"
              required
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="input"
              placeholder="••••••••"
            />
          </div>
          {error && <p className="rounded-lg bg-danger/15 px-3 py-2 text-sm text-danger">{error}</p>}
          <button type="submit" disabled={loading} className="btn-primary w-full">
            {loading ? "Signing in…" : "Sign in"}
          </button>
        </form>
      </div>
    </div>
  );
}
