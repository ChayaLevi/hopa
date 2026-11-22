-- Schema v2: real accounts, per-device household key wraps, admin console
-- sessions and unauthenticated pending pairings.
--
-- V1's posture is unchanged: every tenant table still carries household_id,
-- RLS is enabled and forced, and a missing setting denies. The three new global
-- tables (accounts, admins, admin_sessions) are read only by the auth and admin
-- paths, so they carry no tenant policy. Admin lookups into tenant rows are the
-- only escape hatch and they are SECURITY DEFINER functions returning minimum
-- columns -- there is no BYPASSRLS app role and no blanket GUC.

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

-- Admin console lookups. FORCE RLS binds the table owner too, so a
-- SECURITY DEFINER function owned by the migration role would read no tenant
-- rows. These four functions are therefore owned by `hopa_admin`, a NOLOGIN
-- role with BYPASSRLS that only these functions ever run as; the app role
-- itself never bypasses a policy. Each function returns only the columns the
-- lookup page renders, and callers audit-log every phone-number read.
--
-- Production provisioning (creating or altering a BYPASSRLS role requires a
-- superuser; CREATEROLE alone is not enough):
--   create role hopa_admin nologin bypassrls;
-- Run this migration as that superuser, or as a non-superuser deploy role that
-- holds `hopa_admin` WITH ADMIN OPTION, SET FALSE for the duration of the
-- migration and has it revoked afterwards. The temporary grant below adds SET
-- only to this migration's own grant row; a pre-existing grant from another
-- grantor survives the revoke. If a non-superuser deploy role still has SET on
-- `hopa_admin` when the migration ends, the check at the bottom fails here,
-- loudly, rather than leaving a SQL-injection route to the BYPASSRLS owner.
-- `ALTER ... OWNER` also requires CREATE on schema `public` (or ownership of
-- it), so that privilege is borrowed below as well.
do $$
begin
    if not exists (select from pg_roles where rolname = 'hopa_admin') then
        begin
            create role hopa_admin nologin bypassrls;
        exception
            when insufficient_privilege then
                raise exception
                    'admin lookups need hopa_admin, and only a superuser can create a BYPASSRLS role. Run as a superuser: create role hopa_admin nologin bypassrls;';
        end;
    end if;
end
$$;

-- A pre-created role with the wrong attributes would silently deny every admin
-- lookup (or allow a login). Refuse to migrate instead.
do $$
declare
    can_login boolean;
    bypasses_rls boolean;
begin
    select rolcanlogin, rolbypassrls into can_login, bypasses_rls
    from pg_roles
    where rolname = 'hopa_admin';
    if can_login or not bypasses_rls then
        raise exception
            'hopa_admin must be NOLOGIN BYPASSRLS. As a superuser: alter role hopa_admin nologin bypassrls;';
    end if;
end
$$;

-- BYPASSRLS bypasses policies, not privileges: the function owner still needs
-- schema usage and SELECT on every table the lookups touch.
grant usage on schema public to hopa_admin;
grant select on accounts, members, devices, households to hopa_admin;

create function admin_resolve_member(member uuid)
returns table (household_id uuid, member_id uuid, display_name text)
language sql
security definer
set search_path = public, pg_temp
as $$
    select m.household_id, m.id, m.display_name
    from members m
    where m.id = $1
$$;

create function admin_resolve_phone(phone text)
returns table (household_id uuid, member_id uuid, display_name text, account_id uuid)
language sql
security definer
set search_path = public, pg_temp
as $$
    select m.household_id, m.id, m.display_name, a.id
    from accounts a
    join members m on m.account_id = a.id
    where a.phone = $1
      and a.deleted_at is null
    order by m.created_at
    limit 1
$$;

create function admin_resolve_household(household uuid)
returns table (household_id uuid, member_id uuid, display_name text)
language sql
security definer
set search_path = public, pg_temp
as $$
    select m.household_id, m.id, m.display_name
    from members m
    where m.household_id = $1
      and m.role = 'owner'
    order by m.created_at, m.id
    limit 1
$$;

create function admin_list_devices(household uuid)
returns table (
    device_id uuid,
    platform text,
    name text,
    last_seen_at timestamptz,
    revoked_at timestamptz
)
language sql
security definer
set search_path = public, pg_temp
as $$
    select d.id, d.platform, d.name, d.last_seen_at, d.revoked_at
    from devices d
    where d.household_id = $1
    order by d.created_at
$$;

revoke all on function admin_resolve_member(uuid) from public;
revoke all on function admin_resolve_phone(text) from public;
revoke all on function admin_resolve_household(uuid) from public;
revoke all on function admin_list_devices(uuid) from public;
grant execute on function admin_resolve_member(uuid) to hopa_app;
grant execute on function admin_resolve_phone(text) to hopa_app;
grant execute on function admin_resolve_household(uuid) to hopa_app;
grant execute on function admin_list_devices(uuid) to hopa_app;

-- Temporary membership, held only while the ownership changes are made. The
-- new owner must have CREATE on the object's schema, so that privilege is also
-- borrowed for the duration and taken back before the migration ends.
grant hopa_admin to current_user;
grant create on schema public to hopa_admin;
alter function admin_resolve_member(uuid) owner to hopa_admin;
alter function admin_resolve_phone(text) owner to hopa_admin;
alter function admin_resolve_household(uuid) owner to hopa_admin;
alter function admin_list_devices(uuid) owner to hopa_admin;
revoke create on schema public from hopa_admin;

-- The migration role's own grant is gone after this line, but a grant made by
-- another grantor (for example the operator's bootstrap grant) is not removed
-- by it. The invariant that matters is narrower and is checked below: no
-- non-superuser may be able to SET ROLE into the BYPASSRLS owner.
revoke hopa_admin from current_user;

-- Runtime invariant: "no SET capability for a non-superuser", not "no
-- membership". Superusers are exempt because they can already do anything (and
-- Postgres reports SET for them universally). A failure here rolls the whole
-- migration back and names the operator line that removes the residual grant.
do $$
begin
    if not (select rolsuper from pg_roles where rolname = current_user) then
        if pg_has_role(current_user, 'hopa_admin', 'set') then
            raise exception
                'the deploy role can still SET ROLE to the BYPASSRLS owner. As a superuser: revoke hopa_admin from %;', current_user;
        end if;
    end if;
end
$$;
