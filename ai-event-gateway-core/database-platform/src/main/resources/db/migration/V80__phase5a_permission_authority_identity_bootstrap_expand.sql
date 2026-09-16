-- Phase 5A expand: PostgreSQL becomes the only runtime Permission Catalog authority.
-- Historical migrations intentionally keep their original names; this migration removes the
-- runtime permission_point_catalog table and replaces it with permission_definitions.

do $$
begin
  if to_regclass('public.permission_definitions') is null
     and to_regclass('public.permission_point_catalog') is not null then
    alter table permission_point_catalog rename to permission_definitions;
  end if;
end $$;

do $$
begin
  if exists (
    select 1 from information_schema.columns
    where table_schema='public' and table_name='permission_definitions' and column_name='permission_point'
  ) then
    alter table permission_definitions rename column permission_point to permission_code;
  end if;
end $$;

-- Reconcile permissions that previously existed only in the deleted Java/JSON catalogs.
-- After this insert every runtime-recognized permission is represented in PostgreSQL.
insert into permission_definitions(
  permission_code,resource_type,action_code,description,risk_level,
  allowed_scope_types,system_managed,active,version)
values
 ('task.read_payload','TASK','READ','Read Task payload content.','HIGH',array['TENANT']::varchar[],true,true,1),
 ('task.read_evidence','TASK','READ','Read Task execution evidence.','HIGH',array['TENANT']::varchar[],true,true,1),
 ('task.cancel','TASK','CANCEL','Cancel an active Task.','HIGH',array['TENANT']::varchar[],true,true,1),
 ('task.retry','TASK','RETRY','Retry a failed Task.','HIGH',array['TENANT']::varchar[],true,true,1),
 ('task.reassign','TASK','REASSIGN','Reassign Task ownership.','HIGH',array['TENANT']::varchar[],true,true,1),
 ('task.add_reference','TASK_REFERENCE','CREATE','Add a Task reference.','MEDIUM',array['TENANT']::varchar[],true,true,1),
 ('task.remove_reference','TASK_REFERENCE','DELETE','Remove a Task reference.','HIGH',array['TENANT']::varchar[],true,true,1),
 ('a2a.reject','A2A_REQUEST','REJECT','Reject an A2A Request.','HIGH',array['TENANT']::varchar[],true,true,1),
 ('a2a.cancel','A2A_REQUEST','CANCEL','Cancel an A2A Request.','HIGH',array['TENANT']::varchar[],true,true,1),
 ('a2a.override_policy','A2A_POLICY','OVERRIDE','Override A2A policy under governed approval.','CRITICAL',array['TENANT']::varchar[],true,true,1),
 ('a2a.view_chain','A2A_REQUEST','READ','Read the A2A handoff chain.','MEDIUM',array['TENANT']::varchar[],true,true,1),
 ('a2a.view_payload','A2A_REQUEST','READ','Read A2A payload content.','HIGH',array['TENANT']::varchar[],true,true,1),
 ('a2a.manage_policy','A2A_POLICY','MANAGE','Manage A2A policy definitions.','CRITICAL',array['TENANT']::varchar[],true,true,1),
 ('agent.read','AGENT','READ','Read Agent metadata.','MEDIUM',array['TENANT']::varchar[],true,true,1),
 ('agent.manage','AGENT','MANAGE','Manage Agent definitions.','HIGH',array['TENANT']::varchar[],true,true,1),
 ('agent.approve','AGENT','APPROVE','Approve an Agent for use.','CRITICAL',array['TENANT']::varchar[],true,true,1),
 ('agent.suspend','AGENT','SUSPEND','Suspend an Agent.','CRITICAL',array['TENANT']::varchar[],true,true,1),
 ('agent.pool.manage','AGENT_POOL','MANAGE','Manage Agent pools.','HIGH',array['TENANT']::varchar[],true,true,1),
 ('agent.credential.rotate','AGENT_CREDENTIAL','ROTATE','Rotate Agent credentials.','CRITICAL',array['TENANT']::varchar[],true,true,1),
 ('agent.runtime.diagnose','AGENT_RUNTIME','DIAGNOSE','Diagnose Agent runtime state.','HIGH',array['TENANT']::varchar[],true,true,1),
 ('integration.connection.read','INTEGRATION_CONNECTION','READ','Read Integration connections.','MEDIUM',array['TENANT']::varchar[],true,true,1),
 ('integration.connection.manage','INTEGRATION_CONNECTION','MANAGE','Manage Integration connections.','CRITICAL',array['TENANT']::varchar[],true,true,1),
 ('integration.connection.test','INTEGRATION_CONNECTION','TEST','Test an Integration connection.','HIGH',array['TENANT']::varchar[],true,true,1),
 ('integration.project_mapping.read','PROJECT_MAPPING','READ','Read Integration project mappings.','MEDIUM',array['TENANT']::varchar[],true,true,1),
 ('integration.project_mapping.manage','PROJECT_MAPPING','MANAGE','Manage Integration project mappings.','CRITICAL',array['TENANT']::varchar[],true,true,1),
 ('integration.issue.read','EXTERNAL_ISSUE','READ','Read external Issue projections.','MEDIUM',array['TENANT']::varchar[],true,true,1),
 ('integration.issue.comment','EXTERNAL_ISSUE','COMMENT','Create an external Issue comment.','HIGH',array['TENANT']::varchar[],true,true,1),
 ('integration.issue.relate','EXTERNAL_ISSUE','RELATE','Create an external Issue relation.','HIGH',array['TENANT']::varchar[],true,true,1),
 ('integration.relay.read','INTEGRATION_RELAY','READ','Read cross-project relay state.','MEDIUM',array['TENANT']::varchar[],true,true,1),
 ('integration.relay.manage','INTEGRATION_RELAY','MANAGE','Manage cross-project relay topology.','CRITICAL',array['TENANT']::varchar[],true,true,1),
 ('integration.relay.retry','INTEGRATION_RELAY','RETRY','Retry a relay edge operation.','HIGH',array['TENANT']::varchar[],true,true,1),
 ('integration.relay.compensate','INTEGRATION_RELAY','COMPENSATE','Execute governed relay compensation.','CRITICAL',array['TENANT']::varchar[],true,true,1),
 ('audit.export','AUDIT_EVIDENCE','EXPORT','Export immutable audit evidence.','CRITICAL',array['TENANT']::varchar[],true,true,1)
on conflict(permission_code) do nothing;

create table if not exists permission_catalog_revisions (
  revision_id uuid primary key,
  revision_code varchar(128) not null unique,
  revision_number bigint not null unique,
  status varchar(24) not null,
  content_hash varchar(128) not null,
  description text not null default '',
  supersedes_revision_id uuid references permission_catalog_revisions(revision_id),
  created_at timestamptz not null,
  created_by varchar(128) not null,
  published_at timestamptz,
  published_by varchar(128),
  version bigint not null default 1
);

insert into permission_catalog_revisions(
  revision_id,revision_code,revision_number,status,content_hash,description,
  created_at,created_by,published_at,published_by,version)
values(
  '00000000-0000-0000-0000-000000000001',
  'BASELINE-0.8.2',1,'PUBLISHED','PENDING_RECALCULATION',
  'Phase 5A baseline imported from the former PostgreSQL permission_point_catalog.',
  now(),'phase5a-migration',now(),'phase5a-migration',1)
on conflict(revision_id) do nothing;

alter table permission_definitions
  add column if not exists owner_module varchar(128),
  add column if not exists risk_lane varchar(24),
  add column if not exists lifecycle varchar(24),
  add column if not exists catalog_revision_id uuid,
  add column if not exists replacement_permission_code varchar(160),
  add column if not exists introduced_at timestamptz,
  add column if not exists deprecated_at timestamptz,
  add column if not exists retired_at timestamptz,
  add column if not exists updated_at timestamptz,
  add column if not exists updated_by varchar(128);

update permission_definitions
set owner_module = case
      when permission_code like 'task.%' then 'task-orchestration'
      when permission_code like 'a2a.%' then 'a2a-core'
      when permission_code like 'agent.%' then 'agent-control'
      when permission_code like 'integration.%' then 'issue-tracking-application'
      when permission_code like 'identity.%' then 'identity-core'
      when permission_code like 'instance.%' then 'organization-access'
      when permission_code like 'security.%' then 'authentication'
      when permission_code like 'resource.%' then 'resource-access-core'
      when permission_code like 'audit.%' then 'governance'
      when permission_code like 'dispatch.%' then 'task-orchestration'
      when permission_code like 'handoff.%' then 'task-orchestration'
      when permission_code like 'source_system.%' then 'integration-events'
      when permission_code like 'system.%' then 'control-plane-app'
      else 'platform-governance'
    end,
    risk_lane = case
      when upper(action_code) in ('READ','LIST','VIEW','SEARCH','EXPLAIN','SIMULATE') then 'READ'
      when upper(action_code)='EXPORT' then 'EXPORT'
      when risk_level='CRITICAL' then 'CRITICAL'
      when upper(action_code) in ('MANAGE','APPROVE','OVERRIDE','ROTATE','REVOKE','RESET','REPAIR') then 'ADMIN'
      else 'WRITE'
    end,
    lifecycle = case when active then 'ACTIVE' else 'RETIRED' end,
    catalog_revision_id = coalesce(catalog_revision_id,'00000000-0000-0000-0000-000000000001'::uuid),
    introduced_at = coalesce(introduced_at,now()),
    updated_at = coalesce(updated_at,now()),
    updated_by = coalesce(updated_by,'phase5a-migration')
where owner_module is null or risk_lane is null or lifecycle is null or catalog_revision_id is null
   or introduced_at is null or updated_at is null or updated_by is null;

alter table permission_definitions
  alter column owner_module set not null,
  alter column risk_lane set not null,
  alter column lifecycle set not null,
  alter column catalog_revision_id set not null,
  alter column introduced_at set not null,
  alter column updated_at set not null,
  alter column updated_by set not null;

alter table permission_definitions
  add constraint fk_permission_definition_revision
    foreign key(catalog_revision_id) references permission_catalog_revisions(revision_id),
  add constraint fk_permission_definition_replacement
    foreign key(replacement_permission_code) references permission_definitions(permission_code);

create table if not exists permission_catalog_active_revision (
  singleton_id varchar(16) primary key,
  revision_id uuid not null references permission_catalog_revisions(revision_id),
  activated_at timestamptz not null,
  activated_by varchar(128) not null,
  version bigint not null default 1
);
insert into permission_catalog_active_revision(singleton_id,revision_id,activated_at,activated_by,version)
values('ACTIVE','00000000-0000-0000-0000-000000000001',now(),'phase5a-migration',1)
on conflict(singleton_id) do nothing;

create table if not exists permission_catalog_aliases (
  alias_code varchar(160) primary key,
  canonical_permission_code varchar(160) not null references permission_definitions(permission_code),
  alias_type varchar(24) not null,
  valid_from timestamptz not null,
  valid_until timestamptz,
  reason varchar(500) not null,
  created_at timestamptz not null,
  created_by varchar(128) not null,
  version bigint not null default 1
);

create table if not exists permission_catalog_change_events (
  event_id uuid primary key,
  revision_id uuid not null references permission_catalog_revisions(revision_id),
  permission_code varchar(160),
  event_type varchar(40) not null,
  change_summary text not null,
  before_hash varchar(128),
  after_hash varchar(128),
  actor_id varchar(128) not null,
  correlation_id varchar(128),
  occurred_at timestamptz not null
);
create index if not exists idx_permission_catalog_change_events_revision
  on permission_catalog_change_events(revision_id,occurred_at,event_id);

-- Existing identity tables already correctly separate a global user from Tenant membership.
-- Add only missing account classification and bootstrap provenance; do not duplicate credential state.
alter table iam_users
  add column if not exists account_type varchar(24) not null default 'STANDARD',
  add column if not exists authentication_type varchar(24) not null default 'LOCAL',
  add column if not exists last_login_at timestamptz;

alter table iam_root_identities
  add column if not exists bootstrap_origin varchar(24) not null default 'INTERACTIVE',
  add column if not exists bootstrap_created_at timestamptz,
  add column if not exists bootstrap_completed_at timestamptz,
  add column if not exists last_login_at timestamptz;

alter table auth_root_bootstrap_state
  add column if not exists bootstrap_source varchar(24) not null default 'INTERACTIVE',
  add column if not exists bootstrap_secret_consumed_at timestamptz;

update iam_root_identities
set bootstrap_created_at=coalesce(bootstrap_created_at,created_at),
    bootstrap_completed_at=case
      when bootstrap_completed_at is null and status <> 'BOOTSTRAP_PENDING' then updated_at
      else bootstrap_completed_at end;

-- Baseline content hash is computed wholly inside PostgreSQL so no external JSON/Java catalog is authoritative.
update permission_catalog_revisions
set content_hash=(
  select 'md5:' || md5(string_agg(
    permission_code || '|' || owner_module || '|' || resource_type || '|' || action_code || '|' ||
    risk_level || '|' || risk_lane || '|' || lifecycle || '|' || coalesce(array_to_string(allowed_scope_types,','),''),
    E'\n' order by permission_code))
  from permission_definitions
), version=version+1
where revision_id='00000000-0000-0000-0000-000000000001'
  and content_hash='PENDING_RECALCULATION';
