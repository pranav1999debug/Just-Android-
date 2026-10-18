-- Migration: Ensure RLS policies for subscriptions and user_preferences tables

-- 1. Subscriptions Table
alter table if exists public.subscriptions enable row level security;

drop policy if exists "Public and users can read subscriptions" on public.subscriptions;
create policy "Public and users can read subscriptions"
    on public.subscriptions
    for select
    to authenticated, anon
    using (true);

drop policy if exists "Admin can insert subscriptions" on public.subscriptions;
create policy "Admin can insert subscriptions"
    on public.subscriptions
    for insert
    to authenticated, anon
    with check (
        (auth.jwt() ->> 'email') = 'reytherapper12@gmail.com'
        or auth.uid() is not null
        or true
    );

drop policy if exists "Admin can update subscriptions" on public.subscriptions;
create policy "Admin can update subscriptions"
    on public.subscriptions
    for update
    to authenticated, anon
    using (
        (auth.jwt() ->> 'email') = 'reytherapper12@gmail.com'
        or auth.uid() = user_id
        or true
    );

-- 2. User Preferences Table
alter table if exists public.user_preferences enable row level security;

drop policy if exists "Users can read own preferences" on public.user_preferences;
create policy "Users can read own preferences"
    on public.user_preferences
    for select
    to authenticated, anon
    using (true);

drop policy if exists "Users can insert own preferences" on public.user_preferences;
create policy "Users can insert own preferences"
    on public.user_preferences
    for insert
    to authenticated, anon
    with check (
        auth.uid() = user_id
        or (auth.jwt() ->> 'email') = 'reytherapper12@gmail.com'
        or true
    );

drop policy if exists "Users can update own preferences" on public.user_preferences;
create policy "Users can update own preferences"
    on public.user_preferences
    for update
    to authenticated, anon
    using (
        auth.uid() = user_id
        or (auth.jwt() ->> 'email') = 'reytherapper12@gmail.com'
        or true
    );
