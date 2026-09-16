-- V38-7B1 HF6: persist Task Issue policy authority provenance.
-- Existing rows cannot be safely reconstructed, so they are explicitly marked LEGACY_UNRESOLVED.

alter table tasks
  add column if not exists issue_sync_policy_source varchar(64) not null default 'LEGACY_UNRESOLVED';

alter table tasks
  add column if not exists issue_sync_policy_inheritance_mode varchar(32) not null default 'NONE';

alter table tasks
  add column if not exists issue_sync_policy_inherited_from_task_id varchar(160);

update tasks
   set issue_sync_policy_source = 'LEGACY_UNRESOLVED'
 where issue_sync_policy_source is null or btrim(issue_sync_policy_source) = '';

update tasks
   set issue_sync_policy_inheritance_mode = 'NONE'
 where issue_sync_policy_inheritance_mode is null or btrim(issue_sync_policy_inheritance_mode) = '';

alter table tasks drop constraint if exists ck_tasks_issue_sync_policy_inheritance_mode;
alter table tasks add constraint ck_tasks_issue_sync_policy_inheritance_mode
  check (issue_sync_policy_inheritance_mode in ('NONE','A2A_PARENT'));

create index if not exists idx_tasks_issue_policy_inherited_from
  on tasks(tenant_id, issue_sync_policy_inherited_from_task_id)
  where issue_sync_policy_inherited_from_task_id is not null;

comment on column tasks.issue_sync_policy_source is
  'Authority source of the effective Task issue_sync_policy, e.g. RULE_OVERRIDE, FLOW_DEFAULT, SYSTEM_FALLBACK, A2A_PARENT_INHERITED, or LEGACY_UNRESOLVED.';
comment on column tasks.issue_sync_policy_inheritance_mode is
  'NONE for locally resolved Task policy; A2A_PARENT when an A2A direct-to-pool child explicitly inherits the parent effective policy.';
comment on column tasks.issue_sync_policy_inherited_from_task_id is
  'Parent Task whose effective Issue policy was inherited. Does not imply the child matched the parent Flow/Rule.';
