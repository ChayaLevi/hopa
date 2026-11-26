-- Schema v6: the family board, an append-only log of encrypted operations
-- (ADR-0033).
--
-- The server stores op bodies as ciphertext only and never reads them. The
-- plaintext columns are exactly the metadata ADR-0033 lists: scope, seq,
-- op_id, from_device, member_id, key_epoch, body hash, size and time.
--
-- board_heads holds the last seq per household. An append bumps it with one
-- upsert inside the tenant transaction, so concurrent appends serialize on that
-- row and the seqs of one household have no gap and no duplicate. One counter
-- is shared by both scopes; the gaps a non-parent device sees in `family` are a
-- recorded metadata leak.
--
-- from_device and member_id are server-attested (D12). They reference their
-- rows without a cascade: devices are tombstoned, not deleted, and an
-- append-only log must not lose entries when a device goes away.

create table board_heads (
    household_id uuid primary key references households (id) on delete cascade,
    last_seq bigint not null check (last_seq >= 0)
);

create table board_ops (
    household_id uuid not null references households (id) on delete cascade,
    seq bigint not null check (seq > 0),
    scope text not null check (scope in ('family', 'parents')),
    op_id uuid not null,
    from_device uuid not null references devices (id),
    member_id uuid not null references members (id),
    key_epoch int not null check (key_epoch >= 0),
    body bytea not null,
    body_hash bytea not null check (octet_length(body_hash) = 32),
    size int not null check (size >= 0),
    created_at timestamptz not null default now(),
    primary key (household_id, seq),
    unique (household_id, op_id)
);
-- Paging reads one scope after a seq.
create index board_ops_scope_idx on board_ops (household_id, scope, seq);

create table board_cursors (
    household_id uuid not null references households (id) on delete cascade,
    device_id uuid not null references devices (id) on delete cascade,
    scope text not null check (scope in ('family', 'parents')),
    last_seq bigint not null check (last_seq >= 0),
    updated_at timestamptz not null default now(),
    primary key (device_id, scope)
);
create index board_cursors_household_idx on board_cursors (household_id);

-- One wrapped scope key per device, scope and epoch. Only `parents` has a
-- scope key today; the household key (scope `family`) keeps its own table.
create table scope_key_wraps (
    household_id uuid not null references households (id) on delete cascade,
    scope text not null check (scope in ('parents')),
    key_epoch int not null check (key_epoch >= 0),
    device_id uuid not null references devices (id) on delete cascade,
    wrapped_key bytea not null,
    wrapper_device_id uuid references devices (id) on delete set null,
    wrapped_at timestamptz not null default now(),
    primary key (device_id, scope, key_epoch)
);
create index scope_key_wraps_household_idx on scope_key_wraps (household_id, scope, key_epoch);
create index scope_key_wraps_wrapper_idx on scope_key_wraps (wrapper_device_id);

-- O2: a family role, set only by the test harness until lane B's roles land.
alter table members
    add column family_role text null check (family_role in ('parent', 'teen', 'child'));

alter table board_heads enable row level security;
alter table board_heads force row level security;
create policy board_heads_own on board_heads
    using (household_id = nullif(current_setting('hopa.household_id', true), '')::uuid)
    with check (household_id = nullif(current_setting('hopa.household_id', true), '')::uuid);

alter table board_ops enable row level security;
alter table board_ops force row level security;
create policy board_ops_own on board_ops
    using (household_id = nullif(current_setting('hopa.household_id', true), '')::uuid)
    with check (household_id = nullif(current_setting('hopa.household_id', true), '')::uuid);

alter table board_cursors enable row level security;
alter table board_cursors force row level security;
create policy board_cursors_own on board_cursors
    using (household_id = nullif(current_setting('hopa.household_id', true), '')::uuid)
    with check (household_id = nullif(current_setting('hopa.household_id', true), '')::uuid);

alter table scope_key_wraps enable row level security;
alter table scope_key_wraps force row level security;
create policy scope_key_wraps_own on scope_key_wraps
    using (household_id = nullif(current_setting('hopa.household_id', true), '')::uuid)
    with check (household_id = nullif(current_setting('hopa.household_id', true), '')::uuid);
