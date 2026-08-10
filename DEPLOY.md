# 10K Ultra — going to production

Three pieces: **Supabase** (data), **backend** (Render), **dashboard** (Netlify), plus the **APK**.

---

## 1. Database

In Supabase → SQL Editor, run `dashboard/supabase/schema-10kultra.sql`.

It creates three `tenkultra_`-prefixed tables. The prefix matters: sharing another product's
`devices` / `app_versions` tables mixes customers together and makes that product's releases show
up on 10K Ultra boxes as a forced "Update Required".

The file ends with a commented-out `insert … select` that copies existing boxes over from a shared
`devices` table. Uncomment it only if you are migrating.

---

## 2. Backend (Render)

Root directory `backend/`, build `npm install`, start `npm start`.

Environment — see `backend/.env.example` for the full list. The server **refuses to start** if
`AUTH_SECRET` is missing/short or `ALLOWED_ORIGINS` is unset in production; that is deliberate,
those two are what stop anyone from minting their own admin session.

```
SUPABASE_URL=…
SUPABASE_SERVICE_ROLE_KEY=…            # service_role, never the anon key
AUTH_SECRET=<48 random bytes>          # node -e "console.log(require('crypto').randomBytes(48).toString('base64url'))"
ALLOWED_ORIGINS=https://<your-dashboard>.netlify.app
NODE_ENV=production
DEVICES_TABLE=tenkultra_devices
VERSIONS_TABLE=tenkultra_app_versions
RESELLERS_TABLE=tenkultra_resellers
OTA_ENABLED=false                      # true once you publish 10K Ultra builds
ADMIN_EMAIL=you@yourdomain.com         # first run only
ADMIN_PASSWORD=<strong password>       # first run only — delete both after the admin exists
```

On first boot with an empty `tenkultra_resellers` the server seeds that one admin, then ignores
those two variables forever. Remove them from Render once you have signed in.

---

## 3. Dashboard (Netlify)

Base directory `dashboard/`, build `npm run build`, publish `dist`.
Environment: `VITE_API_URL=https://<your-backend>.onrender.com`.

Then set `ALLOWED_ORIGINS` on the backend to the Netlify URL and redeploy it.

### How client isolation works

* Sign in as the admin → **Clients** → add an account for each customer.
* A reseller sees **only** the devices they activated. Overview totals, the device list, activation
  lookups and every write are filtered by their account id server-side — not hidden in the UI.
* A box that has never been activated has no owner. Any reseller holding its pairing code can claim
  it, and activating **stamps it as theirs**. From then on nobody else can see or touch it.
* Publishing releases and managing clients are admin-only.

---

## 4. App

`RemoteConfig.kt` holds the two production URLs — update them to the real Render/Netlify addresses
before cutting a release:

```kotlin
BACKEND_BASE   = "https://tenkultra-backend.onrender.com/"
DASHBOARD_BASE = "https://10kultra-dashboard.netlify.app/"
```

Debug builds automatically point at `10.0.2.2` (the emulator's route to this machine), so local
testing needs no edits.

Release build (signed with `tenkultra-release.jks`, R8 + resource shrinking on):

```
cd android-app
./gradlew.bat :app:assembleRelease --offline
# app/build/outputs/apk/release/app-release.apk
```

Bump `versionCode` **and** `versionName` in `app/build.gradle.kts` for every release, upload the APK
somewhere public, then add it under **App Versions** and flip `OTA_ENABLED=true`.

### Test-only build flags

```
-PforcedPortal=http://star.homeip.net    # pin one server, skip activation entirely
-PforcedMac=DA:7A:69:4E:DE:E0            # pin an already-whitelisted MAC
```

Both are empty in a normal build. **Never ship an APK built with either** — a forced MAC makes every
box identical, which defeats the anti-clone guarantee.

---

## Before you ship — checklist

- [ ] `schema-10kultra.sql` run; backend pointed at the `tenkultra_` tables
- [ ] `AUTH_SECRET` random and secret; `ALLOWED_ORIGINS` set; `NODE_ENV=production`
- [ ] `ADMIN_EMAIL` / `ADMIN_PASSWORD` removed from Render after first sign-in
- [ ] `backend/.env` is **not** committed (it is in `.gitignore`)
- [ ] `RemoteConfig.kt` URLs point at the live backend + dashboard
- [ ] Release APK built without `-PforcedPortal` / `-PforcedMac`
- [ ] Keystore `tenkultra-release.jks` backed up somewhere safe — losing it means no future update
      can ever install over an existing app
