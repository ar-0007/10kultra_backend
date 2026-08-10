// All calls go to the backend (Render). Configure via VITE_API_URL (Netlify env).
const BASE = (import.meta.env.VITE_API_URL || "http://localhost:4000").replace(/\/$/, "");

const getToken = () => localStorage.getItem("tenkultra_token") || "";
export const isAuthed = () => !!getToken();
export const logout = () => {
  localStorage.removeItem("tenkultra_token");
  localStorage.removeItem("tenkultra_user");
};

/** Remembers who is signed in so the sidebar can label the account and hide admin-only pages. */
export const setSession = (token, user) => {
  localStorage.setItem("tenkultra_token", token);
  localStorage.setItem("tenkultra_user", JSON.stringify(user || {}));
};
export const currentUser = () => {
  try {
    return JSON.parse(localStorage.getItem("tenkultra_user") || "{}");
  } catch {
    return {};
  }
};
export const isAdmin = () => currentUser().role === "admin";

/**
 * The servers 10K Ultra ships with. The backend is the source of truth (`GET /api/portals`) and
 * the app hardcodes the same three; this copy is only the fallback for an offline/old backend so
 * the picker is never empty.
 *
 * Mega 4K points at steel4k.cc on purpose: mega4k.cc serves no Stalker API itself, it
 * root-redirects there, and the box probes paths on the host it is given without hopping hosts.
 */
export const FALLBACK_PORTALS = [
  { label: "A1 TV", url: "http://tv.a1tv.ac" },
  { label: "Elite 4K", url: "http://portal.elite4k.co" },
  { label: "Mega 4K", url: "http://steel4k.cc" },
];

async function req(path, opts = {}) {
  const res = await fetch(BASE + path, {
    ...opts,
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${getToken()}`,
      ...(opts.headers || {}),
    },
  });
  if (res.status === 401) {
    logout();
    if (location.pathname !== "/login") location.href = "/login";
    throw new Error("unauthorized");
  }
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || `HTTP ${res.status}`);
  return data;
}

export const api = {
  base: BASE,
  login: (email, password) =>
    req("/api/admin/login", { method: "POST", body: JSON.stringify({ email, password }) }),
  overview: () => req("/api/admin/overview"),
  versions: () => req("/api/admin/versions"),
  createVersion: (v) => req("/api/admin/versions", { method: "POST", body: JSON.stringify(v) }),
  publishVersion: (id, publish) =>
    req(`/api/admin/versions/${id}/publish`, { method: "POST", body: JSON.stringify({ publish }) }),
  deleteVersion: (id) => req(`/api/admin/versions/${id}`, { method: "DELETE" }),
  devices: () => req("/api/admin/devices"),
  /** The built-in server list; falls back to the local copy if the backend predates the route. */
  portals: () =>
    req("/api/portals")
      .then((d) => (Array.isArray(d.portals) && d.portals.length ? d.portals : FALLBACK_PORTALS))
      .catch(() => FALLBACK_PORTALS),
  // MAC is stored on register. Activation assigns the server and (optionally) a customer + payment.
  activateDevice: (id, { customer_name = "", payment_status = "paid", server_url } = {}) =>
    req(`/api/admin/devices/${id}/activate`, {
      method: "POST", body: JSON.stringify({ customer_name, payment_status, server_url }),
    }),
  /** Re-point an already-activated box at a different server. */
  setDeviceServer: (id, server_url) =>
    req(`/api/admin/devices/${id}/server`, {
      method: "POST", body: JSON.stringify({ server_url }),
    }),
  updatePayment: (id, payment_status) =>
    req(`/api/admin/devices/${id}/payment`, {
      method: "POST", body: JSON.stringify({ payment_status }),
    }),
  revokeDevice: (id) => req(`/api/admin/devices/${id}/revoke`, { method: "POST" }),
  me: () => req("/api/admin/me"),
  // Reseller accounts (admin only) — this is how a NEW client gets their own isolated dashboard.
  resellers: () => req("/api/admin/resellers"),
  createReseller: (body) =>
    req("/api/admin/resellers", { method: "POST", body: JSON.stringify(body) }),
  setResellerActive: (id, active) =>
    req(`/api/admin/resellers/${id}/active`, { method: "POST", body: JSON.stringify({ active }) }),
  setResellerPassword: (id, password) =>
    req(`/api/admin/resellers/${id}/password`, { method: "POST", body: JSON.stringify({ password }) }),
  findByCode: (code) => req(`/api/admin/by-code/${encodeURIComponent(code)}`),
  activateByCode: (code, { customer_name = "", payment_status = "paid", server_url } = {}) =>
    req("/api/admin/activate", {
      method: "POST", body: JSON.stringify({ code, customer_name, payment_status, server_url }),
    }),
};
