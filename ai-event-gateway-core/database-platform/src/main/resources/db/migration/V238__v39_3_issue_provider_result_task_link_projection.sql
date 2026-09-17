-- V39-3: durable provider result evidence and TaskIssueLink projection reconciliation.
-- Provider-observed fields are immutable after insert. Only projection lifecycle fields may change.
create table if not exists issue_provider_execution_results (
  tenant_id varchar(128) not null,
  result_id varchar(196) not null,
  adapter_action_id varchar(128) not null,
  task_id varchar(128),
  adapter_action_type varchar(64),
  attempt_no integer not null,
  observation_kind varchar(64) not null default 'EXECUTION',
  provider varchar(64),
  operation varchar(64),
  disposition varchar(32) not null,
  provider_outcome_certainty varchar(32) not null default 'CONFIRMED',
  provider_status_code integer,
  retryable boolean not null default false,
  failure_code varchar(128),
  provider_health_impact varchar(32),
  external_project_id varchar(255),
  external_issue_id varchar(255),
  external_issue_key varchar(255),
  external_issue_url text,
  external_status varchar(128),
  response_ref text,
  response_fingerprint varchar(128) not null,
  idempotency_key varchar(255),
  operation_fingerprint varchar(128),
  correlation_id varchar(128),
  a2a_request_id varchar(128),
  source_system_id varchar(128),
  connection_id varchar(128),
  project_mapping_id varchar(128),
  technical_principal_id varchar(128),
  credential_id varchar(128),
  credential_version varchar(128),
  error_message text,
  executed_at timestamptz not null,
  projection_status varchar(32) not null default 'PENDING',
  projection_attempt_count integer not null default 0,
  projection_max_attempts integer not null default 10,
  next_projection_attempt_at timestamptz,
  last_projection_error text,
  projected_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key(result_id),
  unique(adapter_action_id,attempt_no,observation_kind),
  check(attempt_no >= 1),
  check(observation_kind in ('EXECUTION','RECONCILIATION_APPLIED','RECONCILIATION_NOT_APPLIED')),
  check(disposition in ('CONFIRMED_SUCCESS','CONFIRMED_FAILURE','UNKNOWN')),
  check(provider_outcome_certainty in ('CONFIRMED','UNCERTAIN')),
  check(projection_status in ('PENDING','PROJECTED','RETRY_WAITING','FAILED_PERMANENT','NOT_REQUIRED')),
  check(projection_attempt_count >= 0),
  check(projection_max_attempts >= 1)
);

create index if not exists idx_issue_provider_results_projection_due
  on issue_provider_execution_results(projection_status,next_projection_attempt_at,executed_at)
  where projection_status in ('PENDING','RETRY_WAITING');
create index if not exists idx_issue_provider_results_task
  on issue_provider_execution_results(tenant_id,task_id,executed_at desc);
create index if not exists idx_issue_provider_results_idempotency
  on issue_provider_execution_results(tenant_id,idempotency_key,executed_at desc)
  where idempotency_key is not null;
create index if not exists idx_issue_provider_results_external_issue
  on issue_provider_execution_results(tenant_id,connection_id,external_project_id,external_issue_id)
  where external_issue_id is not null;

create or replace function protect_issue_provider_execution_result_evidence() returns trigger language plpgsql as $$
begin
  if (to_jsonb(new) - array['projection_status','projection_attempt_count','projection_max_attempts','next_projection_attempt_at','last_projection_error','projected_at','updated_at'])
      is distinct from
     (to_jsonb(old) - array['projection_status','projection_attempt_count','projection_max_attempts','next_projection_attempt_at','last_projection_error','projected_at','updated_at']) then
    raise exception 'ISSUE_PROVIDER_RESULT_EVIDENCE_IMMUTABLE';
  end if;
  return new;
end $$;

drop trigger if exists trg_issue_provider_result_evidence_immutable on issue_provider_execution_results;
create trigger trg_issue_provider_result_evidence_immutable
  before update on issue_provider_execution_results
  for each row execute function protect_issue_provider_execution_result_evidence();

comment on table issue_provider_execution_results is 'V39-3 immutable provider-observed Issue execution evidence with independently retryable TaskIssueLink projection state.';

-- V39-3 extends the projection guard for provider-outcome reconciliation. A previously
-- FAILED_* read model may converge to SYNCED only when the canonical AdapterAction is now
-- COMPLETED and a concrete external Issue id exists. This does not re-run the provider.
create or replace function phase0g_task_issue_sync_guard() returns trigger as $$
declare
  v_completed_issue_action boolean := false;
begin
  if old.tenant_id is distinct from new.tenant_id
     or old.link_id is distinct from new.link_id
     or old.task_id is distinct from new.task_id then
    raise exception 'ISSUE_LINK_IDENTITY_IMMUTABLE';
  end if;

  if new.resource_version <> old.resource_version + 1 then
    raise exception 'RESOURCE_VERSION_CONFLICT: task_issue_links';
  end if;

  if new.sync_status = 'SYNCED'
     and nullif(trim(coalesce(new.issue_id, new.external_issue_id)), '') is not null
     and nullif(trim(new.issue_action_id), '') is not null then
    select exists(
      select 1
        from adapter_actions action
       where action.action_id = new.issue_action_id
         and action.task_id = new.task_id
         and action.tenant_id = new.tenant_id
         and action.adapter_type = 'ISSUE_TRACKING'
         and action.status = 'COMPLETED'
    ) into v_completed_issue_action;
  end if;

  if not (
    new.sync_status = old.sync_status or
    (old.sync_status='PENDING' and new.sync_status in ('IN_PROGRESS','FAILED_RETRYABLE','FAILED_PERMANENT','CONFLICT','DISABLED')) or
    (old.sync_status='PENDING' and new.sync_status='SYNCED' and v_completed_issue_action) or
    (old.sync_status='IN_PROGRESS' and new.sync_status in ('SYNCED','FAILED_RETRYABLE','FAILED_PERMANENT','CONFLICT')) or
    (old.sync_status='FAILED_RETRYABLE' and new.sync_status in ('PENDING','IN_PROGRESS','FAILED_PERMANENT','DISABLED')) or
    (old.sync_status='FAILED_RETRYABLE' and new.sync_status='SYNCED' and v_completed_issue_action) or
    (old.sync_status='FAILED_PERMANENT' and new.sync_status in ('PENDING','IN_PROGRESS','DISABLED')) or
    (old.sync_status='FAILED_PERMANENT' and new.sync_status='SYNCED' and v_completed_issue_action) or
    (old.sync_status='SYNCED' and new.sync_status in ('CONFLICT','PENDING','DISABLED')) or
    (old.sync_status='CONFLICT' and new.sync_status in ('PENDING','SYNCED','DISABLED'))
  ) then
    raise exception 'ISSUE_SYNC_STATE_TRANSITION_DENIED: % -> %', old.sync_status,new.sync_status;
  end if;

  new.updated_at = now();
  return new;
end $$ language plpgsql;
