-- Phase 10 clean baseline: single Dispatch Flow Direct Delivery schema.
-- This project has not gone live; historical Flyway migrations were intentionally squashed.
-- Only the standard path is modeled:
-- source_systems -> dispatch_flows(Source Flow) -> dispatch_policies(known-event override rules)
-- -> agent_pools/work queues -> agent_pool_members -> agents -> tasks -> task_assignments -> dispatch_requests.
-- Capability rows are retained as Agent metadata / compatibility records only; Phase 32-B
-- does not allow Capability to become a first-version routing gate.

create table if not exists source_systems (
  tenant_id varchar(64) not null,
  source_system_id varchar(128) not null,
  display_name varchar(255) not null,
  description text,
  status varchar(32) not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, source_system_id)
);

create table if not exists agents (
  agent_id varchar(128) primary key,
  agent_type varchar(128),
  owner_gateway_node_id varchar(128),
  agent_session_id varchar(128),
  site_id varchar(128),
  site_name varchar(255),
  region varchar(128),
  zone varchar(128),
  status varchar(32) not null default 'OFFLINE',
  capabilities_json jsonb not null default '[]'::jsonb,
  current_task_count int not null default 0,
  reserved_task_count int not null default 0,
  max_concurrent_tasks int not null default 1,
  health_score numeric(8,2),
  capability_profile_json jsonb not null default '{}'::jsonb,
  runtime_load_json jsonb not null default '{}'::jsonb,
  plugin_name varchar(128),
  plugin_version varchar(255),
  capability_revision varchar(255),
  available_slots int,
  capacity_utilization numeric(8,4),
  outbox_pending int,
  outbox_in_flight int,
  recovery_pending_assignments int,
  draining boolean not null default false,
  connected_at timestamptz,
  last_heartbeat_at timestamptz,
  disconnected_at timestamptz,
  lease_expires_at timestamptz,
  runtime_backoff_until timestamptz,
  runtime_backoff_reason text,
  runtime_failure_count int not null default 0,
  updated_at timestamptz not null default now()
);

create table if not exists gateway_nodes (
  gateway_node_id varchar(128) primary key,
  display_name varchar(255),
  node_name varchar(255),
  host_name varchar(255),
  advertise_host varchar(255),
  http_port int,
  ws_port int,
  region varchar(64),
  zone varchar(64),
  site_id varchar(128),
  status varchar(32) not null default 'ONLINE',
  version varchar(64) not null default 'unknown',
  metadata_json jsonb not null default '{}'::jsonb,
  registered_at timestamptz,
  last_heartbeat_at timestamptz,
  lease_expires_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists agent_enrollment_requests (
  enrollment_id varchar(128) primary key,
  claimed_agent_id varchar(128),
  tenant_id varchar(64) not null,
  agent_name varchar(255),
  agent_type varchar(64),
  submitted_metadata_json jsonb not null default '{}'::jsonb,
  evidence_json jsonb not null default '{}'::jsonb,
  fingerprint varchar(255),
  remote_address varchar(255),
  status varchar(32) not null default 'PENDING',
  submitted_at timestamptz,
  reviewed_by varchar(128),
  reviewed_at timestamptz,
  review_comment text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists agent_profiles (
  agent_id varchar(128) primary key,
  tenant_id varchar(64) not null,
  agent_name varchar(255),
  agent_type varchar(64),
  owner_team varchar(128),
  description text,
  approval_status varchar(32) not null default 'PENDING',
  enabled boolean not null default false,
  risk_status varchar(32) not null default 'NORMAL',
  policy_version int not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists idx_agent_profiles_tenant_status on agent_profiles(tenant_id, approval_status, enabled);

-- Phase 32-B: Agent Pool / Work Queue persistence.
-- Pools are the first-version dispatch targets. A Source Flow resolves to a Pool,
-- then runtime selection chooses an Agent from active Pool members.
create table if not exists agent_pools (
  tenant_id varchar(64) not null,
  pool_id varchar(128) not null,
  pool_code varchar(128) not null,
  pool_name varchar(255) not null,
  source_system varchar(128),
  pool_type varchar(32) not null default 'RESOLUTION',
  selection_strategy varchar(32) not null default 'LOWEST_LOAD',
  status varchar(32) not null default 'ACTIVE',
  description text,
  metadata_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, pool_id),
  unique (tenant_id, pool_code)
);
create index if not exists idx_agent_pools_source_status on agent_pools(tenant_id, source_system, status, pool_type, updated_at desc);

create table if not exists agent_pool_members (
  tenant_id varchar(64) not null,
  pool_id varchar(128) not null,
  agent_id varchar(128) not null,
  member_status varchar(32) not null default 'ACTIVE',
  priority int not null default 100,
  weight int not null default 1,
  metadata_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, pool_id, agent_id)
);
create index if not exists idx_agent_pool_members_agent on agent_pool_members(tenant_id, agent_id, member_status, updated_at desc);
create index if not exists idx_agent_pool_members_pool on agent_pool_members(tenant_id, pool_id, member_status, priority, weight);

create table if not exists agent_credentials (
  credential_id varchar(128) primary key,
  agent_id varchar(128) not null,
  credential_type varchar(64) not null,
  public_key_fingerprint varchar(255),
  token_hash varchar(255),
  credential_version int not null default 1,
  issued_at timestamptz,
  expires_at timestamptz,
  revoked_at timestamptz,
  revoked_reason text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists idx_agent_credentials_active on agent_credentials(agent_id, revoked_at, expires_at);

create table if not exists agent_capabilities (
  agent_id varchar(128) not null,
  capability_code varchar(128) not null,
  capability_version varchar(64),
  enabled boolean not null default true,
  approved_by varchar(128),
  approved_at timestamptz,
  primary key (agent_id, capability_code)
);


-- Agent governance support tables used by AgentGovernanceDao.xml during the
-- standard enrollment/approval path. These are audit/security/support records,
-- not dispatch routing authorities.
create table if not exists agent_authorization_scopes (
  scope_id varchar(128) primary key,
  agent_id varchar(128) not null,
  tenant_id varchar(64) not null,
  system_code varchar(128),
  site_code varchar(128),
  event_type varchar(128),
  task_type varchar(128),
  data_classification_limit varchar(128),
  enabled boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists idx_agent_authorization_scopes_agent_enabled on agent_authorization_scopes(agent_id, enabled);
create index if not exists idx_agent_authorization_scopes_tenant_system on agent_authorization_scopes(tenant_id, system_code, task_type);

create table if not exists agent_approval_audit (
  audit_id varchar(128) primary key,
  agent_id varchar(128),
  enrollment_id varchar(128),
  action varchar(64) not null,
  old_status varchar(64),
  new_status varchar(64),
  operator_id varchar(128),
  reason text,
  created_at timestamptz not null default now()
);
create index if not exists idx_agent_approval_audit_agent_created on agent_approval_audit(agent_id, created_at desc);
create index if not exists idx_agent_approval_audit_enrollment_created on agent_approval_audit(enrollment_id, created_at desc);

create table if not exists agent_security_events (
  security_event_id varchar(128) primary key,
  gateway_node_id varchar(128),
  claimed_agent_id varchar(128),
  agent_id varchar(128),
  event_type varchar(128) not null,
  reason text,
  fingerprint varchar(255),
  remote_address varchar(255),
  metadata_json jsonb not null default '{}'::jsonb,
  occurred_at timestamptz,
  created_at timestamptz not null default now()
);
create index if not exists idx_agent_security_events_agent_created on agent_security_events(agent_id, created_at desc);
create index if not exists idx_agent_security_events_claimed_created on agent_security_events(claimed_agent_id, created_at desc);
create index if not exists idx_agent_security_events_gateway_created on agent_security_events(gateway_node_id, created_at desc);

create table if not exists agent_security_enforcement_policies (
  policy_id varchar(128) primary key,
  agent_id varchar(128) not null,
  enabled boolean not null default true,
  duplicate_runtime_mode varchar(64) not null default 'WARN',
  require_credential_rotation boolean not null default false,
  notify_email boolean not null default false,
  notify_slack boolean not null default false,
  notify_siem boolean not null default false,
  email_recipients_json jsonb not null default '[]'::jsonb,
  slack_channels_json jsonb not null default '[]'::jsonb,
  siem_topics_json jsonb not null default '[]'::jsonb,
  metadata_json jsonb not null default '{}'::jsonb,
  updated_by varchar(128),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(agent_id)
);
create index if not exists idx_agent_security_enforcement_policies_enabled on agent_security_enforcement_policies(enabled, updated_at desc);

create table if not exists agent_capability_catalog (
  tenant_id varchar(64) not null,
  capability_id varchar(128) not null,
  capability_code varchar(128) not null,
  capability_name varchar(255),
  category varchar(128),
  capability_type varchar(64),
  domain varchar(128),
  resource_type varchar(128),
  operation varchar(128),
  data_class varchar(128),
  service_level varchar(64),
  description text,
  risk_level varchar(32),
  status varchar(32) not null default 'ACTIVE',
  version int not null default 1,
  owner_team varchar(128),
  requires_approval boolean not null default false,
  requires_runtime_probe boolean not null default false,
  is_dispatch_eligible boolean not null default true,
  effective_from timestamptz,
  retired_at timestamptz,
  metadata_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, capability_code),
  unique (tenant_id, capability_id)
);

create table if not exists agent_capability_assignments (
  tenant_id varchar(64) not null,
  assignment_id varchar(128) primary key,
  agent_id varchar(128) not null,
  capability_code varchar(128) not null,
  capability_name varchar(255),
  status varchar(32) not null default 'REQUESTED',
  source varchar(64),
  requested_by varchar(128),
  requested_at timestamptz,
  approved_by varchar(128),
  approved_at timestamptz,
  revoked_by varchar(128),
  revoked_at timestamptz,
  expires_at timestamptz,
  evidence_ref text,
  reason text,
  metadata_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, agent_id, capability_code)
);
create index if not exists idx_agent_capability_assignments_agent on agent_capability_assignments(agent_id, status);

create table if not exists runtime_resources (
  tenant_id varchar(64) not null,
  runtime_id varchar(128) not null,
  runtime_type varchar(64),
  runtime_name varchar(255),
  status varchar(32) not null default 'ACTIVE',
  metadata_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, runtime_id)
);

create table if not exists agent_runtime_bindings (
  tenant_id varchar(64) not null,
  binding_id varchar(128) primary key,
  agent_id varchar(128) not null,
  runtime_id varchar(128),
  runtime_type varchar(64),
  binding_status varchar(32) not null default 'ACTIVE',
  activated_at timestamptz,
  paused_at timestamptz,
  revoked_at timestamptz,
  metadata_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create unique index if not exists uq_agent_runtime_bindings_active on agent_runtime_bindings(tenant_id, agent_id) where binding_status='ACTIVE';

create table if not exists dispatch_flows (
  tenant_id varchar(64) not null,
  flow_id varchar(128) not null,
  flow_code varchar(128) not null,
  flow_name varchar(255) not null,
  source_system varchar(128) not null,
  flow_type varchar(32) not null default 'SOURCE_FLOW',
  default_pool_id varchar(128),
  status varchar(32) not null default 'DRAFT',
  description text,
  default_capability_requirement_mode varchar(32) not null default 'NONE',
  default_required_operation varchar(128),
  default_side_effect_level varchar(32) not null default 'NONE',
  default_candidate_pool_mode varchar(32) not null default 'EXPLICIT_FLOW_AGENTS',
  default_routing_strategy varchar(32) not null default 'WEIGHTED_SCORE',
  metadata_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, flow_id),
  unique (tenant_id, flow_code)
);
create index if not exists idx_dispatch_flows_source_status on dispatch_flows(tenant_id, source_system, status, updated_at desc);
create index if not exists idx_dispatch_flows_default_pool on dispatch_flows(tenant_id, default_pool_id, status);

create table if not exists dispatch_policies (
  tenant_id varchar(64) not null,
  policy_id varchar(128) primary key,
  policy_code varchar(128) not null,
  policy_name varchar(255) not null,
  description text,
  owner_team varchar(128),
  risk_level varchar(32),
  status varchar(32) not null default 'DRAFT',
  version int not null default 1,
  effective_from timestamptz,
  retired_at timestamptz,
  metadata_json jsonb not null default '{}'::jsonb,
  flow_id varchar(128) not null,
  rule_scope varchar(64),
  event_stage varchar(32) not null default 'EXTERNAL',
  source_system varchar(128) not null,
  origin_source_system varchar(128),
  target_system varchar(128),
  event_type varchar(128),
  object_type varchar(128),
  error_code varchar(128),
  condition_json jsonb not null default '{}'::jsonb,
  priority int not null default 100,
  match_mode varchar(32) not null default 'EXACT_OR_WILDCARD',
  target_pool_id varchar(128),
  target_pool_code varchar(128),
  requested_skill varchar(128),
  capability_requirement_mode varchar(32) not null default 'NONE',
  required_operation varchar(128),
  side_effect_level varchar(32) not null default 'NONE',
  candidate_pool_mode varchar(32) not null default 'EXPLICIT_FLOW_AGENTS',
  routing_strategy varchar(32) not null default 'WEIGHTED_SCORE',
  explicit_action_authorization_required boolean not null default false,
  requirement_model_version int not null default 1,
  handoff_mode varchar(32),
  issue_policy_id varchar(128),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists idx_dispatch_policies_flow on dispatch_policies(tenant_id, flow_id, status);
create index if not exists idx_dispatch_policies_match on dispatch_policies(tenant_id, source_system, event_stage, object_type, event_type, error_code, status);
create index if not exists idx_dispatch_policies_target_pool on dispatch_policies(tenant_id, target_pool_id, status, priority);

-- Phase 32-B compatibility view: the product model calls these Flow Rules,
-- while the historical physical table remains dispatch_policies in this clean baseline.
create or replace view dispatch_flow_rules as
select
  tenant_id,
  policy_id as rule_id,
  policy_code as rule_code,
  policy_name as rule_name,
  flow_id,
  rule_scope,
  event_stage,
  source_system,
  origin_source_system,
  target_system,
  object_type,
  event_type,
  error_code,
  condition_json,
  priority,
  match_mode,
  target_pool_id,
  target_pool_code,
  requested_skill,
  capability_requirement_mode,
  candidate_pool_mode,
  routing_strategy,
  status,
  metadata_json,
  created_at,
  updated_at
from dispatch_policies;


create table if not exists dispatch_cutover_policies (
  tenant_id varchar(64) not null,
  policy_id varchar(128) not null,
  flow_id varchar(128) not null default '*',
  mode varchar(32) not null default 'AUTHORITATIVE',
  canary_percentage int not null default 100,
  minimum_sample_size int not null default 50,
  maximum_requirement_blocked_rate double precision not null default 0.05,
  maximum_no_candidate_rate double precision not null default 0.10,
  maximum_selection_difference_rate double precision not null default 0.20,
  auto_rollback_enabled boolean not null default false,
  status varchar(32) not null default 'ACTIVE',
  version int not null default 1,
  rolled_back_at timestamptz,
  rollback_reason text,
  created_at timestamptz not null default now(),
  created_by varchar(128),
  updated_at timestamptz not null default now(),
  updated_by varchar(128),
  primary key (tenant_id, policy_id),
  unique (tenant_id, flow_id)
);
create index if not exists idx_dispatch_cutover_policies_effective on dispatch_cutover_policies(tenant_id, flow_id, status, updated_at desc);

create table if not exists dispatch_cutover_task_decisions (
  tenant_id varchar(64) not null,
  decision_id varchar(128) not null,
  task_id varchar(128) not null,
  flow_id varchar(128) not null,
  policy_id varchar(128),
  configured_mode varchar(32) not null,
  authoritative boolean not null default false,
  deterministic_bucket int not null default 0,
  reason_code varchar(128),
  created_at timestamptz not null default now(),
  primary key (tenant_id, decision_id),
  unique (tenant_id, task_id, flow_id)
);
create index if not exists idx_dispatch_cutover_task_decisions_task on dispatch_cutover_task_decisions(tenant_id, task_id, flow_id, created_at desc);

create table if not exists dispatch_cutover_outcomes (
  tenant_id varchar(64) not null,
  outcome_id varchar(128) not null,
  task_id varchar(128) not null,
  flow_id varchar(128) not null,
  policy_id varchar(128),
  authoritative boolean not null default false,
  requirement_blocked boolean not null default false,
  no_candidate boolean not null default false,
  selected_agent_different boolean not null default false,
  selected_agent_id varchar(128),
  legacy_selected_agent_id varchar(128),
  reason_code varchar(128),
  created_at timestamptz not null default now(),
  primary key (tenant_id, outcome_id),
  unique (tenant_id, task_id, flow_id, authoritative)
);
create index if not exists idx_dispatch_cutover_outcomes_flow on dispatch_cutover_outcomes(tenant_id, flow_id, authoritative, created_at desc);

create or replace view dispatch_p10_cutover_readiness as
select
  tenant_id,
  flow_id,
  count(*)::bigint as sample_size,
  count(*) filter (where authoritative)::bigint as authoritative_sample_size,
  count(*) filter (where not authoritative)::bigint as control_sample_size,
  count(*) filter (where requirement_blocked)::bigint as requirement_blocked_count,
  count(*) filter (where no_candidate)::bigint as no_candidate_count,
  count(*) filter (where selected_agent_different)::bigint as selection_difference_count,
  coalesce((count(*) filter (where requirement_blocked))::double precision / nullif(count(*), 0), 0.0) as requirement_blocked_rate,
  coalesce((count(*) filter (where no_candidate))::double precision / nullif(count(*), 0), 0.0) as no_candidate_rate,
  coalesce((count(*) filter (where selected_agent_different))::double precision / nullif(count(*) filter (where authoritative), 0), 0.0) as selection_difference_rate,
  (count(*) filter (where authoritative) > 0) as authoritative_metrics_available
from dispatch_cutover_outcomes
group by tenant_id, flow_id;


create table if not exists dispatch_operator_incidents (
  incident_id varchar(128) primary key,
  trigger_code varchar(128),
  severity varchar(32),
  task_id varchar(128),
  agent_id varchar(128),
  message text,
  metadata_json jsonb not null default '{}'::jsonb,
  status varchar(32) not null default 'OPEN',
  created_at timestamptz not null default now(),
  created_by varchar(128),
  updated_at timestamptz not null default now(),
  updated_by varchar(128)
);
create index if not exists idx_dispatch_operator_incidents_status on dispatch_operator_incidents(status, created_at desc);

create table if not exists dispatch_release_artifacts (
  artifact_id varchar(128) primary key,
  artifact_name varchar(255),
  artifact_path text,
  source varchar(128),
  generated_at timestamptz not null default now(),
  retained_until timestamptz,
  metadata_json jsonb not null default '{}'::jsonb
);
create index if not exists idx_dispatch_release_artifacts_source_time on dispatch_release_artifacts(source, generated_at desc);

create table if not exists flow_required_capabilities (
  tenant_id varchar(64) not null,
  id varchar(128) not null,
  flow_id varchar(128) not null,
  rule_id varchar(128),
  event_stage varchar(32) not null default 'EXTERNAL',
  agent_role varchar(128),
  skill_code varchar(128) not null,
  authority_code varchar(128),
  required boolean not null default true,
  metadata_json jsonb not null default '{}'::jsonb,
  skill_name varchar(255),
  skill_kind varchar(64),
  openclaw_skill boolean not null default false,
  description text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, id)
);
create index if not exists idx_flow_required_capabilities_flow on flow_required_capabilities(tenant_id, flow_id, rule_id, skill_code);

create table if not exists flow_agent_assignments (
  tenant_id varchar(64) not null,
  id varchar(128) not null,
  flow_id varchar(128) not null,
  agent_id varchar(128) not null,
  agent_name varchar(255),
  event_stage varchar(32) not null default 'EXTERNAL',
  agent_role varchar(128),
  assignment_status varchar(32) not null default 'ACTIVE',
  runtime_status varchar(32),
  approval_status varchar(32),
  skill_coverage_total int not null default 0,
  skill_coverage_matched int not null default 0,
  missing_skills_json jsonb not null default '[]'::jsonb,
  missing_authorities_json jsonb not null default '[]'::jsonb,
  readiness_status varchar(32),
  metadata_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, id),
  unique (tenant_id, flow_id, agent_id, event_stage, agent_role)
);
create index if not exists idx_flow_agent_assignments_flow on flow_agent_assignments(tenant_id, flow_id, assignment_status, agent_id);

create table if not exists incidents (
  incident_id varchar(128) primary key,
  fingerprint varchar(255),
  tenant_id varchar(64),
  source_system varchar(128),
  site_id varchar(128),
  plant_id varchar(128),
  object_type varchar(128),
  object_id varchar(128),
  event_type varchar(128),
  error_code varchar(128),
  severity varchar(32),
  status varchar(32) not null default 'ACTIVE',
  first_seen_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now(),
  occurrence_count int not null default 1,
  last_message text,
  linked_task_id varchar(128),
  linked_issue_id varchar(128),
  resolved_at timestamptz,
  reopened_at timestamptz,
  reopen_count int not null default 0,
  lifecycle_reason varchar(128),
  source_event_id varchar(128),
  occurred_at timestamptz,
  metadata_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists idx_incidents_active_last_seen on incidents(status, last_seen_at);
create index if not exists idx_incidents_fingerprint_last_seen on incidents(fingerprint, last_seen_at desc);
create index if not exists idx_incidents_tenant_status on incidents(tenant_id, status, last_seen_at desc);

create table if not exists tasks (
  task_id varchar(128) primary key,
  incident_id varchar(128),
  source_event_id varchar(128),
  source_system varchar(128),
  event_stage varchar(32),
  origin_source_system varchar(128),
  target_system varchar(128),
  task_type varchar(128),
  task_type_code varchar(128),
  status varchar(32) not null default 'CREATED',
  priority varchar(32),
  tenant_id varchar(64),
  site_id varchar(128),
  plant_id varchar(128),
  object_type varchar(128),
  object_id varchar(128),
  event_type varchar(128),
  error_code varchar(128),
  requested_skill varchar(128),
  handoff_mode varchar(32),
  correlation_id varchar(128),
  parent_task_id varchar(128),
  matched_flow_id varchar(128),
  matched_rule_id varchar(128),
  assigned_pool_id varchar(128),
  target_pool_id varchar(128),
  classification_status varchar(32) not null default 'CLASSIFIED',
  classification_result_json jsonb not null default '{}'::jsonb,
  routing_path varchar(128),
  routing_policy varchar(128),
  required_capabilities_json jsonb not null default '[]'::jsonb,
  created_reason text,
  occurrence_count_at_creation int,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  timeout_at timestamptz,
  terminal_at timestamptz,
  reassignment_count int not null default 0,
  next_dispatch_attempt_at timestamptz,
  dispatch_attempt_count int not null default 0,
  dispatch_retry_reason text,
  dispatch_recovery_claimed_by varchar(128),
  dispatch_recovery_claim_until timestamptz,
  lifecycle_reason text,
  external_execution_key varchar(255)
);
create unique index if not exists uq_tasks_open_incident_type on tasks(incident_id, task_type) where status not in ('SUCCEEDED','FAILED','ESCALATED','DEAD_LETTER','SUPPRESSED','COMPLETED','TIMED_OUT','CANCELLED');
create index if not exists idx_tasks_tenant_status on tasks(tenant_id, status, updated_at);
create index if not exists idx_tasks_assigned_pool on tasks(tenant_id, assigned_pool_id, status, updated_at desc);
create index if not exists idx_tasks_classification_status on tasks(tenant_id, classification_status, updated_at desc);

create table if not exists task_assignments (
  assignment_id varchar(128) primary key,
  task_id varchar(128) not null,
  incident_id varchar(128),
  agent_id varchar(128) not null,
  agent_type varchar(64),
  owner_gateway_node_id varchar(128),
  agent_session_id varchar(128),
  site_id varchar(128),
  event_stage varchar(32),
  origin_source_system varchar(128),
  target_system varchar(128),
  requested_skill varchar(128),
  correlation_id varchar(128),
  parent_task_id varchar(128),
  handoff_mode varchar(32),
  matched_flow_id varchar(128),
  matched_rule_id varchar(128),
  assigned_pool_id varchar(128),
  target_pool_id varchar(128),
  routing_path varchar(128),
  status varchar(32) not null default 'ASSIGNED',
  routing_policy varchar(128),
  routing_decision_id varchar(128),
  dispatch_attempt_id varchar(128),
  lease_id varchar(128),
  fencing_token varchar(128),
  lease_expires_at timestamptz,
  score numeric(10,4),
  reason text,
  capacity_reserved boolean not null default false,
  capacity_reserved_at timestamptz,
  capacity_released_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists idx_task_assignments_task on task_assignments(task_id, status, created_at desc);

create table if not exists dispatch_requests (
  dispatch_request_id varchar(128) primary key,
  assignment_id varchar(128),
  task_id varchar(128),
  incident_id varchar(128),
  agent_id varchar(128),
  owner_gateway_node_id varchar(128),
  agent_session_id varchar(128),
  site_id varchar(128),
  status varchar(32) not null default 'APPROVED',
  review_mode varchar(32),
  eligibility_status varchar(32),
  dispatch_method varchar(64),
  gateway_dispatch_path varchar(128),
  dispatch_token varchar(255),
  reason text,
  command_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  approved_at timestamptz,
  dispatched_at timestamptz,
  failed_at timestamptz,
  attempt_count int not null default 0,
  last_error text,
  last_callback_id varchar(128),
  completed_at timestamptz,
  timed_out_at timestamptz,
  retry_waiting_at timestamptz,
  next_retry_at timestamptz,
  dead_letter_at timestamptz,
  claimed_by varchar(128),
  claim_started_at timestamptz,
  claim_until timestamptz
);
create index if not exists idx_dispatch_requests_status on dispatch_requests(status, updated_at);
create index if not exists idx_dispatch_requests_task on dispatch_requests(task_id, created_at desc);

create table if not exists routing_decisions (
  decision_id varchar(128) primary key,
  task_id varchar(128),
  tenant_id varchar(64),
  matched_flow_id varchar(128),
  matched_rule_id varchar(128),
  selected_agent_id varchar(128),
  decision_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists task_dispatch_attempts (
  attempt_id varchar(128) primary key,
  task_id varchar(128),
  assignment_id varchar(128),
  dispatch_request_id varchar(128),
  status varchar(32),
  reason text,
  metadata_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists task_execution_attempts (
  attempt_id varchar(128) primary key,
  task_id varchar(128),
  assignment_id varchar(128),
  dispatch_request_id varchar(128),
  status varchar(32),
  result_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists task_callbacks (
  callback_id varchar(128) primary key,
  callback_type varchar(64) not null,
  task_id varchar(128),
  dispatch_request_id varchar(128),
  assignment_id varchar(128),
  agent_id varchar(128),
  owner_gateway_node_id varchar(128),
  agent_session_id varchar(128),
  attempt_no int,
  fencing_token varchar(255),
  accepted boolean,
  ignored_reason varchar(128),
  message text,
  progress_percent int,
  error_code varchar(128),
  error_message text,
  payload_json jsonb not null default '{}'::jsonb,
  occurred_at timestamptz,
  processed_at timestamptz not null default now(),
  duplicate boolean not null default false,
  idempotency_key varchar(255),
  callback_fingerprint varchar(255),
  replay_detected boolean not null default false,
  previous_task_status varchar(64),
  new_task_status varchar(64),
  previous_dispatch_status varchar(64),
  new_dispatch_status varchar(64),
  status varchar(32),
  received_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists idx_task_callbacks_task on task_callbacks(task_id, processed_at desc);
create index if not exists idx_task_callbacks_dispatch on task_callbacks(dispatch_request_id, processed_at desc);
create index if not exists idx_task_callbacks_type_time on task_callbacks(callback_type, processed_at desc);
create index if not exists idx_task_callbacks_agent_time on task_callbacks(agent_id, processed_at desc);

create table if not exists dispatch_attempt_history (
  attempt_id varchar(128) primary key,
  dispatch_request_id varchar(128),
  task_id varchar(128),
  agent_id varchar(128),
  status varchar(32),
  reason text,
  created_at timestamptz not null default now()
);

create table if not exists task_issue_links (
  link_id varchar(128) primary key,
  task_id varchar(128),
  issue_key varchar(128),
  issue_url text,
  status varchar(32),
  metadata_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists event_decisions (
  decision_id varchar(128) primary key,
  tenant_id varchar(64),
  source_event_id varchar(128),
  source_system varchar(128),
  decision_status varchar(32),
  decision_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists event_dedup_state (
  fingerprint varchar(255) primary key,
  active_incident_id varchar(128),
  tenant_id varchar(64),
  source_system varchar(128),
  site_id varchar(128),
  plant_id varchar(128),
  object_type varchar(128),
  object_id varchar(255),
  event_type varchar(128),
  error_code varchar(128),
  first_seen_at timestamptz,
  last_seen_at timestamptz,
  occurrence_count int not null default 1,
  max_severity varchar(32),
  last_event_id varchar(128),
  last_message text,
  expires_at timestamptz,
  dedup_key varchar(255),
  hit_count int not null default 1,
  snapshot_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists idx_event_dedup_state_tenant_source on event_dedup_state(tenant_id, source_system);
create index if not exists idx_event_dedup_state_active_incident on event_dedup_state(active_incident_id);
create index if not exists idx_event_dedup_state_expires_at on event_dedup_state(expires_at);

create table if not exists module_outbox_events (
  event_id varchar(128) primary key,
  aggregate_type varchar(128),
  aggregate_id varchar(128),
  event_type varchar(128),
  payload_json jsonb not null default '{}'::jsonb,
  status varchar(32) not null default 'PENDING',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists integration_event_outbox (
  event_id varchar(128) primary key,
  aggregate_type varchar(128),
  aggregate_id varchar(128),
  event_type varchar(128),
  payload_json jsonb not null default '{}'::jsonb,
  status varchar(32) not null default 'PENDING',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists adapter_actions (
  action_id varchar(128) primary key,
  tenant_id varchar(64),
  task_id varchar(128),
  status varchar(32),
  action_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists adapter_executor_audit (
  audit_id varchar(128) primary key,
  action_id varchar(128),
  task_id varchar(128),
  executor_id varchar(128),
  status varchar(32),
  audit_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

-- Phase 17 runtime infrastructure schema completion.
-- Clean dispatch baseline must still support active infrastructure schedulers and mappers.
-- These tables/columns are not additional dispatch authorities; they are persistence support
-- for domain event outbox, gateway heartbeat, runtime snapshots, adapter execution, and
-- remediation lease cleanup endpoints still present in the application context.

-- Domain-event outbox mapper contract.
alter table module_outbox_events add column if not exists outbox_id varchar(128);
update module_outbox_events set outbox_id = event_id where outbox_id is null;
alter table module_outbox_events alter column outbox_id set not null;
create unique index if not exists ux_module_outbox_events_outbox_id on module_outbox_events(outbox_id);
alter table module_outbox_events add column if not exists attempt_count int not null default 0;
alter table module_outbox_events add column if not exists next_attempt_at timestamptz;
alter table module_outbox_events add column if not exists last_error text;
alter table module_outbox_events add column if not exists claimed_by varchar(128);
alter table module_outbox_events add column if not exists claim_until timestamptz;
alter table module_outbox_events add column if not exists published_at timestamptz;
create index if not exists idx_module_outbox_dispatchable on module_outbox_events(status, next_attempt_at, claim_until, created_at);

-- Integration-event outbox mapper contract.
alter table integration_event_outbox add column if not exists integration_event_id varchar(128);
update integration_event_outbox set integration_event_id = event_id where integration_event_id is null;
alter table integration_event_outbox alter column integration_event_id set not null;
create unique index if not exists ux_integration_event_outbox_integration_event_id on integration_event_outbox(integration_event_id);
alter table integration_event_outbox add column if not exists envelope_json jsonb not null default '{}'::jsonb;
update integration_event_outbox set envelope_json = payload_json where envelope_json = '{}'::jsonb and payload_json is not null;
alter table integration_event_outbox add column if not exists attempt_count int not null default 0;
alter table integration_event_outbox add column if not exists next_attempt_at timestamptz;
alter table integration_event_outbox add column if not exists last_error text;
alter table integration_event_outbox add column if not exists claimed_by varchar(128);
alter table integration_event_outbox add column if not exists claim_until timestamptz;
alter table integration_event_outbox add column if not exists delivered_at timestamptz;
create index if not exists idx_integration_event_dispatchable on integration_event_outbox(status, next_attempt_at, claim_until, created_at);

-- Gateway directory mapper contract.
alter table gateway_nodes add column if not exists node_name varchar(255);
alter table gateway_nodes add column if not exists host_name varchar(255);
alter table gateway_nodes add column if not exists advertise_host varchar(255);
alter table gateway_nodes add column if not exists http_port int;
alter table gateway_nodes add column if not exists ws_port int;
alter table gateway_nodes add column if not exists region varchar(64);
alter table gateway_nodes add column if not exists zone varchar(64);
alter table gateway_nodes add column if not exists site_id varchar(128);
alter table gateway_nodes add column if not exists version varchar(64) not null default 'unknown';
alter table gateway_nodes alter column version type varchar(64) using version::text;
alter table gateway_nodes alter column version set default 'unknown';
update gateway_nodes set version = 'unknown' where version is null;
alter table gateway_nodes alter column version set not null;
alter table gateway_nodes add column if not exists registered_at timestamptz;
alter table gateway_nodes add column if not exists last_heartbeat_at timestamptz;
alter table gateway_nodes add column if not exists lease_expires_at timestamptz;
create index if not exists idx_gateway_nodes_lease on gateway_nodes(status, lease_expires_at);

-- Adapter action mapper contract used by the scheduled action executor.
alter table adapter_actions add column if not exists idempotency_key varchar(255);
alter table adapter_actions add column if not exists incident_id varchar(128);
alter table adapter_actions add column if not exists dispatch_request_id varchar(128);
alter table adapter_actions add column if not exists assignment_id varchar(128);
alter table adapter_actions add column if not exists agent_id varchar(128);
alter table adapter_actions add column if not exists adapter_name varchar(128);
alter table adapter_actions add column if not exists adapter_type varchar(128);
alter table adapter_actions add column if not exists action_type varchar(128);
alter table adapter_actions add column if not exists reason text;
alter table adapter_actions add column if not exists request_hash varchar(255);
alter table adapter_actions add column if not exists response_ref text;
alter table adapter_actions add column if not exists payload_json jsonb not null default '{}'::jsonb;
update adapter_actions set payload_json = action_json where payload_json = '{}'::jsonb and action_json is not null;
alter table adapter_actions add column if not exists executing_at timestamptz;
alter table adapter_actions add column if not exists completed_at timestamptz;
alter table adapter_actions add column if not exists failed_at timestamptz;
alter table adapter_actions add column if not exists next_attempt_at timestamptz;
alter table adapter_actions add column if not exists retry_waiting_at timestamptz;
alter table adapter_actions add column if not exists executor_unavailable_at timestamptz;
alter table adapter_actions add column if not exists claimed_by varchar(128);
alter table adapter_actions add column if not exists claimed_at timestamptz;
alter table adapter_actions add column if not exists lease_expires_at timestamptz;
alter table adapter_actions add column if not exists worker_heartbeat_at timestamptz;
alter table adapter_actions add column if not exists attempt_count int not null default 0;
alter table adapter_actions add column if not exists max_attempts int not null default 3;
alter table adapter_actions add column if not exists executor_name varchar(128);
alter table adapter_actions add column if not exists last_error text;
create index if not exists idx_adapter_actions_executable on adapter_actions(status, adapter_type, next_attempt_at, created_at);
create unique index if not exists ux_adapter_actions_idempotency_key on adapter_actions(idempotency_key) where idempotency_key is not null;

-- Runtime state normalization tables used by gateway heartbeat/runtime state ingestion.
create table if not exists agent_runtime_capability_profiles (
  agent_id varchar(128) primary key,
  agent_type varchar(64),
  owner_gateway_node_id varchar(128),
  agent_session_id varchar(128),
  plugin_name varchar(128),
  plugin_version varchar(255),
  capability_revision varchar(255),
  executor_mode varchar(64),
  placement_pool varchar(128),
  placement_region varchar(64),
  placement_zone varchar(64),
  max_concurrent_tasks int,
  capability_profile_json jsonb not null default '{}'::jsonb,
  first_seen_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now()
);

create table if not exists agent_runtime_capability_items (
  agent_id varchar(128) not null,
  capability_kind varchar(128) not null,
  capability_value varchar(255) not null,
  capability_revision varchar(255),
  source varchar(64),
  updated_at timestamptz not null default now(),
  primary key (agent_id, capability_kind, capability_value)
);
create index if not exists idx_agent_runtime_capability_items_value on agent_runtime_capability_items(capability_value, agent_id);

create table if not exists agent_runtime_load_snapshots (
  agent_id varchar(128) primary key,
  owner_gateway_node_id varchar(128),
  agent_session_id varchar(128),
  status varchar(32),
  active_tasks int not null default 0,
  max_concurrent_tasks int not null default 1,
  available_slots int,
  capacity_utilization numeric(8,4),
  outbox_pending int,
  outbox_in_flight int,
  recovery_pending_assignments int,
  draining boolean not null default false,
  heartbeat_sequence bigint,
  runtime_load_json jsonb not null default '{}'::jsonb,
  heartbeat_at timestamptz,
  updated_at timestamptz not null default now()
);

create table if not exists agent_runtime_descriptors (
  agent_id varchar(128) primary key,
  agent_type varchar(64),
  plugin_name varchar(128),
  plugin_version varchar(255),
  protocol_version varchar(255),
  connection_type varchar(64),
  owner_gateway_node_id varchar(128),
  agent_session_id varchar(128),
  site_id varchar(128),
  region varchar(64),
  zone varchar(64),
  status varchar(32),
  runtime_features_json jsonb not null default '{}'::jsonb,
  active_tasks int not null default 0,
  max_concurrent_tasks int not null default 1,
  available_slots int,
  capacity_utilization numeric(8,4),
  draining boolean not null default false,
  heartbeat_sequence bigint,
  connected_at timestamptz,
  last_heartbeat_at timestamptz,
  last_seen_at timestamptz,
  raw_payload_json jsonb not null default '{}'::jsonb,
  first_seen_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- Runtime resource/binding fields used by AgentAssignmentDao direct-dispatch support APIs.
alter table runtime_resources add column if not exists runtime_code varchar(128);
update runtime_resources set runtime_code = runtime_id where runtime_code is null;
alter table runtime_resources add column if not exists connector_type varchar(128);
alter table runtime_resources add column if not exists execution_host varchar(255);
alter table runtime_resources add column if not exists environment varchar(64);
alter table runtime_resources add column if not exists region varchar(64);
alter table runtime_resources add column if not exists zone varchar(64);
alter table runtime_resources add column if not exists trust_status varchar(32);
alter table runtime_resources add column if not exists capacity_limit int;
create unique index if not exists ux_runtime_resources_code on runtime_resources(tenant_id, runtime_code) where runtime_code is not null;
create unique index if not exists ux_runtime_resources_tenant_runtime on runtime_resources(tenant_id, runtime_id);

alter table agent_runtime_bindings add column if not exists runtime_code varchar(128);
alter table agent_runtime_bindings add column if not exists verified_by varchar(128);
alter table agent_runtime_bindings add column if not exists verified_at timestamptz;
alter table agent_runtime_bindings add column if not exists approved_by varchar(128);
alter table agent_runtime_bindings add column if not exists approved_at timestamptz;
alter table agent_runtime_bindings add column if not exists effective_from timestamptz;
alter table agent_runtime_bindings add column if not exists expires_at timestamptz;
alter table agent_runtime_bindings add column if not exists capacity_limit int;
alter table agent_runtime_bindings add column if not exists region varchar(64);
alter table agent_runtime_bindings add column if not exists zone varchar(64);
alter table agent_runtime_bindings add column if not exists data_scope varchar(128);
alter table agent_runtime_bindings add column if not exists risk_limit varchar(32);

create table if not exists runtime_feature_catalog (
  tenant_id varchar(64) not null,
  feature_id varchar(128) not null,
  feature_code varchar(128) not null,
  feature_name varchar(255),
  category varchar(128),
  description text,
  status varchar(32) not null default 'ACTIVE',
  version int not null default 1,
  requires_probe boolean not null default false,
  requires_trust_approval boolean not null default false,
  is_dispatch_eligible boolean not null default true,
  owner_team varchar(128),
  effective_from timestamptz,
  retired_at timestamptz,
  metadata_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, feature_code),
  unique (tenant_id, feature_id)
);

create table if not exists agent_runtime_feature_observations (
  tenant_id varchar(64) not null,
  observation_id varchar(128) primary key,
  agent_id varchar(128) not null,
  runtime_id varchar(128),
  binding_id varchar(128),
  feature_code varchar(128) not null,
  feature_name varchar(255),
  observed_value varchar(255),
  source varchar(64),
  probe_result varchar(64),
  observed_at timestamptz,
  metadata_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists idx_agent_runtime_feature_observations_agent on agent_runtime_feature_observations(tenant_id, agent_id, feature_code, observed_at desc);

create table if not exists agent_runtime_feature_trust (
  tenant_id varchar(64) not null,
  trust_id varchar(128) primary key,
  agent_id varchar(128) not null,
  runtime_id varchar(128),
  binding_id varchar(128),
  feature_code varchar(128) not null,
  feature_name varchar(255),
  trust_status varchar(32) not null default 'UNVERIFIED',
  source varchar(64),
  observed_at timestamptz,
  verified_by varchar(128),
  verified_at timestamptz,
  trusted_by varchar(128),
  trusted_at timestamptz,
  revoked_by varchar(128),
  revoked_at timestamptz,
  expires_at timestamptz,
  evidence_ref text,
  reason text,
  metadata_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, agent_id, runtime_id, binding_id, feature_code)
);

-- Capability catalog compatibility columns used by direct capability management APIs.
alter table agent_capability_catalog add column if not exists legacy_task_coupling varchar(64);
alter table agent_capability_catalog add column if not exists migration_status varchar(64);
alter table agent_capability_catalog add column if not exists task_definition_id varchar(128);
alter table agent_capability_catalog add column if not exists source_system varchar(128);
alter table agent_capability_catalog add column if not exists task_type varchar(128);
alter table agent_capability_catalog add column if not exists requires_certification boolean not null default false;

-- Remediation workflow persistence used by the remaining support-only lease cleanup scheduler.
create table if not exists agent_remediation_workflows (
  workflow_id varchar(128) primary key,
  proposal_id varchar(128),
  agent_id varchar(128) not null,
  status varchar(32) not null,
  severity varchar(32),
  approval_required boolean not null default false,
  created_by varchar(128),
  last_operator_id varchar(128),
  rollback_suggestions jsonb not null default '[]'::jsonb,
  actions_json jsonb not null default '[]'::jsonb,
  metadata jsonb not null default '{}'::jsonb,
  execution_lease_owner varchar(128),
  execution_lease_acquired_at timestamptz,
  execution_lease_expires_at timestamptz,
  execution_lease_version int not null default 0,
  version int not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists idx_agent_remediation_workflows_agent on agent_remediation_workflows(agent_id, updated_at desc);
create index if not exists idx_agent_remediation_workflows_lease on agent_remediation_workflows(execution_lease_owner, execution_lease_expires_at);

create table if not exists agent_remediation_workflow_history (
  history_id varchar(128) primary key,
  workflow_id varchar(128) not null,
  agent_id varchar(128),
  event_type varchar(64) not null,
  operator_id varchar(128),
  reason text,
  metadata jsonb not null default '{}'::jsonb,
  occurred_at timestamptz not null default now()
);
create index if not exists idx_agent_remediation_workflow_history_workflow on agent_remediation_workflow_history(workflow_id, occurred_at);

create table if not exists agent_remediation_workflow_action_executions (
  action_execution_id varchar(128) primary key,
  workflow_id varchar(128) not null,
  agent_id varchar(128),
  action_id varchar(128) not null,
  action_type varchar(128),
  idempotency_key varchar(255),
  status varchar(32) not null,
  attempt_count int not null default 0,
  last_operator_id varchar(128),
  last_reason text,
  last_result jsonb not null default '{}'::jsonb,
  last_error text,
  first_attempt_at timestamptz,
  last_attempt_at timestamptz,
  completed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (workflow_id, action_id)
);
create index if not exists idx_agent_remediation_action_executions_workflow on agent_remediation_workflow_action_executions(workflow_id, created_at);


-- Phase 28 MyBatis DAO baseline contract completion.
-- This block aligns the clean baseline with compiled MyBatis XML mappers so
-- live runtime paths fail in application logic instead of SQL grammar/DDL drift.

create or replace function severity_rank(value text) returns integer as $$
begin
  return case upper(coalesce(value, ''))
    when 'CRITICAL' then 5
    when 'HIGH' then 4
    when 'MEDIUM' then 3
    when 'LOW' then 2
    when 'INFO' then 1
    else 0
  end;
end;
$$ language plpgsql immutable;

create table if not exists incident_occurrence_summary (
  summary_id varchar(128) primary key,
  incident_id varchar(128) not null,
  fingerprint varchar(255),
  tenant_id varchar(64),
  source_system varchar(128),
  site_id varchar(128),
  plant_id varchar(128),
  object_type varchar(128),
  object_id varchar(255),
  event_type varchar(128),
  error_code varchar(128),
  window_start timestamptz not null,
  window_end timestamptz not null,
  occurrence_count int not null default 1,
  max_severity varchar(32),
  latest_event_id varchar(128),
  latest_message text,
  latest_payload_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (incident_id, window_start, window_end)
);
create index if not exists idx_incident_occurrence_summary_tenant_window on incident_occurrence_summary(tenant_id, window_start desc, window_end desc);
create index if not exists idx_incident_occurrence_summary_fingerprint_window on incident_occurrence_summary(fingerprint, window_start desc);

-- Event decision mapper stores decisions by event_id while older baseline used decision_id.
alter table event_decisions alter column decision_id set default ('decision-' || md5(random()::text || clock_timestamp()::text));
alter table event_decisions add column if not exists event_id varchar(128);
alter table event_decisions add column if not exists fingerprint varchar(255);
alter table event_decisions add column if not exists incident_id varchar(128);
alter table event_decisions add column if not exists decision_type varchar(64);
alter table event_decisions add column if not exists duplicate boolean not null default false;
alter table event_decisions add column if not exists occurrence_count int not null default 1;
alter table event_decisions add column if not exists actions_json jsonb not null default '[]'::jsonb;
alter table event_decisions add column if not exists reason text;
alter table event_decisions add column if not exists decided_at timestamptz;
create unique index if not exists ux_event_decisions_event_id on event_decisions(event_id);

-- Runtime history mapper identity defaults: mapper supplies semantic IDs, older baseline kept generic attempt/link IDs.
alter table dispatch_attempt_history alter column attempt_id set default ('dispatch-history-' || md5(random()::text || clock_timestamp()::text));
alter table dispatch_attempt_history add column if not exists history_id varchar(128);
alter table dispatch_attempt_history add column if not exists incident_id varchar(128);
alter table dispatch_attempt_history add column if not exists assignment_id varchar(128);
alter table dispatch_attempt_history add column if not exists owner_gateway_node_id varchar(128);
alter table dispatch_attempt_history add column if not exists agent_session_id varchar(128);
alter table dispatch_attempt_history add column if not exists site_id varchar(128);
alter table dispatch_attempt_history add column if not exists routing_decision_id varchar(128);
alter table dispatch_attempt_history add column if not exists event_type varchar(128);
alter table dispatch_attempt_history add column if not exists attempt_no int;
alter table dispatch_attempt_history add column if not exists task_dispatch_attempt_no int;
alter table dispatch_attempt_history add column if not exists error_code varchar(128);
alter table dispatch_attempt_history add column if not exists error_message text;
alter table dispatch_attempt_history add column if not exists next_attempt_at timestamptz;
alter table dispatch_attempt_history add column if not exists runtime_backoff_until timestamptz;
alter table dispatch_attempt_history add column if not exists worker_id varchar(128);
alter table dispatch_attempt_history add column if not exists claim_until timestamptz;
alter table dispatch_attempt_history add column if not exists payload_json jsonb not null default '{}'::jsonb;
alter table dispatch_attempt_history add column if not exists occurred_at timestamptz;
create unique index if not exists ux_dispatch_attempt_history_history_id on dispatch_attempt_history(history_id);
create index if not exists idx_dispatch_attempt_history_dispatch_request on dispatch_attempt_history(dispatch_request_id, occurred_at desc, created_at desc);

alter table task_issue_links alter column link_id set default ('task-issue-link-' || md5(random()::text || clock_timestamp()::text));
alter table task_issue_links add column if not exists incident_id varchar(128);
alter table task_issue_links add column if not exists dispatch_request_id varchar(128);
alter table task_issue_links add column if not exists assignment_id varchar(128);
alter table task_issue_links add column if not exists agent_id varchar(128);
alter table task_issue_links add column if not exists issue_vendor varchar(128);
alter table task_issue_links add column if not exists issue_id varchar(128);
alter table task_issue_links add column if not exists issue_status varchar(64);
alter table task_issue_links add column if not exists sync_status varchar(64) not null default 'SYNC_PENDING';
alter table task_issue_links add column if not exists issue_action_id varchar(128);
alter table task_issue_links add column if not exists issue_action_type varchar(128);
alter table task_issue_links add column if not exists issue_action_status varchar(64);
alter table task_issue_links add column if not exists issue_retryable boolean;
alter table task_issue_links add column if not exists issue_comment_mode varchar(64);
alter table task_issue_links add column if not exists agent_summary text;
alter table task_issue_links add column if not exists issue_comment_preview text;
alter table task_issue_links add column if not exists last_synced_at timestamptz;
alter table task_issue_links add column if not exists sync_error text;
alter table task_issue_links add column if not exists message text;
alter table task_issue_links add column if not exists last_adapter_action_at timestamptz;
create unique index if not exists ux_task_issue_links_task_id on task_issue_links(task_id);

alter table routing_decisions add column if not exists incident_id varchar(128);
alter table routing_decisions add column if not exists routing_policy varchar(128);
alter table routing_decisions add column if not exists status varchar(64);
alter table routing_decisions add column if not exists selected_gateway_node_id varchar(128);
alter table routing_decisions add column if not exists selected_agent_session_id varchar(128);
alter table routing_decisions add column if not exists selected_site_id varchar(128);
alter table routing_decisions add column if not exists selected_score numeric;
alter table routing_decisions add column if not exists decision_reason text;
alter table routing_decisions add column if not exists user_facing_error_json jsonb not null default '{}'::jsonb;
alter table routing_decisions add column if not exists candidates_json jsonb not null default '[]'::jsonb;

alter table task_dispatch_attempts alter column attempt_id set default ('task-dispatch-attempt-' || md5(random()::text || clock_timestamp()::text));
alter table task_dispatch_attempts add column if not exists dispatch_attempt_id varchar(128);
alter table task_dispatch_attempts add column if not exists incident_id varchar(128);
alter table task_dispatch_attempts add column if not exists routing_decision_id varchar(128);
alter table task_dispatch_attempts add column if not exists selected_agent_id varchar(128);
alter table task_dispatch_attempts add column if not exists selected_gateway_node_id varchar(128);
alter table task_dispatch_attempts add column if not exists selected_agent_session_id varchar(128);
alter table task_dispatch_attempts add column if not exists selected_site_id varchar(128);
alter table task_dispatch_attempts add column if not exists selected_score numeric;
alter table task_dispatch_attempts add column if not exists eligibility_status varchar(64);
alter table task_dispatch_attempts add column if not exists decision_reason text;
alter table task_dispatch_attempts add column if not exists score_breakdown_json jsonb not null default '{}'::jsonb;
alter table task_dispatch_attempts add column if not exists eligibility_facts_json jsonb not null default '{}'::jsonb;
create unique index if not exists ux_task_dispatch_attempts_dispatch_attempt_id on task_dispatch_attempts(dispatch_attempt_id);

alter table task_execution_attempts alter column attempt_id set default ('task-execution-attempt-' || md5(random()::text || clock_timestamp()::text));
alter table task_execution_attempts add column if not exists execution_attempt_id varchar(128);
alter table task_execution_attempts add column if not exists dispatch_attempt_id varchar(128);
alter table task_execution_attempts add column if not exists agent_id varchar(128);
alter table task_execution_attempts add column if not exists agent_session_id varchar(128);
alter table task_execution_attempts add column if not exists lease_id varchar(128);
alter table task_execution_attempts add column if not exists fencing_token varchar(255);
alter table task_execution_attempts add column if not exists attempt_no int;
alter table task_execution_attempts add column if not exists result_code varchar(64);
alter table task_execution_attempts add column if not exists error_code varchar(128);
alter table task_execution_attempts add column if not exists error_message text;
alter table task_execution_attempts add column if not exists callback_id varchar(128);
alter table task_execution_attempts add column if not exists started_at timestamptz;
alter table task_execution_attempts add column if not exists completed_at timestamptz;
create unique index if not exists ux_task_execution_attempts_execution_attempt_id on task_execution_attempts(execution_attempt_id);

alter table adapter_executor_audit add column if not exists incident_id varchar(128);
alter table adapter_executor_audit add column if not exists adapter_type varchar(128);
alter table adapter_executor_audit add column if not exists action_type varchar(128);
alter table adapter_executor_audit add column if not exists executor_name varchar(128);
alter table adapter_executor_audit add column if not exists before_status varchar(64);
alter table adapter_executor_audit add column if not exists after_status varchar(64);
alter table adapter_executor_audit add column if not exists outcome varchar(64);
alter table adapter_executor_audit add column if not exists message text;
alter table adapter_executor_audit add column if not exists attempt_count int;
alter table adapter_executor_audit add column if not exists payload_snapshot_json jsonb not null default '{}'::jsonb;

create table if not exists recovery_approval_requests (
  approval_id varchar(128) primary key,
  status varchar(64), action varchar(128), target_type varchar(128), target_id varchar(128),
  dispatch_request_id varchar(128), task_id varchar(128), agent_id varchar(128), risk_level varchar(64),
  requested_by varchar(128), requester_principal varchar(128), requester_role varchar(128), request_reason text, request_id varchar(128),
  request_client_address varchar(128), request_user_agent text, approval_reason text, approved_by varchar(128), approver_principal varchar(128),
  approver_role varchar(128), approval_request_id varchar(128), approval_client_address varchar(128), approval_user_agent text,
  rejected_by varchar(128), rejected_reason text, cancelled_by varchar(128), cancelled_reason text, execution_result varchar(128), execution_error text,
  expires_at timestamptz, approved_at timestamptz, executed_at timestamptz, rejected_at timestamptz, cancelled_at timestamptz,
  created_at timestamptz not null default now(), updated_at timestamptz not null default now(), payload_json jsonb not null default '{}'::jsonb
);

create table if not exists dispatch_task_definitions (
  tenant_id varchar(64) not null, definition_id varchar(128) primary key, source_system varchar(128), task_type varchar(128), display_name varchar(255),
  description text, domain varchar(128), risk_level varchar(64), default_severity varchar(64), owner_team varchar(128), status varchar(64), version int,
  effective_from timestamptz, retired_at timestamptz, metadata_json jsonb not null default '{}'::jsonb, created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create unique index if not exists ux_dispatch_task_definitions_tenant_source_task on dispatch_task_definitions(tenant_id, source_system, task_type);

create table if not exists dispatch_event_task_mappings (
  tenant_id varchar(64) not null, mapping_id varchar(128) not null, source_system varchar(128), object_type varchar(128), event_type varchar(128), error_code varchar(128),
  message_pattern text, task_type varchar(128), capability_code varchar(128), priority int, is_active boolean not null default true, metadata_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(), updated_at timestamptz not null default now(), primary key (tenant_id, mapping_id)
);

create table if not exists assignment_profile_capability_bindings (
  tenant_id varchar(64) not null, binding_id varchar(128), profile_code varchar(128) not null, capability_code varchar(128) not null, capability_name varchar(255),
  binding_mode varchar(64), is_required boolean not null default false, is_active boolean not null default true, priority int, approval_status varchar(64), condition_expr text,
  metadata_json jsonb not null default '{}'::jsonb, created_at timestamptz not null default now(), updated_at timestamptz not null default now(), primary key (tenant_id, profile_code, capability_code)
);
create unique index if not exists ux_assignment_profile_capability_bindings_binding_id on assignment_profile_capability_bindings(tenant_id, binding_id);

create table if not exists dispatch_policy_scopes (
  tenant_id varchar(64) not null, scope_id varchar(128) not null, policy_code varchar(128), source_system varchar(128), task_type varchar(128), task_definition_id varchar(128),
  risk_level varchar(64), priority int, condition_expr text, is_active boolean not null default true, priority_order int, metadata_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(), updated_at timestamptz not null default now(), primary key (tenant_id, scope_id)
);
create table if not exists dispatch_policy_required_capabilities (
  tenant_id varchar(64) not null, rule_id varchar(128) not null, policy_code varchar(128), capability_code varchar(128), capability_name varchar(255), required_mode varchar(64), min_version varchar(64),
  condition_expr text, is_blocking boolean not null default false, priority int, metadata_json jsonb not null default '{}'::jsonb, created_at timestamptz not null default now(), updated_at timestamptz not null default now(), primary key (tenant_id, rule_id)
);
create table if not exists dispatch_policy_required_runtime_features (
  tenant_id varchar(64) not null, rule_id varchar(128) not null, policy_code varchar(128), feature_code varchar(128), feature_name varchar(255), trust_status varchar(64),
  condition_expr text, is_blocking boolean not null default false, priority int, metadata_json jsonb not null default '{}'::jsonb, created_at timestamptz not null default now(), updated_at timestamptz not null default now(), primary key (tenant_id, rule_id)
);
create table if not exists dispatch_policy_quality_rules (
  tenant_id varchar(64) not null, rule_id varchar(128) not null, policy_code varchar(128), metric_name varchar(128), operator varchar(32), threshold_value numeric, metric_window varchar(64),
  is_blocking boolean not null default false, score_weight numeric, condition_expr text, metadata_json jsonb not null default '{}'::jsonb, created_at timestamptz not null default now(), updated_at timestamptz not null default now(), primary key (tenant_id, rule_id)
);
create table if not exists dispatch_policy_scoring_rules (
  tenant_id varchar(64) not null, rule_id varchar(128) not null, policy_code varchar(128), factor_name varchar(128), weight numeric, direction varchar(64), condition_expr text,
  metadata_json jsonb not null default '{}'::jsonb, created_at timestamptz not null default now(), updated_at timestamptz not null default now(), primary key (tenant_id, rule_id)
);
create unique index if not exists ux_dispatch_policies_tenant_policy_code on dispatch_policies(tenant_id, policy_code);

create table if not exists supply_profiles (
  tenant_id varchar(64) not null, supply_profile_id varchar(128), profile_code varchar(128) not null, profile_name varchar(255), agent_id varchar(128), runtime_binding_id varchar(128), runtime_id varchar(128),
  service_role varchar(128), service_level varchar(64), quality_grade varchar(64), risk_limit varchar(64), data_scope varchar(255), capacity_policy varchar(128), status varchar(64),
  effective_from timestamptz, expires_at timestamptz, capability_snapshot_json jsonb not null default '{}'::jsonb, runtime_feature_snapshot_json jsonb not null default '{}'::jsonb,
  quality_snapshot_json jsonb not null default '{}'::jsonb, metadata_json jsonb not null default '{}'::jsonb, created_at timestamptz not null default now(), updated_at timestamptz not null default now(), primary key (tenant_id, profile_code)
);
create unique index if not exists ux_supply_profiles_supply_profile_id on supply_profiles(tenant_id, supply_profile_id);

create table if not exists agent_quality_metrics_daily (
  tenant_id varchar(64) not null, metric_id varchar(128) not null, metric_date date, agent_id varchar(128), runtime_id varchar(128), binding_id varchar(128), supply_profile_id varchar(128),
  success_rate numeric, failure_rate numeric, timeout_rate numeric, sla_breach_rate numeric, avg_ack_latency_ms numeric, avg_completion_latency_ms numeric,
  recent_failure_count int, manual_rating numeric, quality_grade varchar(64), risk_penalty numeric, score numeric, sample_size int,
  metadata_json jsonb not null default '{}'::jsonb, created_at timestamptz not null default now(), updated_at timestamptz not null default now(), primary key (tenant_id, metric_id)
);
create table if not exists agent_quality_metrics_window (
  tenant_id varchar(64) not null, metric_id varchar(128) not null, agent_id varchar(128), runtime_id varchar(128), binding_id varchar(128), supply_profile_id varchar(128), metric_window varchar(64), window_start timestamptz, window_end timestamptz,
  success_rate numeric, failure_rate numeric, timeout_rate numeric, sla_breach_rate numeric, avg_ack_latency_ms numeric, avg_completion_latency_ms numeric,
  recent_failure_count int, manual_rating numeric, quality_grade varchar(64), risk_penalty numeric, score numeric, sample_size int,
  metadata_json jsonb not null default '{}'::jsonb, created_at timestamptz not null default now(), updated_at timestamptz not null default now(), primary key (tenant_id, metric_id)
);
create table if not exists runtime_quality_metrics_daily (
  tenant_id varchar(64) not null, metric_id varchar(128) not null, metric_date date, runtime_id varchar(128), success_rate numeric, failure_rate numeric, timeout_rate numeric, sla_breach_rate numeric,
  avg_ack_latency_ms numeric, avg_completion_latency_ms numeric, recent_failure_count int, quality_grade varchar(64), risk_penalty numeric, score numeric, sample_size int,
  metadata_json jsonb not null default '{}'::jsonb, created_at timestamptz not null default now(), updated_at timestamptz not null default now(), primary key (tenant_id, metric_id)
);
create table if not exists supply_profile_quality_snapshot (
  tenant_id varchar(64) not null, snapshot_id varchar(128) not null, supply_profile_id varchar(128), profile_code varchar(128), agent_id varchar(128), runtime_id varchar(128), binding_id varchar(128), metric_window varchar(64),
  success_rate numeric, failure_rate numeric, timeout_rate numeric, sla_breach_rate numeric, avg_ack_latency_ms numeric, avg_completion_latency_ms numeric,
  recent_failure_count int, manual_rating numeric, quality_grade varchar(64), risk_penalty numeric, score numeric, sample_size int, calculated_at timestamptz,
  source varchar(64), metadata_json jsonb not null default '{}'::jsonb, created_at timestamptz not null default now(), updated_at timestamptz not null default now(), primary key (tenant_id, snapshot_id)
);
create table if not exists agent_assignment_profile_policy_bindings (
  tenant_id varchar(64) not null, binding_id varchar(128), profile_code varchar(128) not null, policy_code varchar(128) not null, policy_name varchar(255), binding_mode varchar(64),
  is_required boolean not null default false, is_active boolean not null default true, priority int, condition_expr text, metadata_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(), updated_at timestamptz not null default now(), primary key (tenant_id, profile_code, policy_code)
);
create unique index if not exists ux_agent_assignment_profile_policy_bindings_binding_id on agent_assignment_profile_policy_bindings(tenant_id, binding_id);
create table if not exists agent_skill_definitions (
  skill_code varchar(128) primary key, display_name varchar(255), domain varchar(128), description text, taxonomy_version varchar(64), task_definition_id varchar(128), source_system varchar(128), task_type varchar(128),
  providers_json jsonb not null default '[]'::jsonb, task_types_json jsonb not null default '[]'::jsonb, operations_json jsonb not null default '[]'::jsonb,
  tool_policies_json jsonb not null default '[]'::jsonb, resource_scopes_json jsonb not null default '[]'::jsonb, data_classes_json jsonb not null default '[]'::jsonb,
  risk_level varchar(64), requires_human_approval boolean not null default false, masking_required boolean not null default false, enabled boolean not null default true,
  metadata_json jsonb not null default '{}'::jsonb, created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create table if not exists agent_profile_approved_skills (
  agent_id varchar(128) not null, skill_code varchar(128) not null, policy_version int, enabled boolean not null default true, approved_by varchar(128), approved_at timestamptz,
  metadata_json jsonb not null default '{}'::jsonb, created_at timestamptz not null default now(), updated_at timestamptz not null default now(), primary key(agent_id, skill_code)
);
create table if not exists agent_skill_versions (
  skill_code varchar(128) not null, version varchar(64) not null, status varchar(64), definition_json jsonb not null default '{}'::jsonb, submitted_by varchar(128), submitted_at timestamptz,
  reviewed_by varchar(128), reviewed_at timestamptz, review_comment text, published_by varchar(128), published_at timestamptz, supersedes_version varchar(64), rollback_of_version varchar(64),
  metadata_json jsonb not null default '{}'::jsonb, created_at timestamptz not null default now(), updated_at timestamptz not null default now(), primary key(skill_code, version)
);
create table if not exists agent_skill_audit_entries (
  audit_id varchar(128) primary key, skill_code varchar(128), version varchar(64), action varchar(128), operator_id varchar(128), reason text, from_status varchar(64), to_status varchar(64),
  metadata_json jsonb not null default '{}'::jsonb, created_at timestamptz not null default now()
);
create table if not exists agent_skill_approval_policies (
  skill_code varchar(128) primary key, enabled boolean not null default true, submit_roles_json jsonb not null default '[]'::jsonb, approve_roles_json jsonb not null default '[]'::jsonb,
  publish_roles_json jsonb not null default '[]'::jsonb, rollback_roles_json jsonb not null default '[]'::jsonb, separation_of_duties boolean not null default true,
  updated_by varchar(128), updated_at timestamptz not null default now(), metadata_json jsonb not null default '{}'::jsonb
);
create table if not exists agent_skill_deprecation_plans (
  skill_code varchar(128) primary key, status varchar(64), replacement_skill_codes_json jsonb not null default '[]'::jsonb, migration_deadline timestamptz,
  created_by varchar(128), created_at timestamptz not null default now(), updated_by varchar(128), updated_at timestamptz not null default now(), metadata_json jsonb not null default '{}'::jsonb
);
create table if not exists agent_skill_dependency_edges (
  edge_id varchar(128) primary key, source_skill_code varchar(128), target_skill_code varchar(128), relation_type varchar(64), required boolean not null default false, enabled boolean not null default true,
  confidence numeric, description text, created_by varchar(128), created_at timestamptz not null default now(), updated_by varchar(128), updated_at timestamptz not null default now(), metadata_json jsonb not null default '{}'::jsonb
);

-- Exact ON CONFLICT targets required by compiled mappers.
create unique index if not exists ux_agent_runtime_feature_trust_agent_feature on agent_runtime_feature_trust(tenant_id, agent_id, feature_code);
create unique index if not exists ux_runtime_resources_tenant_runtime_id on runtime_resources(tenant_id, runtime_id);

-- Support views used by JDBC administration repositories.
create or replace view dispatch_p11_enforce_observability_snapshot as
select
  now() as generated_at,
  'runtime'::varchar(128) as source,
  'AUTHORITATIVE'::varchar(64) as mode,
  '24 hours'::varchar(64) as metric_window,
  0::bigint as v2_allowed,
  0::bigint as v2_blocked,
  coalesce((select count(*) from routing_decisions where status = 'NO_CANDIDATE'), 0)::bigint as no_candidate,
  0::bigint as fallback_denied,
  0::bigint as quality_unavailable,
  0::bigint as score_breakdown_missing,
  0.0::double precision as blocked_rate,
  0.0::double precision as no_candidate_rate,
  0.0::double precision as quality_unavailable_rate,
  0::bigint as readiness_blocking_count;

create or replace view dispatch_p11_routing_audit as
select
  decision_id,
  task_id,
  selected_agent_id as agent_id,
  routing_policy::text as policy_code,
  case when status = 'NO_CANDIDATE' then decision_reason else null end as blocking_code,
  'STANDARD'::varchar(64) as eligibility_engine_mode,
  false as eligibility_v2_applied,
  false as eligibility_v2_candidate_eligible,
  selected_score as eligibility_v2_score,
  '[]'::text as eligibility_v2_blocking_reasons,
  '{}'::text as eligibility_v2_score_breakdown,
  created_at
from routing_decisions;

create or replace view dispatch_p11_legacy_final_report as
select
  'STANDARD_RUNTIME'::varchar(128) as category,
  0::bigint as count,
  'INFO'::varchar(32) as severity,
  array[]::text[] as sample_refs;

create or replace view dispatch_p11_artifact_retention as
select
  artifact_name,
  artifact_path,
  generated_at,
  retained_until,
  source
from dispatch_release_artifacts;

