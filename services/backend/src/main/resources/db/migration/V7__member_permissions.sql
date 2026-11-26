-- Schema v7: member permissions (family board B5, ADR-0033 amendment).
--
-- A household is a group; what a member may do in it is a set of permissions,
-- not a fixed family role. Clients show a member with `board.private` as a
-- parent and one without it as a child; the server stores no such word.
--
--   board.private  read and write the private board scope (`parents`) and
--                  hold its key
--   group.manage   change the members' permissions
--
-- The household founder gets both. `members.role` (owner/member) is untouched.

create table member_permissions (
    household_id uuid not null references households (id) on delete cascade,
    member_id uuid not null references members (id) on delete cascade,
    permission text not null check (permission in ('board.private', 'group.manage')),
    granted_at timestamptz not null default now(),
    primary key (member_id, permission)
);
create index member_permissions_household_idx on member_permissions (household_id);

-- The backfill reads every household's members. The deploy role owns the
-- tables but FORCE RLS binds it too, so it is lifted on `members` for this
-- transaction only (DDL is transactional) and restored below.
alter table members no force row level security;

-- V6's family role becomes a permission: a parent keeps the private scope.
insert into member_permissions (household_id, member_id, permission)
select household_id, id, 'board.private' from members where family_role = 'parent';

-- The founder of every household (its first owner) gets both permissions.
insert into member_permissions (household_id, member_id, permission)
select founder.household_id, founder.id, p.permission
from (
    select distinct on (household_id) household_id, id
    from members
    where role = 'owner'
    order by household_id, created_at, id
) founder
cross join (values ('board.private'), ('group.manage')) as p (permission)
on conflict do nothing;

alter table members drop column family_role;
alter table members force row level security;

alter table member_permissions enable row level security;
alter table member_permissions force row level security;
create policy member_permissions_own on member_permissions
    using (household_id = nullif(current_setting('hopa.household_id', true), '')::uuid)
    with check (household_id = nullif(current_setting('hopa.household_id', true), '')::uuid);
