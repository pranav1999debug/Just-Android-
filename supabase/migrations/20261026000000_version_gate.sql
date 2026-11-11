-- ============================================================================
-- JUSTFAN ANDROID FORCED-UPDATE (VERSION GATE v3.0) MIGRATION
-- ============================================================================
-- Blocks all previous app versions (< 3) from accessing ANY database content
-- by applying a RESTRICTIVE RLS policy (`public.app_version_ok()`) across all
-- content tables while keeping `public.app_config` and `public.app_version`
-- publicly readable so older versions can check the database version and
-- download the latest APK.
--
--   1. GATED (All Content & App Tables — require x-app-version >= min_version):
--      - public.posts
--      - public.post_clicks
--      - public.profiles
--      - public.subscriptions
--      - public.users
--      - public.user_preferences
--      - public.app_secrets
--      - public.upload_requests
--      - public."RequestfromApp"
--      - public.user_devices
--   2. SKIPPED (Views — PostgreSQL does not support RLS policies on views):
--      - public.post_click_counts (view)
--   3. NEVER GATED (Public Version Config Tables):
--      - public.app_config
--      - public.app_version
-- ============================================================================

-- 1. Create app_config table and set min_version = 3, latest_version = 3 (v3.0)
create table if not exists public.app_config (
  id int primary key default 1,
  min_version int not null,
  latest_version int not null,
  update_url text not null,
  update_message text default 'A new version (v3.0) is required to access JustFan database contents. Please download and install the latest update.'
);

insert into public.app_config (id, min_version, latest_version, update_url, update_message)
values (
  1,
  3,
  3,
  'https://github.com/pranav1999debug/Just-Android-/releases/latest/download/app-debug.apk',
  'A new version (v3.0) is required to access JustFan database contents. Please download and install the latest update.'
)
on conflict (id) do update set
  min_version = excluded.min_version,
  latest_version = excluded.latest_version,
  update_url = excluded.update_url,
  update_message = excluded.update_message;

alter table public.app_config enable row level security;

drop policy if exists "public read app_config" on public.app_config;
create policy "public read app_config"
  on public.app_config
  for select
  using (true);

drop policy if exists "allow update app_config" on public.app_config;
create policy "allow update app_config"
  on public.app_config
  for all
  to anon, authenticated
  using (true)
  with check (true);

-- Also keep public.app_version synced with v3.0
create table if not exists public.app_version (
  uid uuid primary key default gen_random_uuid(),
  version text not null,
  download_link text not null,
  created_at timestamptz not null default now()
);

alter table public.app_version enable row level security;

drop policy if exists "Anyone can read app versions" on public.app_version;
create policy "Anyone can read app versions"
  on public.app_version
  for select
  to anon, authenticated
  using (true);

drop policy if exists "Allow insert app versions" on public.app_version;
create policy "Allow insert app versions"
  on public.app_version
  for all
  to anon, authenticated
  using (true)
  with check (true);

insert into public.app_version (version, download_link)
values (
  '3.0',
  'https://github.com/pranav1999debug/Just-Android-/releases/latest/download/app-debug.apk'
);

-- 2. Version-check helper function reading the global x-app-version header
create or replace function public.app_version_ok() returns boolean
language sql stable as $$
  select coalesce(
    (current_setting('request.headers', true)::json->>'x-app-version')::int, 0
  ) >= (select min_version from public.app_config where id = 1)
$$;

-- 3. Loop-free RESTRICTIVE version gate policies on ALL content tables
--    (Blocks any client with x-app-version < 3 from reading or writing data)

alter table if exists public.posts enable row level security;
drop policy if exists "version gate" on public.posts;
create policy "version gate"
  on public.posts
  as restrictive
  for all
  using (public.app_version_ok());

alter table if exists public.post_clicks enable row level security;
drop policy if exists "version gate" on public.post_clicks;
create policy "version gate"
  on public.post_clicks
  as restrictive
  for all
  using (public.app_version_ok());

alter table if exists public.profiles enable row level security;
drop policy if exists "version gate" on public.profiles;
create policy "version gate"
  on public.profiles
  as restrictive
  for all
  using (public.app_version_ok());

alter table if exists public.subscriptions enable row level security;
drop policy if exists "version gate" on public.subscriptions;
create policy "version gate"
  on public.subscriptions
  as restrictive
  for all
  using (public.app_version_ok());

alter table if exists public.users enable row level security;
drop policy if exists "version gate" on public.users;
create policy "version gate"
  on public.users
  as restrictive
  for all
  using (public.app_version_ok());

alter table if exists public.user_preferences enable row level security;
drop policy if exists "version gate" on public.user_preferences;
create policy "version gate"
  on public.user_preferences
  as restrictive
  for all
  using (public.app_version_ok());

alter table if exists public.app_secrets enable row level security;
drop policy if exists "version gate" on public.app_secrets;
create policy "version gate"
  on public.app_secrets
  as restrictive
  for all
  using (public.app_version_ok());

alter table if exists public.upload_requests enable row level security;
drop policy if exists "version gate" on public.upload_requests;
create policy "version gate"
  on public.upload_requests
  as restrictive
  for all
  using (public.app_version_ok());

alter table if exists public."RequestfromApp" enable row level security;
drop policy if exists "version gate" on public."RequestfromApp";
create policy "version gate"
  on public."RequestfromApp"
  as restrictive
  for all
  using (public.app_version_ok());

alter table if exists public.user_devices enable row level security;
drop policy if exists "version gate" on public.user_devices;
create policy "version gate"
  on public.user_devices
  as restrictive
  for all
  using (public.app_version_ok());
