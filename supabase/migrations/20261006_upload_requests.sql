-- Migration: Add smart media routing and social posting columns to upload_requests
-- and seed app_download_url into app_secrets

-- 1. Add columns to upload_requests
alter table upload_requests
  add column if not exists media_type text default 'image',
  add column if not exists title text,
  add column if not exists description text,
  add column if not exists share_to_social boolean default false,
  add column if not exists buffer_channel_id text;

-- 2. Store the app download URL constant in app_secrets
insert into app_secrets (name, value)
values ('app_download_url', 'https://github.com/pranav1999debug/Just-Android-/releases/latest/download/app-debug.apk')
on conflict (name) do update set value = excluded.value;

-- 3. Enable read access for app client on upload_requests (while keeping write restricted to Edge Functions)
create policy if not exists "Allow public read access to upload_requests"
on upload_requests
for select
to authenticated, anon
using (true);
