import { useEffect, useState } from "react";
import { Server, Check, Link2 } from "lucide-react";
import { api, FALLBACK_PORTALS } from "../api";

/**
 * Loads the built-in server list once per mount. The backend owns the list
 * (`GET /api/portals`); `api.portals()` already falls back to the local copy.
 */
export function usePortals() {
  const [portals, setPortals] = useState(FALLBACK_PORTALS);
  useEffect(() => {
    let alive = true;
    api.portals().then((p) => alive && setPortals(p));
    return () => { alive = false; };
  }, []);
  return portals;
}

/**
 * Picks which of the built-in servers a device is assigned to.
 *
 * ONLY the three 10K Ultra servers are ever offered. A device row carried over from another
 * product can hold a foreign URL (star.homeip.net and the like); that must NOT show up here as a
 * fourth choice, so it is simply not listed and activating the box moves it onto a real one.
 */
export default function ServerPicker({ value, onChange, label = "Server" }) {
  const options = usePortals();
  const isPreset = options.some((p) => p.url === value);
  // The box's QR lands here, so typing a URL has to be possible without leaving the page.
  const [custom, setCustom] = useState(isPreset ? "" : value || "");

  useEffect(() => {
    if (!isPreset && value) setCustom(value);
  }, [value, isPreset]);

  const applyCustom = (raw) => {
    setCustom(raw);
    const t = raw.trim();
    onChange(t ? (/^https?:\/\//i.test(t) ? t : `http://${t}`) : "");
  };

  return (
    <div>
      <label className="mb-1.5 flex items-center gap-1.5 text-sm text-gold-muted">
        <Server size={14} /> {label}
      </label>
      <div className="grid gap-2 sm:grid-cols-3">
        {options.map((p) => {
          const on = p.url === value;
          return (
            <button
              key={p.url}
              type="button"
              onClick={() => { setCustom(""); onChange(p.url); }}
              className={`relative rounded-xl border px-3 py-2.5 text-left transition-all ${
                on
                  ? "border-gold-primary bg-gold-primary/12 shadow-gold"
                  : "border-gold-border bg-gold-bg hover:border-gold-primary/45"
              }`}
            >
              {on && (
                <Check size={14} className="absolute right-2 top-2 text-gold-primary" />
              )}
              <p className="pr-4 text-sm font-semibold text-gold-ivory">{p.label}</p>
              <p className="truncate font-mono text-[11px] text-gold-muted">
                {p.url.replace(/^https?:\/\//, "")}
              </p>
            </button>
          );
        })}
      </div>

      <div className="mt-3">
        <div className="relative">
          <Link2
            size={15}
            className={`pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 ${
              !isPreset && value ? "text-gold-primary" : "text-gold-muted"
            }`}
          />
          <input
            value={custom}
            onChange={(e) => applyCustom(e.target.value)}
            className={`input pl-9 font-mono text-sm ${
              !isPreset && value ? "border-gold-primary" : ""
            }`}
            placeholder="…or type any portal URL, e.g. http://your-portal.com/c/"
          />
        </div>
        <p className="mt-1 text-[11px] text-gold-muted">
          The box picks this up on its next check — no visit to the customer needed.
        </p>
      </div>
    </div>
  );
}
