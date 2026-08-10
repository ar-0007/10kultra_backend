import { useEffect, useState } from "react";
import { UploadCloud } from "lucide-react";
import { api } from "../api";

export default function Versions() {
  const [versions, setVersions] = useState([]);
  const [form, setForm] = useState({ version_code: "", version_name: "", apk_url: "", changelog: "", is_published: true, force_update: false });
  const [msg, setMsg] = useState(null);
  const [saving, setSaving] = useState(false);

  const load = () => api.versions().then((r) => setVersions(r.versions)).catch(() => {});
  useEffect(() => { load(); }, []);

  async function submit(e) {
    e.preventDefault();
    setSaving(true); setMsg(null);
    try {
      await api.createVersion({ ...form, version_code: Number(form.version_code) });
      setMsg("Published ✓");
      setForm({ version_code: "", version_name: "", apk_url: "", changelog: "", is_published: true, force_update: false });
      load();
    } catch (err) { setMsg("Error: " + err.message); }
    finally { setSaving(false); }
  }
  const upd = (k) => (e) => setForm({ ...form, [k]: e.target.type === "checkbox" ? e.target.checked : e.target.value });

  return (
    <div className="animate-fade-up">
      <h1 className="page-title">App Versions</h1>
      <p className="page-sub mb-6">Publish a build and every box gets an in-app “Update available”.</p>
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <form onSubmit={submit} className="card space-y-4">
          <h2 className="flex items-center gap-2 font-semibold text-gold-ivory">
            <UploadCloud size={18} className="text-gold-primary" /> Publish a new version
          </h2>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div>
              <label className="mb-1 block text-sm text-gold-muted">Version code *</label>
              <input type="number" required value={form.version_code} onChange={upd("version_code")} className="input" placeholder="14" />
            </div>
            <div>
              <label className="mb-1 block text-sm text-gold-muted">Version name *</label>
              <input required value={form.version_name} onChange={upd("version_name")} className="input" placeholder="1.4.0" />
            </div>
          </div>
          <div>
            <label className="mb-1 block text-sm text-gold-muted">APK URL *</label>
            <input required value={form.apk_url} onChange={upd("apk_url")} className="input" placeholder="https://cdn.example.com/10kultra-1.4.0.apk" />
            <p className="mt-1 text-xs text-gold-muted">Signed release APK (same keystore). Host on a CDN (Cloudflare R2) for scale.</p>
          </div>
          <div>
            <label className="mb-1 block text-sm text-gold-muted">Changelog</label>
            <textarea rows={3} value={form.changelog} onChange={upd("changelog")} className="input" placeholder="• Fixed playback&#10;• New layout" />
          </div>
          <div className="flex items-center gap-6">
            <label className="flex items-center gap-2 text-sm text-gold-ivory">
              <input type="checkbox" checked={form.is_published} onChange={upd("is_published")} /> Publish now
            </label>
            <label className="flex items-center gap-2 text-sm text-gold-ivory">
              <input type="checkbox" checked={form.force_update} onChange={upd("force_update")} /> Force update
            </label>
          </div>
          {msg && <p className="text-sm text-gold-primary">{msg}</p>}
          <button type="submit" disabled={saving} className="btn-primary">{saving ? "Saving…" : "Publish version"}</button>
        </form>

        <div className="card">
          <h2 className="mb-3 font-semibold text-gold-ivory">Releases</h2>
          <div className="space-y-2">
            {versions.length === 0 && <p className="text-sm text-gold-muted">No versions yet.</p>}
            {versions.map((v) => (
              <div key={v.id} className="flex items-center justify-between rounded-lg border border-gold-border bg-gold-bg px-3 py-2.5">
                <div>
                  <p className="font-semibold text-gold-ivory">{v.version_name} <span className="text-xs text-gold-muted">(code {v.version_code})</span></p>
                  {v.changelog && <p className="line-clamp-1 text-xs text-gold-muted">{v.changelog}</p>}
                </div>
                <div className="flex items-center gap-2">
                  {v.force_update && <span className="badge bg-warn/20 text-warn">Force</span>}
                  <button
                    onClick={() => api.publishVersion(v.id, !v.is_published).then(load)}
                    className={`badge ${v.is_published ? "bg-ok/20 text-ok" : "bg-gold-card2 text-gold-muted"}`}
                  >
                    {v.is_published ? "Published" : "Draft"}
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
