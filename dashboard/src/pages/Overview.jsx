import { useEffect, useState } from "react";
import {
  MonitorSmartphone, CheckCircle2, Clock, PackageOpen,
  ShieldOff, Wifi, Layers, AlertTriangle, Server,
} from "lucide-react";
import StatCard from "../components/StatCard";
import { usePortals } from "../components/ServerPicker";
import { api } from "../api";

function timeAgo(iso) {
  if (!iso) return "—";
  const s = Math.floor((Date.now() - new Date(iso).getTime()) / 1000);
  if (s < 60) return `${s}s ago`;
  if (s < 3600) return `${Math.floor(s / 60)}m ago`;
  if (s < 86400) return `${Math.floor(s / 3600)}h ago`;
  return `${Math.floor(s / 86400)}d ago`;
}
const BADGE = {
  activated: "bg-ok/20 text-ok",
  pending: "bg-warn/20 text-warn",
  payment_required: "bg-warn/20 text-warn",
  revoked: "bg-danger/20 text-danger",
};

export default function Overview() {
  const [d, setD] = useState(null);
  const [err, setErr] = useState(null);
  const portals = usePortals();

  useEffect(() => {
    api.overview().then(setD).catch((e) => setErr(e.message));
  }, []);

  if (err) return <p className="rounded-lg bg-danger/15 px-3 py-2 text-danger">Error: {err}</p>;
  if (!d) {
    return (
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        {Array.from({ length: 8 }).map((_, i) => <div key={i} className="skeleton h-[76px]" />)}
      </div>
    );
  }

  const maxVer = Math.max(1, ...d.perVersion.map((p) => p.count));
  const rate = d.total > 0 ? Math.round((d.activated / d.total) * 100) : 0;

  return (
    <div className="animate-fade-up">
      <h1 className="page-title">Overview</h1>
      <p className="page-sub mb-6">Live status of every 10K Ultra box and the current release.</p>

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label="Total devices" value={d.total} icon={MonitorSmartphone} />
        <StatCard label="Online now" value={d.online} icon={Wifi} accent="#4CC97A" />
        <StatCard label="Activated" value={d.activated} icon={CheckCircle2} accent="#4CC97A" />
        <StatCard label="Pending" value={d.pending} icon={Clock} accent="#E0A458" />
      </div>
      <div className="mt-4 grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label="Revoked" value={d.revoked} icon={ShieldOff} accent="#E06C5E" />
        <StatCard label="Need update" value={d.outdated} icon={AlertTriangle} accent="#E0A458" />
        <StatCard label="Releases" value={d.releases} icon={Layers} />
        <StatCard label="Latest version" value={d.latest?.version_name ?? "—"} icon={PackageOpen} />
      </div>

      <div className="card mt-6">
        <div className="mb-2 flex items-center justify-between">
          <h2 className="font-semibold text-gold-ivory">Activation rate</h2>
          <span className="text-sm font-bold text-gold-primary">{rate}%</span>
        </div>
        <div className="h-3 w-full overflow-hidden rounded-full bg-gold-bg">
          <div
            className="h-full rounded-full bg-gold-sheen transition-[width] duration-700"
            style={{ width: `${rate}%` }}
          />
        </div>
        <p className="mt-2 text-xs text-gold-muted">
          {d.activated} of {d.total} devices activated.
          {d.latest?.force_update ? " · Latest release is a forced update." : ""}
        </p>
      </div>

      <div className="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-2">
        <div className="card">
          <h2 className="mb-4 font-semibold text-gold-ivory">Devices by version</h2>
          {d.perVersion.length === 0 && <p className="text-sm text-gold-muted">No versions published yet.</p>}
          <div className="space-y-3">
            {d.perVersion.map((v) => (
              <div key={v.version_code}>
                <div className="mb-1 flex items-center justify-between text-sm">
                  <span className="text-gold-ivory">
                    {v.version_name} <span className="text-xs text-gold-muted">(code {v.version_code})</span>
                  </span>
                  <span className="text-gold-muted">{v.count}</span>
                </div>
                <div className="h-2 w-full overflow-hidden rounded-full bg-gold-bg">
                  <div className="h-full rounded-full bg-gold-primary/70" style={{ width: `${Math.round((v.count / maxVer) * 100)}%` }} />
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="card">
          <h2 className="mb-4 font-semibold text-gold-ivory">Recent devices</h2>
          {d.recent.length === 0 ? (
            <p className="text-sm text-gold-muted">No devices yet. Boxes appear here on first boot.</p>
          ) : (
            <div className="space-y-2">
              {d.recent.map((x) => (
                <div key={x.device_id} className="flex items-center justify-between rounded-xl border border-gold-border bg-gold-bg px-3 py-2">
                  <div className="min-w-0">
                    <p className="truncate font-mono text-xs text-gold-ivory">{x.device_id.slice(0, 18)}…</p>
                    <p className="truncate text-xs text-gold-muted">
                      {(x.server_url || "not set").replace(/^https?:\/\//, "")} · v{x.app_version ?? "—"}
                    </p>
                  </div>
                  <div className="flex shrink-0 items-center gap-2">
                    <span className="text-xs text-gold-muted">{timeAgo(x.last_seen)}</span>
                    <span className={`badge ${BADGE[x.status] ?? ""}`}>{x.status}</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* The servers boxes can be assigned to — the same three the app ships with. */}
      <div className="card mt-6">
        <h2 className="mb-1 flex items-center gap-2 font-semibold text-gold-ivory">
          <Server size={16} className="text-gold-primary" /> Servers
        </h2>
        <p className="mb-4 text-xs text-gold-muted">
          Assigned to a box on activation. A box picks up a change on its next boot.
        </p>
        <div className="grid gap-3 sm:grid-cols-3">
          {portals.map((p) => (
            <div key={p.url} className="rounded-xl border border-gold-border bg-gold-bg px-3 py-2.5">
              <p className="text-sm font-semibold text-gold-ivory">{p.label}</p>
              <p className="truncate font-mono text-[11px] text-gold-muted">
                {p.url.replace(/^https?:\/\//, "")}
              </p>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
