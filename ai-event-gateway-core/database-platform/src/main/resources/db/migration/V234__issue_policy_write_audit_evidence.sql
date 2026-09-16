-- V38-7B1 HF10 Evidence Patch 1: durable Issue-policy write provenance only.
-- Evidence-only schema. This migration does not alter Issue policy semantics or Task inheritance behavior.

create table if not exists dispatch_issue_policy_write_audits (
  tenant_id varchar(64) not null,
  audit_id uuid not null default gen_random_uuid(),
  resource_type varchar(16) not null,
  flow_id varchar(128) not null,
  rule_id varchar(128),
  rule_code varchar(128),
  source_system varchar(128),
  operation varchar(32) not null,
  mutation_source varchar(128) not null,
  request_field_present boolean not null,
  request_value varchar(64),
  model_value varchar(64),
  previous_persisted_value varchar(64),
  persisted_value varchar(64),
  inheritance_from_flow boolean not null default false,
  server_default_applied boolean not null default false,
  changed boolean not null default false,
  request_id varchar(160),
  correlation_id varchar(160),
  operator_id varchar(160),
  request_kind varchar(96),
  created_at timestamptz not null default now(),
  primary key (tenant_id, audit_id),
  check (resource_type in ('FLOW','RULE')),
  check (operation in ('CREATE','UPDATE','MIGRATION_SNAPSHOT'))
);

create index if not exists idx_dispatch_issue_policy_write_audit_flow
  on dispatch_issue_policy_write_audits(tenant_id, flow_id, created_at desc);
create index if not exists idx_dispatch_issue_policy_write_audit_rule
  on dispatch_issue_policy_write_audits(tenant_id, rule_id, created_at desc)
  where rule_id is not null;

-- Existing configuration predates durable provenance. Snapshot it explicitly without inventing an actor.
insert into dispatch_issue_policy_write_audits(
  tenant_id, resource_type, flow_id, source_system, operation, mutation_source,
  request_field_present, request_value, model_value, previous_persisted_value, persisted_value,
  inheritance_from_flow, server_default_applied, changed, request_kind, created_at)
select f.tenant_id, 'FLOW', f.flow_id, f.source_system, 'MIGRATION_SNAPSHOT', 'V234_EXISTING_STATE',
       false, '<UNKNOWN>', f.issue_sync_policy, null, f.issue_sync_policy,
       false, false, false, 'MIGRATION', now()
from dispatch_flows f
where not exists (
  select 1 from dispatch_issue_policy_write_audits a
   where a.tenant_id=f.tenant_id and a.resource_type='FLOW' and a.flow_id=f.flow_id
     and a.operation='MIGRATION_SNAPSHOT' and a.mutation_source='V234_EXISTING_STATE'
);

insert into dispatch_issue_policy_write_audits(
  tenant_id, resource_type, flow_id, rule_id, rule_code, source_system, operation, mutation_source,
  request_field_present, request_value, model_value, previous_persisted_value, persisted_value,
  inheritance_from_flow, server_default_applied, changed, request_kind, created_at)
select p.tenant_id, 'RULE', p.flow_id, p.policy_id, p.policy_code, p.source_system,
       'MIGRATION_SNAPSHOT', 'V234_EXISTING_STATE', false, '<UNKNOWN>', p.issue_sync_policy,
       null, p.issue_sync_policy, p.issue_sync_policy is null, false, false, 'MIGRATION', now()
from dispatch_policies p
where not exists (
  select 1 from dispatch_issue_policy_write_audits a
   where a.tenant_id=p.tenant_id and a.resource_type='RULE' and a.rule_id=p.policy_id
     and a.operation='MIGRATION_SNAPSHOT' and a.mutation_source='V234_EXISTING_STATE'
);

-- Apply tenant isolation after the migration snapshot so Flyway does not require a runtime tenant context.
alter table dispatch_issue_policy_write_audits enable row level security;
alter table dispatch_issue_policy_write_audits force row level security;
drop policy if exists tenant_isolation on dispatch_issue_policy_write_audits;
create policy tenant_isolation on dispatch_issue_policy_write_audits
  using (tenant_id = iam_current_tenant_id()) with check (tenant_id = iam_current_tenant_id());
revoke update, delete, truncate on dispatch_issue_policy_write_audits from public;

