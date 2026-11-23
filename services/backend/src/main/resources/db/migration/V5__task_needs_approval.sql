-- P4: an approval request is a task state, not a second queue.
--
-- This arrived as V2 on PR #144, but PR #140 owns V2 (accounts, wraps, pending
-- pairings) and is the authoritative schema. Renumbered to V5 so it applies
-- after V4 instead of colliding with that history; the alter is idempotent, so
-- a database that already applied it under the old name is unaffected.
alter table tasks drop constraint if exists tasks_state_check;
alter table tasks add constraint tasks_state_check check (state in (
    'queued', 'delivered', 'acked', 'progress', 'needs_approval',
    'done', 'failed', 'cancelled'
));
