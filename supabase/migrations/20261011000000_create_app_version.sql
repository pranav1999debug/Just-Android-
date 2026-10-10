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

create index if not exists app_version_version_idx
    on public.app_version (created_at desc);

insert into public.app_version (version, download_link)
select '1.0.0', 'https://github.com/pranav1999debug/Just-Android-/releases/download/debug-apk-build-20-1/app-debug.apk'
where not exists (select 1 from public.app_version);
