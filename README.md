# 10K Ultra

Android TV IPTV app plus the licensing backend and reseller dashboard behind it. Every box is
activated from the dashboard — the portal URL is assigned there, never typed on the TV — and can be
revoked or marked unpaid at any time.

```
android-app/   Android TV + mobile app (Kotlin, Compose, Media3)   → APK
backend/       Express API: device register/config, OTA, QR        → Render
dashboard/     Vite + React admin SPA                              → Netlify
dashboard/supabase/schema-10kultra.sql                             → Supabase
brand/         logo
```

**Deployment, environment variables and the pre-ship checklist live in [DEPLOY.md](DEPLOY.md).**

## Why the `tenkultra_` table prefix

10K Ultra shares a Supabase project with another product. Its three tables are prefixed so the two
never mix, and the backend is pointed at them with `DEVICES_TABLE` / `VERSIONS_TABLE` /
`RESELLERS_TABLE`. **Leaving those unset is a real outage**: the server falls back to the shared
`devices` / `app_versions` tables, which puts the other product's boxes in this dashboard and pushes
its releases to 10K Ultra as a forced "Update Required".

## Local development

```bash
# backend  — copy backend/.env.example to backend/.env and fill it in
cd backend && npm install && npm run dev          # :4000

# dashboard — copy dashboard/.env.example to dashboard/.env
cd dashboard && npm install && npm run dev        # :5173

# app — debug builds point at 10.0.2.2 automatically (the emulator's route to this machine)
cd android-app && ./gradlew.bat :app:assembleDebug
```

## Secrets

`*.env`, `android-app/keystore.properties` and `*.jks` are gitignored and must stay that way. The
release keystore is the one thing that cannot be regenerated — lose it and no future build can ever
install over an installed 10K Ultra app. Keep a copy off this machine.
