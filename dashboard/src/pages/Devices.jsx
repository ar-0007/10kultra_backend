import { useEffect, useMemo, useState } from "react";
import { Power, ShieldOff, Search, RefreshCw, Server } from "lucide-react";
import { api, FALLBACK_PORTALS } from "../api";
import ServerPicker from "../components/ServerPicker";

const BADGE = {
  activated: "bg-ok/20 text-ok",
  pending: "bg-warn/20 text-warn",
  payment_required: "bg-warn/20 text-warn",
  revoked: "bg-danger/20 text-danger",
};

const PAY_BADGE = {
  paid: "bg-ok/20 text-ok",
  unpaid: "bg-warn/20 text-warn",
  expired: "bg-danger/20 text-danger",
};

const FILTERS = [
  { key: "all", label: "All" },
  { key: "pending", label: "Pending" },
  { key: "activated", label: "Activated" },
  { key: "revoked", label: "Revoked" },
];

const short = (url) => (url || "—").replace(/^https?:\/\//, "");

export default function Devices() {
  const [devices, setDevices] = useState([]);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState("all");
  const [editing, setEditing] = useState(null);
  const [customer, setCustomer] = useState("");
  const [payment, setPayment] = useState("paid");
  const [server, setServer] = useState(FALLBACK_PORTALS[0].url);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState(null);

  const load = () => {
    setLoading(true);
    return api
      .devices()
      .then((r) => setDevices(r.devices))
      .catch(() => {})
      .finally(() => setLoading(false));
  };
  useEffect(() => { load(); }, []);

  const shown = useMemo(() => {
    const q = query.trim().toLowerCase();
    return devices.filter((d) => {
      if (filter !== "all" && d.status !== filter) return false;
      if (!q) return true;
      return [d.device_id, d.customer_name, d.mac, d.server_url]
        .some((v) => (v || "").toLowerCase().includes(q));
    });
  }, [devices, query, filter]);

  function openEdit(d) {
    setEditing(d);
    setCustomer(d.customer_name || "");
    setPayment(d.payment_status === "unpaid" ? "paid" : (d.payment_status || "paid"));
    // Only ever preselect one of OUR three servers. A row carried over from another product can
    // hold a foreign URL — preselecting that would leave the picker with nothing highlighted.
    setServer(
      FALLBACK_PORTALS.find((p) => p.url === d.server_url)?.url ?? FALLBACK_PORTALS[0].url
    );
    setErr(null);
  }
  async function save() {
    setBusy(true); setErr(null);
    try {
      await api.activateDevice(editing.id, {
        customer_name: customer,
        payment_status: payment,
        server_url: server,
      });
      setEditing(null);
      await load();
    } catch (e) { setErr(e.message); }
    finally { setBusy(false); }
  }
  // Quick payment toggle straight from the table.
  function togglePayment(d) {
    const next = d.payment_status === "paid" ? "unpaid" : "paid";
    api.updatePayment(d.id, next).then(load).catch(() => {});
  }
  // Revoke is destructive (locks the box) — always confirm so an accidental click can't kill a device.
  function revoke(d) {
    const who = d.customer_name || d.device_id.slice(0, 16);
    if (window.confirm(`Revoke "${who}"?\n\nThe box will be LOCKED immediately until you re-activate it.`)) {
      api.revokeDevice(d.id).then(load).catch(() => {});
    }
  }

  return (
    <div className="animate-fade-up">
      <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="page-title">Devices</h1>
          <p className="page-sub">
            Activate a box and assign its server, or revoke it (remote kill switch).
            Click a payment badge to toggle paid / unpaid.
          </p>
        </div>
        <button onClick={load} className="btn-ghost" disabled={loading}>
          <RefreshCw size={15} className={loading ? "animate-spin" : ""} /> Refresh
        </button>
      </div>

      {/* Search + status filter */}
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <div className="relative min-w-[220px] flex-1">
          <Search size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-gold-muted" />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            className="input pl-9"
            placeholder="Search by customer, MAC, device id or server…"
          />
        </div>
        <div className="flex flex-wrap gap-1.5">
          {FILTERS.map((f) => (
            <button
              key={f.key}
              onClick={() => setFilter(f.key)}
              className={`rounded-full px-3.5 py-1.5 text-xs font-semibold transition-colors ${
                filter === f.key
                  ? "bg-gold-primary text-gold-bg"
                  : "border border-gold-border text-gold-muted hover:text-gold-ivory"
              }`}
            >
              {f.label}
            </button>
          ))}
        </div>
      </div>

      {/* Table — tablet / desktop */}
      <div className="card hidden overflow-x-auto p-0 md:block">
        <table className="w-full min-w-[720px] text-sm">
          <thead>
            <tr className="border-b border-gold-border text-left text-gold-muted">
              <th className="px-4 py-3 font-medium">Device ID</th>
              <th className="px-4 py-3 font-medium">Customer</th>
              <th className="px-4 py-3 font-medium">Server</th>
              <th className="px-4 py-3 font-medium">Status</th>
              <th className="px-4 py-3 font-medium">Payment</th>
              <th className="px-4 py-3 font-medium">MAC</th>
              <th className="px-4 py-3 font-medium">Ver</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {loading && devices.length === 0 && (
              <tr><td colSpan={8} className="px-4 py-6"><div className="skeleton h-5 w-full" /></td></tr>
            )}
            {!loading && shown.length === 0 && (
              <tr><td colSpan={8} className="px-4 py-8 text-center text-gold-muted">
                {devices.length === 0 ? "No devices yet — boxes appear here on first boot." : "No devices match that search."}
              </td></tr>
            )}
            {shown.map((d) => (
              <tr key={d.id} className="border-b border-gold-border/50 transition-colors hover:bg-gold-card2/40">
                <td className="px-4 py-3 font-mono text-xs text-gold-ivory">{d.device_id.slice(0, 16)}…</td>
                <td className="px-4 py-3 text-gold-ivory">{d.customer_name || "—"}</td>
                <td className="max-w-[190px] truncate px-4 py-3 font-mono text-xs text-gold-muted" title={d.server_url || "not assigned"}>
                  {short(d.server_url)}
                </td>
                <td className="px-4 py-3"><span className={`badge ${BADGE[d.status] ?? ""}`}>{d.status}</span></td>
                <td className="px-4 py-3">
                  <button
                    onClick={() => togglePayment(d)}
                    className={`badge ${PAY_BADGE[d.payment_status] ?? "bg-warn/20 text-warn"}`}
                    title="Click to toggle paid/unpaid"
                  >
                    {d.payment_status || "unpaid"}
                  </button>
                </td>
                <td className="px-4 py-3 font-mono text-xs text-gold-muted">{d.mac || "—"}</td>
                <td className="px-4 py-3 text-gold-muted">{d.app_version ?? "—"}</td>
                <td className="px-4 py-3 text-right">
                  <div className="flex justify-end gap-2">
                    <button onClick={() => openEdit(d)} className="btn-ghost px-2.5 py-1.5" title="Activate / assign server">
                      <Power size={15} />
                    </button>
                    {d.status !== "revoked" && (
                      <button onClick={() => revoke(d)} className="btn-ghost px-2.5 py-1.5 text-danger" title="Revoke (locks the box)">
                        <ShieldOff size={15} />
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Cards — mobile */}
      <div className="space-y-3 md:hidden">
        {!loading && shown.length === 0 && (
          <div className="card text-center text-sm text-gold-muted">
            {devices.length === 0 ? "No devices yet." : "No devices match that search."}
          </div>
        )}
        {shown.map((d) => (
          <div key={d.id} className="card space-y-3 p-4">
            <div className="flex items-start justify-between gap-3">
              <p className="min-w-0 break-all font-mono text-xs text-gold-ivory">{d.device_id.slice(0, 24)}…</p>
              <span className={`badge shrink-0 ${BADGE[d.status] ?? ""}`}>{d.status}</span>
            </div>

            <div className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
              <div>
                <p className="text-xs text-gold-muted">Customer</p>
                <p className="truncate text-gold-ivory">{d.customer_name || "—"}</p>
              </div>
              <div>
                <p className="text-xs text-gold-muted">Payment</p>
                <button
                  onClick={() => togglePayment(d)}
                  className={`badge ${PAY_BADGE[d.payment_status] ?? "bg-warn/20 text-warn"}`}
                  title="Tap to toggle paid/unpaid"
                >
                  {d.payment_status || "unpaid"}
                </button>
              </div>
              <div>
                <p className="text-xs text-gold-muted">MAC</p>
                <p className="truncate font-mono text-xs text-gold-ivory">{d.mac || "—"}</p>
              </div>
              <div>
                <p className="text-xs text-gold-muted">Version</p>
                <p className="text-gold-ivory">{d.app_version ?? "—"}</p>
              </div>
            </div>

            <div className="flex items-center gap-1.5 border-t border-gold-border pt-2 text-xs">
              <Server size={12} className="shrink-0 text-gold-muted" />
              <span className="break-all font-mono text-gold-ivory">{short(d.server_url)}</span>
            </div>

            <div className="flex gap-2 border-t border-gold-border pt-3">
              <button onClick={() => openEdit(d)} className="btn-ghost flex-1">
                <Power size={15} /> Activate
              </button>
              {d.status !== "revoked" && (
                <button onClick={() => revoke(d)} className="btn-ghost flex-1 text-danger">
                  <ShieldOff size={15} /> Revoke
                </button>
              )}
            </div>
          </div>
        ))}
      </div>

      {editing && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 px-4 backdrop-blur-sm" onClick={() => setEditing(null)}>
          <div className="card w-full max-w-lg animate-fade-up space-y-4" onClick={(e) => e.stopPropagation()}>
            <h3 className="text-lg font-semibold text-gold-ivory">Activate device</h3>
            <p className="break-all font-mono text-xs text-gold-muted">{editing.device_id}</p>
            {/* MAC is read-only — captured on register. */}
            <div className="flex items-center justify-between gap-3 rounded-xl bg-gold-bg/50 px-4 py-3 text-sm">
              <span className="text-gold-muted">MAC</span>
              <span className="font-mono text-xs text-gold-ivory">{editing.mac || "auto"}</span>
            </div>
            <ServerPicker value={server} onChange={setServer} />
            <div>
              <label className="mb-1 block text-sm text-gold-muted">Customer name (optional)</label>
              <input value={customer} onChange={(e) => setCustomer(e.target.value)} className="input" placeholder="e.g. Ali Hassan" />
            </div>
            <div>
              <label className="mb-1 block text-sm text-gold-muted">Payment</label>
              <select value={payment} onChange={(e) => setPayment(e.target.value)} className="input">
                <option value="paid">Paid</option>
                <option value="unpaid">Unpaid</option>
                <option value="expired">Expired</option>
              </select>
            </div>
            {err && <p className="rounded-lg bg-danger/15 px-3 py-2 text-sm text-danger">{err}</p>}
            <div className="flex justify-end gap-2">
              <button onClick={() => setEditing(null)} className="btn-ghost">Cancel</button>
              <button onClick={save} disabled={busy} className="btn-primary">
                {busy ? "Activating…" : "Activate"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
