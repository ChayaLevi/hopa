-- Schema v2: real accounts, per-device household key wraps, admin console
-- sessions and unauthenticated pending pairings.
--
-- V1's posture is unchanged: every tenant table still carries household_id,
-- RLS is enabled and forced, and a missing setting denies. The three new global
-- tables (accounts, admins, admin_sessions) are read only by the auth and admin
-- paths, so they carry no tenant policy. Optional admin lookups into tenant rows
-- live in db/admin/V5__admin_lookups.sql, applied through a separate history
-- only where a superuser can provision its BYPASSRLS owner. The core schema
-- never depends on it, so a managed Postgres without superuser access can run
-- everything else with HOPA_ADMIN_LOOKUPS=false.

create table accounts (
    id uuid primary key,
    phone text not null,
    password_hash text not null,
    verified_at timestamptz,
    created_at timestamptz not null default now(),
    deleted_at timestamptz
);
-- A tombstoned row keeps its phone, but the unique index only covers live rows;
-- the sign-up path still refuses a phone that appears in a deleted row.
create unique index accounts_phone_idx on accounts (phone) where deleted_at is null;

create table household_key_wraps (
    household_id uuid not null references households (id) on delete cascade,
    device_id uuid not null references devices (id) on delete cascade,
    wrapped_key bytea not null,
    wrapped_at timestamptz not null default now(),
    primary key (device_id)
);
-- device_id is the primary key, so it is already indexed.
create index household_key_wraps_household_idx on household_key_wraps (household_id);

create table admins (
    id uuid primary key,
    email text not null unique,
    password_hash text not null,
    role text not null default 'admin',
    created_at timestamptz not null default now()
);

create table admin_sessions (
    id uuid primary key,
    admin_id uuid not null references admins (id) on delete cascade,
    token_hash bytea not null unique,
    expires_at timestamptz not null,
    created_at timestamptz not null default now()
);
-- token_hash is unique, so the single-token lookup is already indexed.

create table pending_pairings (
    id uuid primary key,
    code_hash bytea not null unique,
    platform text not null check (platform in ('android', 'windows')),
    name text not null,
    public_key bytea not null,
    alg text not null,
    household_id uuid null references households (id) on delete cascade,
    member_id uuid null references members (id) on delete cascade,
    vouched_at timestamptz,
    expires_at timestamptz not null,
    claimed_at timestamptz,
    created_at timestamptz not null default now()
);
create index pending_pairings_expires_idx on pending_pairings (expires_at);

alter table members add column account_id uuid null references accounts (id);
create index members_account_idx on members (account_id);

-- Server-authored diagnostic tasks have no originating device, and every task
-- carries the envelope type it will be delivered as.
alter table tasks alter column from_device drop not null;
alter table tasks add column type text not null default 'task.create';

alter table household_key_wraps enable row level security;
alter table household_key_wraps force row level security;
create policy household_key_wraps_own on household_key_wraps
    using (household_id = nullif(current_setting('hopa.household_id', true), '')::uuid)
    with check (household_id = nullif(current_setting('hopa.household_id', true), '')::uuid);

-- A pending pairing is written before any household exists (the PC mint holds
-- only the secret's hash), then bound to a household by an authenticated vouch.
-- Both callers therefore need their own branch, exactly like pairing_sessions.
alter table pending_pairings enable row level security;
alter table pending_pairings force row level security;
create policy pending_pairings_presented_select on pending_pairings for select
    using (code_hash = decode(nullif(current_setting('hopa.token_hash', true), ''), 'hex'));
create policy pending_pairings_tenant_select on pending_pairings for select
    using (household_id = nullif(current_setting('hopa.household_id', true), '')::uuid);
create policy pending_pairings_presented_insert on pending_pairings for insert
    with check (code_hash = decode(nullif(current_setting('hopa.token_hash', true), ''), 'hex'));
create policy pending_pairings_tenant_insert on pending_pairings for insert
    with check (household_id = nullif(current_setting('hopa.household_id', true), '')::uuid);
create policy pending_pairings_presented_update on pending_pairings for update
    using (code_hash = decode(nullif(current_setting('hopa.token_hash', true), ''), 'hex'))
    with check (code_hash = decode(nullif(current_setting('hopa.token_hash', true), ''), 'hex'));
create policy pending_pairings_tenant_update on pending_pairings for update
    using (household_id = nullif(current_setting('hopa.household_id', true), '')::uuid)
    with check (household_id = nullif(current_setting('hopa.household_id', true), '')::uuid);

