-- Phase 28 live MyBatis DAO schema contract repair.
-- Safe to run multiple times in local/dev databases.

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
