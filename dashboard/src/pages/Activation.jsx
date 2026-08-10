import { useState, useEffect } from "react";
import { useSearchParams } from "react-router-dom";
import { Search, CheckCircle2, QrCode } from "lucide-react";
import { api, FALLBACK_PORTALS } from "../api";
import ServerPicker from "../components/ServerPicker";

export default function Activation() {
  const [params] = useSearchParams();
  const [code, setCode] = useState((params.get("code") || "").toUpperCase());
  const [device, setDevice] = useState(null);
  const [customer, setCustomer] = useState("");
  const [payment, setPayment] = useState("paid");
  const [server, setServer] = useState(FALLBACK_PORTALS[0].url);
  const [done, setDone] = useState(false);
  const [err, setErr] = useState(null);
  const [busy, setBusy] = useState(false);

  async function lookup() {
    setErr(null); setDone(false); setBusy(true);
    try {
      const { device } = await api.findByCode(code);
      if (!device) setErr("No device found for that code.");
      setDevice(device);
      setCustomer(device?.customer_name || "");
      setPayment(device?.payment_status === "unpaid" ? "paid" : (device?.payment_status || "paid"));
      // Only ever preselect one of OUR three servers (see Devices.jsx for why).
      setServer(
        FALLBACK_PORTALS.find((p) => p.url === device?.server_url)?.url ?? FALLBACK_PORTALS[0].url
      );
    } catch (e) { setErr(e.message); }
    finally { setBusy(false); }
  }
  // Auto-lookup if a code came in via the QR link (?code=)
  useEffect(() => { if (code) lookup(); /* eslint-disable-next-line */ }, []);

  async function activate() {
    setBusy(true); setErr(null);
    try {
      await api.activateByCode(code, {
        customer_name: customer,
        payment_status: payment,
        server_url: server,
      });
      setDone(true); setDevice(null);
    } catch (e) { setErr(e.message); }
    finally { setBusy(false); }
  }

  return (
    <div className="animate-fade-up">
      <h1 className="page-title">Activation</h1>
      <p className="page-sub mb-6">
        Scan the box’s QR (or type its code), pick the server it should use, then activate —
        the box connects on its own within seconds.
      </p>

      <div className="card max-w-2xl space-y-5">
        <div>
          <label className="mb-1.5 flex items-center gap-1.5 text-sm text-gold-muted">
            <QrCode size={14} /> Pairing code (from the box / QR)
          </label>
          <div className="flex gap-2">
            <input
              value={code}
              onChange={(e) => setCode(e.target.value.toUpperCase())}
              onKeyDown={(e) => e.key === "Enter" && code && lookup()}
              className="input font-mono text-lg tracking-[0.35em]"
              placeholder="ABC123"
            />
            <button onClick={lookup} disabled={busy || !code} className="btn-primary shrink-0">
              <Search size={16} /> Find
            </button>
          </div>
        </div>

        {err && <p className="rounded-lg bg-danger/15 px-3 py-2 text-sm text-danger">{err}</p>}
        {done && (
          <div className="flex items-center gap-2 rounded-xl bg-ok/15 px-3 py-3 text-ok">
            <CheckCircle2 size={18} /> Activated — the box will connect automatically.
          </div>
        )}

        {device && !done && (
          <div className="space-y-4 border-t border-gold-border pt-4">
            {/* Read-only — captured automatically when the box registered. No typing. */}
            <div className="space-y-1.5 rounded-xl bg-gold-bg/50 px-4 py-3 text-sm">
              <Row label="Device" value={`${device.device_id.slice(0, 18)}…`} mono />
              <Row label="MAC" value={device.mac || "auto-generated"} mono />
              <div className="flex items-center justify-between">
                <span className="text-gold-muted">Status</span>
                <span className="badge bg-warn/20 text-warn">{device.status}</span>
              </div>
            </div>

            <ServerPicker value={server} onChange={setServer} label="Assign server" />

            <div className="grid gap-4 sm:grid-cols-2">
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
            </div>

            <button onClick={activate} disabled={busy} className="btn-primary w-full">
              {busy ? "Activating…" : "Activate device"}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

function Row({ label, value, mono }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <span className="text-gold-muted">{label}</span>
      <span className={`text-gold-ivory ${mono ? "font-mono text-xs" : ""} truncate`}>{value}</span>
    </div>
  );
}
