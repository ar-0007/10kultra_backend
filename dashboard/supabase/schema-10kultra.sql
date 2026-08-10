-- ============================================================
-- 10K Ultra — production schema
-- Run in Supabase: Dashboard -> SQL Editor -> New query -> paste -> Run
--
-- Tables are PREFIXED so 10K Ultra can share a Supabase project with another product without
-- their devices or release feeds ever mixing. Point the backend at them with:
--   DEVICES_TABLE=tenkultra_devices
--   VERSIONS_TABLE=tenkultra_app_versions
--   RESELLERS_TABLE=tenkultra_resellers
-- ============================================================

-- ---------- resellers : who can sign in to the dashboard ----------
-- Every device belongs to exactly one reseller. A reseller only ever sees their own devices;
-- 'admin' sees everything. Passwords are scrypt hashes produced by the backend — never plaintext.
create table if not exists public.tenkultra_resellers (
  id            uuid primary key default gen_random_uuid(),
  email         text unique not null,
  password_hash text not null,
  name          text,
  role          text not null default 'reseller',   -- admin | reseller
  active        boolean not null default true,      -- false = cannot sign in
  created_at    timestamptz not null default now()
);
create index if not exists tenkultra_resellers_email_idx on public.tenkultra_resellers (lower(email));

-- ---------- app_versions : OTA updates (10K Ultra's OWN release feed) ----------
create table if not exists public.tenkultra_app_versions (
  id            uuid primary key default gen_random_uuid(),
  version_code  integer not null,                 -- must increase each release
  version_name  text    not null,                 -- e.g. "1.4.0"
  apk_url       text    not null,                 -- where the APK is hosted (Storage/CDN)
  apk_size      bigint,
  changelog     text,
  force_update  boolean not null default false,   -- true = block app until updated
  is_published  boolean not null default false,   -- only published versions are offered
  created_at    timestamptz not null default now()
);
create index if not exists tenkultra_versions_code_idx
  on public.tenkultra_app_versions (version_code desc);

-- ---------- devices : every installed box ----------
create table if not exists public.tenkultra_devices (
  id             uuid primary key default gen_random_uuid(),
  device_id      text unique not null,            -- app-generated stable id (derived from the MAC)
  pairing_code   text unique,                     -- short code shown in the QR
  status         text not null default 'pending', -- pending | activated | revoked
  server_url     text,                            -- portal URL assigned from the dashboard
  mac            text,                            -- MAC (auto-set from the device on register)
  app_version    integer,                         -- last reported version_code
  model          text,
  sig_sha256     text,                            -- app signing cert hash (anti-clone)
  customer_name  text,
  payment_status text not null default 'unpaid',  -- unpaid | paid | expired (gates the app)
  payment_expiry timestamptz,
  notes          text,
  -- WHO OWNS THIS BOX. NULL = not claimed yet: any reseller holding its pairing code can activate
  -- it, and that activation stamps their id here. After that only they (and admins) can see it.
  reseller_id    uuid references public.tenkultra_resellers (id) on delete set null,
  activated_at   timestamptz,
  last_seen      timestamptz,
  created_at     timestamptz not null default now()
);
create index if not exists tenkultra_devices_status_idx   on public.tenkultra_devices (status);
create index if not exists tenkultra_devices_pairing_idx  on public.tenkultra_devices (pairing_code);
create index if not exists tenkultra_devices_reseller_idx on public.tenkultra_devices (reseller_id);

-- ---------- Row Level Security ----------
-- The backend talks to Postgres with the service_role key, which bypasses RLS, and does the
-- per-reseller filtering itself on every query. RLS is still enabled with NO permissive policies
-- so that a leaked anon/publishable key cannot read any of this directly.
alter table public.tenkultra_resellers    enable row level security;
alter table public.tenkultra_devices      enable row level security;
alter table public.tenkultra_app_versions enable row level security;

-- ---------- Migrating from the shared `devices` table (optional) ----------
-- Copies the existing 10K Ultra boxes across. Safe to re-run: existing device_ids are skipped.
-- insert into public.tenkultra_devices
--   (device_id, pairing_code, status, server_url, mac, app_version, model, sig_sha256,
--    customer_name, payment_status, activated_at, last_seen, created_at)
-- select device_id, pairing_code, status, server_url, mac, app_version, model, sig_sha256,
--        customer_name, payment_status, activated_at, last_seen, created_at
-- from public.devices
-- on conflict (device_id) do nothing;
