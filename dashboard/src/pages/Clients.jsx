import { useEffect, useState } from "react";
import { UserPlus, KeyRound, ShieldCheck, ShieldOff, RefreshCw } from "lucide-react";
import { api, currentUser } from "../api";

/**
 * CLIENTS (admin only). Each row is a dashboard account with its own isolated view: a reseller
 * only ever sees the devices they activated, so a new client never inherits an old client's boxes.
 */
export default function Clients() {
  const me = currentUser();
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState({ email: "", name: "", password: "", role: "reseller" });
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState(null);
  const [err, setErr] = useState(null);

  const load = () => {
    setLoading(true);
    return api
      .resellers()
      .then((r) => setRows(r.resellers))
      .catch((e) => setErr(e.message))
      .finally(() => setLoading(false));
  };
  useEffect(() => { load(); }, []);

  async function create(e) {
    e.preventDefault();
    setBusy(true); setErr(null); setMsg(null);
    try {
      await api.createReseller(form);
      setMsg(`Account created for ${form.email}`);
      setForm({ email: "", name: "", password: "", role: "reseller" });
      await load();
    } catch (e) { setErr(e.message); }
    finally { setBusy(false); }
  }

  async function toggleActive(r) {
    const verb = r.active ? "disable" : "enable";
    if (!window.confirm(`${verb === "disable" ? "Disable" : "Enable"} ${r.email}?`)) return;
    try {
      await api.setResellerActive(r.id, !r.active);
      await load();
    } catch (e) { setErr(e.message); }
  }

  async function resetPassword(r) {
    const pw = window.prompt(`New password for ${r.email} (min 8 characters):`);
    if (!pw) return;
    try {
      await api.setResellerPassword(r.id, pw);
      setMsg(`Password updated for ${r.email}`);
    } catch (e) { setErr(e.message); }
  }

  const upd = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  return (
    <div className="animate-fade-up">
      <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="page-title">Clients</h1>
          <p className="page-sub">
            Every client signs in to their own dashboard and sees only the devices they activated.
          </p>
        </div>
        <button onClick={load} className="btn-ghost" disabled={loading}>
          <RefreshCw size={15} className={loading ? "animate-spin" : ""} /> Refresh
        </button>
      </div>

      {err && <p className="mb-4 rounded-lg bg-danger/15 px-3 py-2 text-sm text-danger">{err}</p>}
      {msg && <p className="mb-4 rounded-lg bg-ok/15 px-3 py-2 text-sm text-ok">{msg}</p>}

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <form onSubmit={create} className="card space-y-4">
          <h2 className="flex items-center gap-2 font-semibold text-gold-ivory">
            <UserPlus size={18} className="text-gold-primary" /> Add a client
          </h2>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div>
              <label className="mb-1 block text-sm text-gold-muted">Email *</label>
              <input type="email" required value={form.email} onChange={upd("email")}
                className="input" placeholder="client@example.com" />
            </div>
            <div>
              <label className="mb-1 block text-sm text-gold-muted">Name</label>
              <input value={form.name} onChange={upd("name")} className="input" placeholder="Ali Hassan" />
            </div>
          </div>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div>
              <label className="mb-1 block text-sm text-gold-muted">Password *</label>
              <input type="text" required minLength={8} value={form.password} onChange={upd("password")}
                className="input font-mono" placeholder="min 8 characters" />
              <p className="mt-1 text-[11px] text-gold-muted">Share it with them; they can be given a new one any time.</p>
            </div>
            <div>
              <label className="mb-1 block text-sm text-gold-muted">Role</label>
              <select value={form.role} onChange={upd("role")} className="input">
                <option value="reseller">Reseller — sees only their own devices</option>
                <option value="admin">Admin — sees everything, manages clients</option>
              </select>
            </div>
          </div>
          <button type="submit" disabled={busy} className="btn-primary w-full">
            {busy ? "Creating…" : "Create client"}
          </button>
        </form>

        <div className="card p-0">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[420px] text-sm">
              <thead>
                <tr className="border-b border-gold-border text-left text-gold-muted">
                  <th className="px-4 py-3 font-medium">Account</th>
                  <th className="px-4 py-3 font-medium">Role</th>
                  <th className="px-4 py-3 font-medium">Status</th>
                  <th className="px-4 py-3" />
                </tr>
              </thead>
              <tbody>
                {loading && rows.length === 0 && (
                  <tr><td colSpan={4} className="px-4 py-6"><div className="skeleton h-5 w-full" /></td></tr>
                )}
                {!loading && rows.length === 0 && (
                  <tr><td colSpan={4} className="px-4 py-8 text-center text-gold-muted">No clients yet.</td></tr>
                )}
                {rows.map((r) => (
                  <tr key={r.id} className="border-b border-gold-border/50">
                    <td className="px-4 py-3">
                      <p className="text-gold-ivory">{r.name || "—"}</p>
                      <p className="truncate text-xs text-gold-muted">{r.email}</p>
                    </td>
                    <td className="px-4 py-3">
                      <span className={`badge ${r.role === "admin" ? "bg-gold-primary/20 text-gold-primary" : "bg-gold-border/50 text-gold-ivory"}`}>
                        {r.role}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <span className={`badge ${r.active ? "bg-ok/20 text-ok" : "bg-danger/20 text-danger"}`}>
                        {r.active ? "active" : "disabled"}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right">
                      <div className="flex justify-end gap-2">
                        <button onClick={() => resetPassword(r)} className="btn-ghost px-2.5 py-1.5" title="Set a new password">
                          <KeyRound size={15} />
                        </button>
                        {r.id !== me.id && (
                          <button
                            onClick={() => toggleActive(r)}
                            className={`btn-ghost px-2.5 py-1.5 ${r.active ? "text-danger" : "text-ok"}`}
                            title={r.active ? "Disable sign-in" : "Enable sign-in"}
                          >
                            {r.active ? <ShieldOff size={15} /> : <ShieldCheck size={15} />}
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
}
