-- Phase 12.5 — Incident Control & Security Operations
-- Control state is reversible containment. Immutable action evidence records who changed what and why.

create table if not exists security_incident_cases (
  tenant_id varchar(64) not null,
  case_id varchar(128) not null,
  title varchar(255) not null,
  severity varchar(32) not null default 'MEDIUM',
  status varchar(32) not null default 'OPEN',
  summary text,
  source_incident_id varchar(128),
  root_task_id varchar(128),
  source_system_id varchar(128),
  owner_department_id varchar(128),
  owner_group_id varchar(128),
  opened_by varchar(128) not null,
  opened_at timestamptz not null default now(),
  contained_at timestamptz,
  resolved_at timestamptz,
  resolution_reason text,
  last_action_at timestamptz,
  version bigint not null default 1,
  primary key(tenant_id,case_id),
  check(severity in ('LOW','MEDIUM','HIGH','CRITICAL')),
  check(status in ('OPEN','CONTAINED','RESOLVED'))
);
create index if not exists idx_security_incident_cases_status
  on security_incident_cases(tenant_id,status,severity,opened_at desc,case_id);
create index if not exists idx_security_incident_cases_task
  on security_incident_cases(tenant_id,root_task_id,opened_at desc) where root_task_id is not null;
create index if not exists idx_security_incident_cases_source
  on security_incident_cases(tenant_id,source_system_id,opened_at desc) where source_system_id is not null;
create index if not exists idx_security_incident_cases_owner_department
  on security_incident_cases(tenant_id,owner_department_id,status,opened_at desc) where owner_department_id is not null;

create table if not exists security_resource_controls (
  tenant_id varchar(64) not null,
  control_id varchar(128) not null,
  case_id varchar(128) not null,
  target_type varchar(32) not null,
  target_id varchar(128) not null,
  parent_target_id varchar(128),
  control_type varchar(32) not null,
  status varchar(16) not null default 'ACTIVE',
  limit_per_minute integer,
  reason text not null,
  requested_by varchar(128) not null,
  started_at timestamptz not null default now(),
  expires_at timestamptz,
  released_at timestamptz,
  released_by varchar(128),
  release_reason text,
  original_state jsonb not null default '{}'::jsonb,
  side_effect_applied boolean not null default false,
  version bigint not null default 1,
  primary key(tenant_id,control_id),
  foreign key(tenant_id,case_id) references security_incident_cases(tenant_id,case_id) on delete restrict,
  check(target_type in ('CREDENTIAL','SERVICE_ACCOUNT','AGENT','TASK','SOURCE_SYSTEM')),
  check(control_type in ('THROTTLE','SUSPEND','HOLD','BLOCK_RETRY','QUARANTINE')),
  check(status in ('ACTIVE','RELEASED','EXPIRED')),
  check(limit_per_minute is null or limit_per_minute > 0)
);
create unique index if not exists ux_security_resource_controls_active
  on security_resource_controls(tenant_id,target_type,target_id,control_type)
  where status='ACTIVE';
create index if not exists idx_security_resource_controls_case
  on security_resource_controls(tenant_id,case_id,status,started_at desc);
create index if not exists idx_security_resource_controls_target
  on security_resource_controls(tenant_id,target_type,target_id,status,started_at desc);
create index if not exists idx_security_resource_controls_expiry
  on security_resource_controls(status,expires_at) where status='ACTIVE' and expires_at is not null;

create table if not exists security_control_action_evidence (
  tenant_id varchar(64) not null,
  action_id varchar(128) not null,
  case_id varchar(128) not null,
  control_id varchar(128),
  target_type varchar(32) not null,
  target_id varchar(128) not null,
  action_type varchar(32) not null,
  actor_id varchar(128) not null,
  reason text not null,
  before_state jsonb not null default '{}'::jsonb,
  after_state jsonb not null default '{}'::jsonb,
  authorization_decision_id varchar(128),
  correlation_id varchar(128),
  occurred_at timestamptz not null default now(),
  primary key(tenant_id,action_id),
  foreign key(tenant_id,case_id) references security_incident_cases(tenant_id,case_id) on delete restrict,
  check(target_type in ('CREDENTIAL','SERVICE_ACCOUNT','AGENT','TASK','SOURCE_SYSTEM')),
  check(action_type in ('THROTTLE','SUSPEND','REVOKE','HOLD','RELEASE_HOLD','BLOCK_RETRY','RELEASE_BLOCK_RETRY','CANCEL','REASSIGN','FORCE_FAIL','QUARANTINE','RELEASE_QUARANTINE','RELEASE_CONTROL'))
);
create index if not exists idx_security_control_action_case
  on security_control_action_evidence(tenant_id,case_id,occurred_at desc,action_id);
create index if not exists idx_security_control_action_target
  on security_control_action_evidence(tenant_id,target_type,target_id,occurred_at desc,action_id);
create index if not exists idx_security_control_action_correlation
  on security_control_action_evidence(tenant_id,correlation_id,occurred_at desc) where correlation_id is not null;

create or replace function phase12_5_reject_security_control_evidence_mutation()
returns trigger language plpgsql as $$
begin
  raise exception 'SECURITY_CONTROL_EVIDENCE_IMMUTABLE';
end $$;
drop trigger if exists trg_security_control_action_evidence_immutable on security_control_action_evidence;
create trigger trg_security_control_action_evidence_immutable before update or delete on security_control_action_evidence
for each row execute function phase12_5_reject_security_control_evidence_mutation();

-- Runtime query helper indexes. These are deliberately narrow because these lookups are on every machine request.
create index if not exists idx_token_service_account_credentials_incident_lookup
  on token_service_account_credentials(tenant_id,credential_id,service_account_id,status);

-- Existing operational data becomes case-linkable without changing the canonical Incident aggregate.
alter table incidents add column if not exists security_case_id varchar(128);
create index if not exists idx_incidents_security_case on incidents(tenant_id,security_case_id) where security_case_id is not null;

-- Tenant RLS follows the existing current_setting('app.tenant_id', true) convention.
do $$
declare table_name text;
begin
  foreach table_name in array array['security_incident_cases','security_resource_controls','security_control_action_evidence'] loop
    execute format('alter table %I enable row level security',table_name);
    execute format('alter table %I force row level security',table_name);
    execute format('drop policy if exists tenant_isolation on %I',table_name);
    execute format('create policy tenant_isolation on %I using (tenant_id=current_setting(''app.tenant_id'',true)) with check (tenant_id=current_setting(''app.tenant_id'',true))',table_name);
  end loop;
end $$;

comment on table security_incident_cases is 'Phase 12.5 operator Security Incident Case. It organizes investigation and containment; it is not authorization authority.';
comment on table security_resource_controls is 'Phase 12.5 active reversible runtime containment controls. Final authorization still comes from IAM/RBAC and Resource Access.';
comment on table security_control_action_evidence is 'Phase 12.5 immutable Human control evidence including before/after snapshots, decision id, correlation and reason.';
