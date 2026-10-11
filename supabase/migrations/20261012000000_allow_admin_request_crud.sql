-- The app uses the real Supabase Auth JWT for request CRUD.
-- Replace the email below if the administrator account changes.
alter table if exists public."RequestfromApp" enable row level security;

drop policy if exists "Admin can read requests" on public."RequestfromApp";
create policy "Admin can read requests"
    on public."RequestfromApp"
    for select
    to authenticated
    using ((auth.jwt() ->> 'email') = 'reytherapper12@gmail.com');

drop policy if exists "Admin can update requests" on public."RequestfromApp";
create policy "Admin can update requests"
    on public."RequestfromApp"
    for update
    to authenticated
    using ((auth.jwt() ->> 'email') = 'reytherapper12@gmail.com')
    with check ((auth.jwt() ->> 'email') = 'reytherapper12@gmail.com');

drop policy if exists "Admin can delete requests" on public."RequestfromApp";
create policy "Admin can delete requests"
    on public."RequestfromApp"
    for delete
    to authenticated
    using ((auth.jwt() ->> 'email') = 'reytherapper12@gmail.com');
