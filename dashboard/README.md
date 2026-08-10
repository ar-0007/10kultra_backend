# 10K Ultra — Dashboard (Vite + React SPA)

Admin control panel for OTA updates, device activation (QR) and licensing.
A pure static SPA that talks to the **backend** (Render). Deploys on **Netlify**.

## Run locally
```bash
cd dashboard
npm install
echo "VITE_API_URL=http://localhost:4000" > .env   # point at the backend
npm run dev        # http://localhost:5173
```

## Deploy on Netlify
1. New site → connect this repo.
2. Build command: `npm run build` · Publish directory: `dist` (already in `netlify.toml`).
3. **Environment variable:** `VITE_API_URL = https://<your-backend>.onrender.com`
4. Deploy. (SPA redirect to `index.html` is configured.)

## Pages
- `/login` — admin sign-in (calls the backend)
- `/overview` — device + release stats
- `/versions` — publish a signed APK build
- `/devices` — activate / revoke boxes
- `/activation` — set a box's portal + MAC by pairing code (opens from the QR `?code=`)

No secrets live here — the service-role key stays on the backend only.
