-- V38-7B1 HF10 Fix3A: align Task issue policy inheritance mode database contract
-- with the runtime authority vocabulary introduced by capability/plan child Tasks.
--
-- V233 established NONE/A2A_PARENT.  Fix3 introduced two additional explicit
-- provenance modes in CanonicalPlanChildTaskAdapter:
--   CAPABILITY_PARENT - governed capability delegation child
--   PLAN_PARENT       - canonical execution-plan child
--
-- This migration changes only the CHECK constraint and column documentation.
-- No Task data is rewritten and issue_sync_policy values are not changed.

alter table tasks drop constraint if exists ck_tasks_issue_sync_policy_inheritance_mode;

alter table tasks add constraint ck_tasks_issue_sync_policy_inheritance_mode
  check (issue_sync_policy_inheritance_mode in (
    'NONE',
    'A2A_PARENT',
    'CAPABILITY_PARENT',
    'PLAN_PARENT'
  ));

comment on column tasks.issue_sync_policy_inheritance_mode is
  'Issue policy inheritance authority: NONE for locally resolved policy; A2A_PARENT for direct A2A pool child; CAPABILITY_PARENT for governed capability delegation child; PLAN_PARENT for execution-plan child.';
