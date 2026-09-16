-- P4RA-I: Shadow acceptance, certification evidence, controlled rollout and rollback evidence.

create table if not exists resource_access_shadow_acceptance_thresholds (
  tenant_id varchar(64) not null,
  threshold_id varchar(128) not null,
  minimum_samples bigint not null,
  maximum_mismatch_rate_bps integer not null,
  maximum_legacy_unavailable_rate_bps integer not null,
  maximum_critical_mismatches bigint not null default 0,
  minimum_observation_seconds bigint not null,
  threshold_status varchar(24) not null default 'ACTIVE',
  version bigint not null default 0,
  valid_from timestamptz not null,
  valid_to timestamptz,
  created_at timestamptz not null default now(),
  created_by varchar(128) not null,
  approved_at timestamptz not null,
  approved_by varchar(128) not null,
  reason text not null,
  primary key(tenant_id,threshold_id),
  foreign key(tenant_id) references tenants(tenant_id),
  check(minimum_samples>0),
  check(maximum_mismatch_rate_bps between 0 and 10000),
  check(maximum_legacy_unavailable_rate_bps between 0 and 10000),
  check(maximum_critical_mismatches>=0),
  check(minimum_observation_seconds>0),
  check(threshold_status in ('ACTIVE','RETIRED')),
  check(valid_to is null or valid_to>valid_from),
  check(approved_by<>created_by)
);
create unique index if not exists uq_p4ra_i_active_threshold
 on resource_access_shadow_acceptance_thresholds(tenant_id) where threshold_status='ACTIVE';

create table if not exists resource_access_release_assessments (
  tenant_id varchar(64) not null,
  assessment_id varchar(128) not null,
  from_mode varchar(32) not null,
  target_mode varchar(32) not null,
  assessment_status varchar(24) not null,
  sample_count bigint not null,
  mismatch_count bigint not null,
  critical_mismatch_count bigint not null,
  legacy_unavailable_count bigint not null,
  mismatch_rate_bps integer not null,
  legacy_unavailable_rate_bps integer not null,
  blocking_reasons varchar(160)[] not null default '{}',
  window_started_at timestamptz not null,
  window_ended_at timestamptz not null,
  assessed_at timestamptz not null,
  assessed_by varchar(128) not null,
  correlation_id varchar(128) not null,
  primary key(tenant_id,assessment_id),
  foreign key(tenant_id) references tenants(tenant_id),
  check(from_mode in ('OFF','SHADOW','READ_ENFORCE','WRITE_ENFORCE','FULL_ENFORCE')),
  check(target_mode in ('OFF','SHADOW','READ_ENFORCE','WRITE_ENFORCE','FULL_ENFORCE')),
  check(assessment_status in ('PENDING','PASSED','BLOCKED','ROLLED_BACK')),
  check(sample_count>=0 and mismatch_count>=0 and critical_mismatch_count>=0 and legacy_unavailable_count>=0),
  check(mismatch_rate_bps between 0 and 10000),
  check(legacy_unavailable_rate_bps between 0 and 10000),
  check(window_ended_at>=window_started_at),
  check((assessment_status='PASSED' and cardinality(blocking_reasons)=0) or assessment_status<>'PASSED')
);

create table if not exists resource_access_release_evidence (
  tenant_id varchar(64) not null,
  evidence_id varchar(128) not null,
  evidence_type varchar(64) not null,
  evidence_status varchar(24) not null,
  command_name varchar(200) not null,
  artifact_ref text not null default '',
  artifact_sha256 varchar(64) not null default '',
  summary text not null default '',
  started_at timestamptz not null,
  completed_at timestamptz not null,
  actor_id varchar(128) not null,
  correlation_id varchar(128) not null,
  created_at timestamptz not null default now(),
  primary key(tenant_id,evidence_id),
  foreign key(tenant_id) references tenants(tenant_id),
  check(evidence_type in ('MAVEN_FULL_REACTOR','CLEAN_MIGRATION','UPGRADE_MIGRATION','FORCE_RLS','ADMIN_UI_TYPECHECK','ADMIN_UI_PRODUCTION_BUILD','BROWSER_E2E','PERFORMANCE','SECURITY_CONCURRENCY','ROLLBACK_REHEARSAL','LEGACY_BYPASS_INVENTORY')),
  check(evidence_status in ('PASS','FAIL','BLOCKED','NOT_RUN')),
  check(completed_at>=started_at),
  check(artifact_sha256='' or artifact_sha256~'^[0-9a-f]{64}$')
);

create table if not exists resource_access_legacy_bypass_inventory (
  tenant_id varchar(64) not null,
  bypass_id varchar(128) not null,
  component varchar(160) not null,
  path_pattern text not null,
  owner_id varchar(128) not null,
  rationale text not null,
  replacement_ref text not null,
  bypass_status varchar(24) not null,
  expires_at timestamptz,
  version bigint not null default 0,
  created_at timestamptz not null default now(),
  created_by varchar(128) not null,
  updated_at timestamptz not null default now(),
  updated_by varchar(128) not null,
  primary key(tenant_id,bypass_id),
  foreign key(tenant_id) references tenants(tenant_id),
  check(bypass_status in ('ACTIVE','REMOVED','EXPIRED')),
  check((bypass_status='ACTIVE' and expires_at is not null) or bypass_status<>'ACTIVE')
);

create table if not exists resource_access_rollout_transitions (
  tenant_id varchar(64) not null,
  transition_id varchar(128) not null,
  from_mode varchar(32) not null,
  to_mode varchar(32) not null,
  assessment_id varchar(128) not null,
  actor_id varchar(128) not null,
  reason text not null,
  correlation_id varchar(128) not null,
  transitioned_at timestamptz not null,
  primary key(tenant_id,transition_id),
  foreign key(tenant_id,assessment_id) references resource_access_release_assessments(tenant_id,assessment_id),
  check(from_mode in ('OFF','SHADOW','READ_ENFORCE','WRITE_ENFORCE','FULL_ENFORCE')),
  check(to_mode in ('OFF','SHADOW','READ_ENFORCE','WRITE_ENFORCE','FULL_ENFORCE')),
  check((from_mode='OFF' and to_mode='SHADOW') or
        (from_mode='SHADOW' and to_mode='READ_ENFORCE') or
        (from_mode='READ_ENFORCE' and to_mode='WRITE_ENFORCE') or
        (from_mode='WRITE_ENFORCE' and to_mode='FULL_ENFORCE'))
);

create table if not exists resource_access_rollback_rehearsals (
  tenant_id varchar(64) not null,
  rehearsal_id varchar(128) not null,
  from_mode varchar(32) not null,
  to_mode varchar(32) not null,
  rehearsal_status varchar(24) not null,
  incident_id varchar(128) not null,
  artifact_ref text not null,
  actor_id varchar(128) not null,
  correlation_id varchar(128) not null,
  rehearsed_at timestamptz not null,
  primary key(tenant_id,rehearsal_id),
  foreign key(tenant_id) references tenants(tenant_id),
  check(from_mode in ('SHADOW','READ_ENFORCE','WRITE_ENFORCE','FULL_ENFORCE')),
  check(to_mode in ('OFF','SHADOW','READ_ENFORCE','WRITE_ENFORCE')),
  check(rehearsal_status in ('PASS','FAIL')),
  check((from_mode='FULL_ENFORCE' and to_mode in ('WRITE_ENFORCE','READ_ENFORCE','SHADOW','OFF')) or
        (from_mode='WRITE_ENFORCE' and to_mode in ('READ_ENFORCE','SHADOW','OFF')) or
        (from_mode='READ_ENFORCE' and to_mode in ('SHADOW','OFF')) or
        (from_mode='SHADOW' and to_mode='OFF'))
);

create index if not exists idx_p4ra_i_assessment_window on resource_access_release_assessments(tenant_id,target_mode,assessed_at desc);
create index if not exists idx_p4ra_i_evidence_latest on resource_access_release_evidence(tenant_id,evidence_type,completed_at desc);
create index if not exists idx_p4ra_i_active_bypass on resource_access_legacy_bypass_inventory(tenant_id,bypass_status,expires_at);
create index if not exists idx_p4ra_i_rollout on resource_access_rollout_transitions(tenant_id,transitioned_at desc);
create index if not exists idx_p4ra_i_rollback on resource_access_rollback_rehearsals(tenant_id,rehearsed_at desc);

-- Assessments, evidence, rollout and rollback are immutable certification ledgers.
drop trigger if exists trg_p4ra_i_assessment_immutable on resource_access_release_assessments;
create trigger trg_p4ra_i_assessment_immutable before update or delete on resource_access_release_assessments for each row execute function p4ra_reject_append_only_mutation();
drop trigger if exists trg_p4ra_i_evidence_immutable on resource_access_release_evidence;
create trigger trg_p4ra_i_evidence_immutable before update or delete on resource_access_release_evidence for each row execute function p4ra_reject_append_only_mutation();
drop trigger if exists trg_p4ra_i_rollout_immutable on resource_access_rollout_transitions;
create trigger trg_p4ra_i_rollout_immutable before update or delete on resource_access_rollout_transitions for each row execute function p4ra_reject_append_only_mutation();
drop trigger if exists trg_p4ra_i_rollback_immutable on resource_access_rollback_rehearsals;
create trigger trg_p4ra_i_rollback_immutable before update or delete on resource_access_rollback_rehearsals for each row execute function p4ra_reject_append_only_mutation();

do $$
declare table_name text;
begin
 foreach table_name in array array[
  'resource_access_shadow_acceptance_thresholds','resource_access_release_assessments',
  'resource_access_release_evidence','resource_access_legacy_bypass_inventory',
  'resource_access_rollout_transitions','resource_access_rollback_rehearsals'
 ] loop
  execute format('alter table %I enable row level security',table_name);
  execute format('alter table %I force row level security',table_name);
  execute format('drop policy if exists tenant_isolation on %I',table_name);
  execute format('create policy tenant_isolation on %I using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id())',table_name);
 end loop;
end $$;

insert into permission_point_catalog(permission_point,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed)
values
 ('resource.release.read','RESOURCE_DECISION','READ','Read Resource Access release evidence and rollout readiness.','CRITICAL',array['TENANT'],true),
 ('resource.release.assess','RESOURCE_DECISION','EXECUTE','Create a Resource Access release assessment from immutable shadow evidence.','CRITICAL',array['TENANT'],true),
 ('resource.release.transition','RESOURCE_DECISION','UPDATE','Record an approved forward Resource Access enforcement transition.','CRITICAL',array['TENANT'],true),
 ('resource.release.rollback','RESOURCE_DECISION','UPDATE','Record a Resource Access rollback rehearsal or execution.','CRITICAL',array['TENANT'],true)
on conflict(permission_point) do update set resource_type=excluded.resource_type,action_code=excluded.action_code,description=excluded.description,risk_level=excluded.risk_level,allowed_scope_types=excluded.allowed_scope_types,system_managed=true,active=true,version=permission_point_catalog.version+1;

insert into reason_code_catalog(reason_code,http_status,category,retryable,message_template)
values
 ('SHADOW_OBSERVATION_WINDOW_TOO_SHORT',409,'RELEASE_GATE',true,'The shadow observation window is shorter than the approved threshold.'),
 ('SHADOW_SAMPLE_SIZE_INSUFFICIENT',409,'RELEASE_GATE',true,'The shadow sample size is below the approved minimum.'),
 ('CRITICAL_SHADOW_MISMATCH_PRESENT',409,'RELEASE_GATE',false,'A release-blocking shadow mismatch is present.'),
 ('AUTHORIZATION_ERROR_PRESENT',409,'RELEASE_GATE',true,'Authorization error evidence blocks rollout.'),
 ('SHADOW_MISMATCH_RATE_EXCEEDED',409,'RELEASE_GATE',true,'The total shadow mismatch rate exceeds the approved threshold.'),
 ('LEGACY_UNAVAILABLE_RATE_EXCEEDED',409,'RELEASE_GATE',true,'Legacy decision unavailability exceeds the approved threshold.'),
 ('CERTIFICATION_NOT_PASSED',409,'RELEASE_GATE',true,'A mandatory certification lane is not passed.'),
 ('ACTIVE_LEGACY_BYPASS_PRESENT',409,'RELEASE_GATE',false,'Full enforcement is blocked while a legacy bypass remains active.'),
 ('PASSED_RELEASE_ASSESSMENT_REQUIRED',409,'RELEASE_GATE',false,'A passed immutable release assessment is required.'),
 ('INVALID_RESOURCE_ACCESS_FORWARD_TRANSITION',409,'RELEASE_GATE',false,'The requested forward enforcement transition is invalid.'),
 ('INVALID_RESOURCE_ACCESS_ROLLBACK_TRANSITION',409,'RELEASE_GATE',false,'The requested rollback transition is invalid.')
on conflict(reason_code) do update set http_status=excluded.http_status,category=excluded.category,retryable=excluded.retryable,message_template=excluded.message_template,active=true,version=reason_code_catalog.version+1;

comment on table resource_access_release_assessments is 'Immutable P4RA-I shadow acceptance assessment; never an authorization token.';
comment on table resource_access_release_evidence is 'Immutable command/artifact evidence for build, migration, RLS, UI, browser, performance, security and rollback lanes.';
comment on table resource_access_rollout_transitions is 'Approved monotonic OFF to FULL_ENFORCE rollout transition evidence; configuration changes remain external deployment actions.';
comment on table resource_access_rollback_rehearsals is 'Immutable rollback rehearsal evidence requiring incident and artifact references.';
