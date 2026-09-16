-- Phase 1A-8: reconcile successful external Issue creation with the Task Issue read model.
--
-- A synchronous provider executor can complete between two independent read-model writes.
-- The normal lifecycle is PENDING -> IN_PROGRESS -> SYNCED.  A recovery transition from
-- PENDING -> SYNCED is permitted only when a COMPLETED ISSUE_TRACKING action and a concrete
-- external Issue identifier prove that the external side effect already succeeded.

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

  if old.sync_status = 'PENDING'
     and new.sync_status = 'SYNCED'
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
    (old.sync_status='FAILED_PERMANENT' and new.sync_status in ('PENDING','IN_PROGRESS','DISABLED')) or
    (old.sync_status='SYNCED' and new.sync_status in ('CONFLICT','PENDING','DISABLED')) or
    (old.sync_status='CONFLICT' and new.sync_status in ('PENDING','SYNCED','DISABLED'))
  ) then
    raise exception 'ISSUE_SYNC_STATE_TRANSITION_DENIED: % -> %', old.sync_status,new.sync_status;
  end if;

  new.updated_at = now();
  return new;
end $$ language plpgsql;

-- Repair rows where the provider completed successfully but the projection write was rejected.
-- response_ref is JSON for the embedded Issue executors. Malformed or legacy non-JSON references
-- are intentionally ignored rather than aborting the migration or guessing external identifiers.
create or replace function phase1a8_try_parse_jsonb(p_value text) returns jsonb as $$
begin
  if p_value is null or trim(p_value) = '' then
    return '{}'::jsonb;
  end if;
  return p_value::jsonb;
exception
  when others then
    return '{}'::jsonb;
end $$ language plpgsql immutable;

with completed_issue_actions as (
  select
    action.action_id,
    action.tenant_id,
    action.task_id,
    action.completed_at,
    action.updated_at,
    phase1a8_try_parse_jsonb(action.response_ref) as result_json
  from adapter_actions action
  where action.adapter_type = 'ISSUE_TRACKING'
    and action.action_type = 'ISSUE_CREATE'
    and action.status = 'COMPLETED'
), recoverable as (
  select
    action.*,
    nullif(trim(action.result_json ->> 'vendor'), '') as issue_vendor,
    nullif(trim(action.result_json ->> 'issueId'), '') as issue_id,
    nullif(trim(action.result_json ->> 'issueUrl'), '') as issue_url,
    nullif(trim(action.result_json ->> 'issueStatus'), '') as issue_status
  from completed_issue_actions action
  where nullif(trim(action.result_json ->> 'issueId'), '') is not null
)
update task_issue_links link
   set provider_type = coalesce(recoverable.issue_vendor, link.provider_type),
       external_issue_id = recoverable.issue_id,
       external_issue_key = coalesce(link.external_issue_key, recoverable.issue_id),
       external_issue_url = coalesce(recoverable.issue_url, link.external_issue_url),
       issue_vendor = coalesce(recoverable.issue_vendor, link.issue_vendor),
       issue_id = recoverable.issue_id,
       issue_url = coalesce(recoverable.issue_url, link.issue_url),
       issue_status = coalesce(recoverable.issue_status, link.issue_status, 'open'),
       sync_status = 'SYNCED',
       issue_action_status = 'COMPLETED',
       issue_retryable = false,
       last_synced_at = coalesce(recoverable.completed_at, recoverable.updated_at, now()),
       sync_error = null,
       message = 'Issue Tracking sync reconciled from a completed provider action.',
       last_adapter_action_at = coalesce(recoverable.completed_at, recoverable.updated_at, link.last_adapter_action_at),
       resource_version = link.resource_version + 1,
       updated_at = now()
  from recoverable
 where link.issue_action_id = recoverable.action_id
   and link.task_id = recoverable.task_id
   and link.tenant_id = recoverable.tenant_id
   and link.sync_status in ('PENDING','IN_PROGRESS')
   and (link.issue_id is null or link.issue_id = recoverable.issue_id);

drop function phase1a8_try_parse_jsonb(text);
