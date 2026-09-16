-- P4RA-E: Task/A2A enforcement evidence and scope-aware query indexes.

create table if not exists resource_task_scope_query_audits (
  tenant_id varchar(64) not null,
  scope_query_audit_id varchar(128) not null,
  principal_type varchar(40) not null,
  principal_id varchar(128) not null,
  permission_code varchar(160) not null,
  purpose varchar(120) not null,
  strategy varchar(48) not null,
  plan_hash varchar(64) not null,
  maximum_visibility varchar(32) not null,
  exact_department_count integer not null default 0,
  subtree_root_count integer not null default 0,
  group_count integer not null default 0,
  explicit_resource_count integer not null default 0,
  excluded_resource_count integer not null default 0,
  policy_catalog_version bigint not null,
  policy_revision bigint not null,
  global_security_epoch bigint not null,
  tenant_security_epoch bigint not null,
  principal_security_epoch bigint not null,
  resource_security_epoch bigint not null,
  department_tree_revision bigint not null,
  created_at timestamptz not null,
  primary key (tenant_id, scope_query_audit_id),
  unique (tenant_id, plan_hash, purpose),
  check (strategy in ('DENY_ALL','TENANT','DIRECT_HIERARCHY_JOIN','EXPLICIT_RESOURCE_SET','PARTICIPANT_JOIN','HYBRID')),
  check (maximum_visibility in ('NONE','METADATA','SUMMARY','STANDARD','SENSITIVE','FULL','SECRET_METADATA')),
  check (exact_department_count >= 0 and subtree_root_count >= 0 and group_count >= 0
         and explicit_resource_count >= 0 and excluded_resource_count >= 0)
);

create table if not exists resource_task_scope_shadow_mismatches (
  tenant_id varchar(64) not null,
  mismatch_id varchar(128) not null,
  principal_type varchar(40) not null,
  principal_id varchar(128) not null,
  permission_code varchar(160) not null,
  purpose varchar(120) not null,
  plan_hash varchar(64) not null,
  legacy_only_count integer not null,
  scoped_only_count integer not null,
  legacy_only_sample text not null default '',
  scoped_only_sample text not null default '',
  created_at timestamptz not null,
  primary key (tenant_id, mismatch_id),
  check (legacy_only_count >= 0 and scoped_only_count >= 0),
  check (legacy_only_count > 0 or scoped_only_count > 0)
);

create index if not exists idx_resource_task_scope_query_principal
  on resource_task_scope_query_audits(tenant_id, principal_id, permission_code, created_at desc);
create index if not exists idx_resource_task_scope_shadow_principal
  on resource_task_scope_shadow_mismatches(tenant_id, principal_id, permission_code, created_at desc);

-- Scope predicates execute before ORDER/LIMIT. These indexes support direct hierarchy and participant plans.
create index if not exists idx_tasks_resource_scope_owner
  on tasks(tenant_id, owner_department_id, created_at desc, task_id);
create index if not exists idx_tasks_resource_scope_requester
  on tasks(tenant_id, requester_department_id, created_at desc, task_id);
create index if not exists idx_tasks_resource_scope_executor
  on tasks(tenant_id, executor_department_id, created_at desc, task_id);
create index if not exists idx_tasks_resource_scope_owner_group
  on tasks(tenant_id, owner_group_id, created_at desc, task_id);
create index if not exists idx_resource_participants_task_scope
  on resource_participants(tenant_id, resource_type, participant_type, participant_ref_id,
                           participant_status, resource_id);
create index if not exists idx_resource_scope_grants_task_list
  on resource_scope_grants(tenant_id, principal_type, principal_id, permission_code,
                           resource_type, grant_state, scope_type, scope_ref_id);
create index if not exists idx_resource_scope_denies_task_list
  on resource_scope_denies(tenant_id, principal_type, principal_id, permission_code,
                           resource_type, deny_state, scope_type, scope_ref_id);

alter table resource_task_scope_query_audits enable row level security;
alter table resource_task_scope_query_audits force row level security;
drop policy if exists tenant_isolation on resource_task_scope_query_audits;
create policy tenant_isolation on resource_task_scope_query_audits
  using (tenant_id = iam_current_tenant_id()) with check (tenant_id = iam_current_tenant_id());

alter table resource_task_scope_shadow_mismatches enable row level security;
alter table resource_task_scope_shadow_mismatches force row level security;
drop policy if exists tenant_isolation on resource_task_scope_shadow_mismatches;
create policy tenant_isolation on resource_task_scope_shadow_mismatches
  using (tenant_id = iam_current_tenant_id()) with check (tenant_id = iam_current_tenant_id());

revoke update, delete, truncate on resource_task_scope_query_audits from public;
revoke update, delete, truncate on resource_task_scope_shadow_mismatches from public;

drop trigger if exists trg_resource_task_scope_query_immutable on resource_task_scope_query_audits;
create trigger trg_resource_task_scope_query_immutable before update or delete on resource_task_scope_query_audits
  for each row execute function p4ra_reject_append_only_mutation();
drop trigger if exists trg_resource_task_scope_shadow_immutable on resource_task_scope_shadow_mismatches;
create trigger trg_resource_task_scope_shadow_immutable before update or delete on resource_task_scope_shadow_mismatches
  for each row execute function p4ra_reject_append_only_mutation();

insert into permission_point_catalog(permission_point,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed)
values
 ('task.read','TASK','READ','Read a Task within Resource Scope.','MEDIUM',array['TENANT','DEPARTMENT','GROUP'],true),
 ('task.update','TASK','UPDATE','Update a Task within Resource Scope.','HIGH',array['TENANT','DEPARTMENT','GROUP'],true),
 ('task.comment','TASK','UPDATE','Add a Task collaboration comment.','MEDIUM',array['TENANT','DEPARTMENT','GROUP'],true),
 ('task.approve','TASK','APPROVE','Approve a Human Task action.','HIGH',array['TENANT','DEPARTMENT','GROUP'],true),
 ('task.participant.manage','TASK','MANAGE','Manage Task participants.','CRITICAL',array['TENANT','DEPARTMENT','GROUP'],true),
 ('a2a.request.create','A2A_REQUEST','CREATE','Create an A2A request from an authorized Task.','HIGH',array['TENANT','DEPARTMENT','GROUP'],true),
 ('a2a.request.read','A2A_REQUEST','READ','Read an A2A request within Resource Scope.','MEDIUM',array['TENANT','DEPARTMENT','GROUP'],true),
 ('a2a.request.execute','A2A_REQUEST','EXECUTE','Execute an assigned A2A request.','HIGH',array['TENANT','DEPARTMENT','GROUP'],true),
 ('a2a.request.cancel','A2A_REQUEST','UPDATE','Cancel an A2A request.','HIGH',array['TENANT','DEPARTMENT','GROUP'],true),
 ('a2a.approval.read','A2A_APPROVAL','READ','Read an A2A approval package.','HIGH',array['TENANT','DEPARTMENT','GROUP'],true),
 ('a2a.approval.approve','A2A_APPROVAL','APPROVE','Approve an A2A request after SoD validation.','CRITICAL',array['TENANT','DEPARTMENT','GROUP'],true),
 ('a2a.approval.reject','A2A_APPROVAL','APPROVE','Reject an A2A request after SoD validation.','CRITICAL',array['TENANT','DEPARTMENT','GROUP'],true),
 ('a2a.context.preview','TASK_CONTEXT_SNAPSHOT','READ','Preview a Task-bound Handoff Context.','HIGH',array['TENANT','DEPARTMENT','GROUP'],true),
 ('a2a.context.create','TASK_CONTEXT_SNAPSHOT','CREATE','Create or supersede a Task-bound Handoff Context.','HIGH',array['TENANT','DEPARTMENT','GROUP'],true),
 ('a2a.context.read','TASK_CONTEXT_SNAPSHOT','READ','Read an authorized Handoff Context snapshot.','HIGH',array['TENANT','DEPARTMENT','GROUP'],true),
 ('a2a.context.release','TASK_CONTEXT_SNAPSHOT','UPDATE','Retry Handoff Context release.','CRITICAL',array['TENANT','DEPARTMENT','GROUP'],true),
 ('a2a.context.approve','TASK_CONTEXT_SNAPSHOT','APPROVE','Approve a Handoff Context snapshot.','CRITICAL',array['TENANT','DEPARTMENT','GROUP'],true),
 ('a2a.context.reject','TASK_CONTEXT_SNAPSHOT','APPROVE','Reject a Handoff Context snapshot.','CRITICAL',array['TENANT','DEPARTMENT','GROUP'],true),
 ('a2a.context.audit.read','TASK_CONTEXT_SNAPSHOT','READ','Read Agent Context access audit evidence.','CRITICAL',array['TENANT','DEPARTMENT','GROUP'],true),
 ('a2a.result.create','TASK_RESULT','CREATE','Create a Task-bound result snapshot.','HIGH',array['TENANT','DEPARTMENT','GROUP'],true),
 ('a2a.result.read','TASK_RESULT','READ','Read a Task-bound result snapshot.','HIGH',array['TENANT','DEPARTMENT','GROUP'],true),
 ('a2a.result.reconcile','TASK_RESULT','UPDATE','Reconcile canonical A2A result processing.','CRITICAL',array['TENANT','DEPARTMENT','GROUP'],true)
on conflict(permission_point) do update set
 resource_type=excluded.resource_type, action_code=excluded.action_code,
 description=excluded.description, risk_level=excluded.risk_level,
 allowed_scope_types=excluded.allowed_scope_types, system_managed=true,
 active=true, version=permission_point_catalog.version+1;

insert into reason_code_catalog(reason_code,http_status,category,retryable,message_template)
values
 ('TASK_SCOPE_QUERY_DENIED',403,'AUTHORIZATION',false,'The Task list scope query has no effective Resource Scope.'),
 ('TASK_CHAIN_NODE_RESTRICTED',200,'AUTHORIZATION',false,'The Task Chain node is represented as a restricted placeholder.'),
 ('TASK_SCOPE_SHADOW_MISMATCH',200,'AUTHORIZATION',false,'Legacy and scoped Task list result sets differ.'),
 ('A2A_APPROVAL_SEPARATION_OF_DUTIES_VIOLATION',403,'AUTHORIZATION',false,'The A2A requester cannot approve the same high-risk request.'),
 ('HANDOFF_CONTEXT_VISIBILITY_REDACTED',200,'AUTHORIZATION',false,'Handoff Context fields were redacted to the granted visibility.')
on conflict(reason_code) do update set http_status=excluded.http_status,category=excluded.category,
 retryable=excluded.retryable,message_template=excluded.message_template;
