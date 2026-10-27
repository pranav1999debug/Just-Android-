-- ============================================================================
-- JUSTFAN ANDROID FORCED-UPDATE (VERSION GATE) MIGRATION
-- ============================================================================
-- Scanned Database Objects in the Android Codebase:
--   1. GATED (Android-App-Only Tables):
--      - public.app_secrets
--      - public.upload_requests
--      - public."RequestfromApp"
--      - public.user_devices
--   2. SKIPPED (Views — PostgreSQL does not support RLS policies on views):
--      - public.post_click_counts (view)
--   3. SKIPPED (Website-Shared Tables — kept ungated so website traffic works):
--      - public.posts
--      - public.post_clicks
--      - public.profiles
--      - public.subscriptions
--      - public.users
--      - public.user_preferences
--   4. NEVER GATED (Public Version Config Table):
--      - public.app_config
-- ============================================================================

-- 1. Create app_config table and seed row id = 1 (versionCode = 2)
create table if not exists public.app_config (
  id int primary key default 1,
  min_version int not null,
  latest_version int not null,
  update_url text not null,
  update_message text default 'A new version is required to continue.'
);

insert into public.app_config (id, min_version, latest_version, update_url)
values (1, 2, 2, 'https://github.com/pranav1999debug/Just-Android-/releases/latest/download/app-debug.apk')
on conflict (id) do update set
  min_version = excluded.min_version,
  latest_version = excluded.latest_version,
  update_url = excluded.update_url;

alter table public.app_config enable row level security;

drop policy if exists "public read app_config" on public.app_config;
create policy "public read app_config"
  on public.app_config
  for select
  using (true);

-- 2. Version-check helper function reading the global x-app-version header
create or replace function public.app_version_ok() returns boolean
language sql stable as $$
  select coalesce(
    (current_setting('request.headers', true)::json->>'x-app-version')::int, 0
  ) >= (select min_version from public.app_config where id = 1)
$$;

-- 3. Loop-free RESTRICTIVE version gate policies on Android-only tables
--    (Never applied to app_config, views like post_click_counts, or website tables)

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
