-- Phase 12.4 lineage is an immutable provenance side effect of authoritative Task/Assignment rows.
-- The trigger must not depend on an ambient HTTP/scheduler tenant context. Instead, it scopes only
-- its own RLS-protected lineage reads/writes to the tenant already carried by NEW.tenant_id, then
-- restores the caller's prior transaction-local tenant context. This does not authorize the caller;
-- the authoritative Task/Assignment write has already passed its own application/DB authority.

create or replace function phase12_4_capture_task_creation_lineage()
returns trigger language plpgsql as $$
declare
  event_kind varchar(32);
  actor_type varchar(32);
  actor_id varchar(128);
  previous_tenant text := current_setting('app.current_tenant_id', true);
begin
  perform set_config('app.current_tenant_id', new.tenant_id, true);

  event_kind := case when new.parent_task_id is not null then 'A2A_DELEGATED' else 'TASK_CREATED' end;
  actor_type := case when new.parent_task_id is not null and new.requesting_agent_id is not null then 'AGENT' else new.actor_principal_type end;
  actor_id := case when new.parent_task_id is not null and new.requesting_agent_id is not null then new.requesting_agent_id else new.actor_principal_id end;
  insert into task_lineage_evidence(
    tenant_id,evidence_id,root_task_id,task_id,parent_task_id,event_type,
    origin_principal_type,origin_principal_id,actor_principal_type,actor_principal_id,
    department_id,group_id,credential_id,oauth_client_id,source_system,correlation_id,trace_id,
    failure_domain,failure_code,reason,occurred_at)
  values(
    new.tenant_id,'create:'||new.task_id,coalesce(new.root_task_id,new.task_id),new.task_id,new.parent_task_id,event_kind,
    new.origin_principal_type,new.origin_principal_id,coalesce(actor_type,'SYSTEM'),coalesce(actor_id,'CORE'),
    case when new.parent_task_id is null then new.origin_department_id else new.requester_department_id end,
    case when new.parent_task_id is null then new.origin_group_id else new.requester_group_id end,
    new.origin_credential_id,new.origin_oauth_client_id,coalesce(new.origin_workload_source_system,new.source_system),
    coalesce(new.origin_correlation_id,new.correlation_id),new.trace_id,
    coalesce(new.failure_domain,'NONE'),new.failure_code,
    case when new.parent_task_id is null then 'Task creation provenance captured' else 'A2A child Task delegated from parent '||new.parent_task_id end,
    coalesce(new.provenance_captured_at,new.created_at,now()))
  on conflict(tenant_id,evidence_id) do nothing;

  perform set_config('app.current_tenant_id', coalesce(previous_tenant, ''), true);
  return new;
exception when others then
  perform set_config('app.current_tenant_id', coalesce(previous_tenant, ''), true);
  raise;
end $$;

create or replace function phase12_4_capture_assignment_lineage()
returns trigger language plpgsql as $$
declare
  t tasks%rowtype;
  ap agent_profiles%rowtype;
  prior_count integer;
  event_kind varchar(32);
  previous_tenant text := current_setting('app.current_tenant_id', true);
begin
  perform set_config('app.current_tenant_id', new.tenant_id, true);

  select * into t from tasks where tenant_id=new.tenant_id and task_id=new.task_id;
  if not found then
    perform set_config('app.current_tenant_id', coalesce(previous_tenant, ''), true);
    return new;
  end if;
  select * into ap from agent_profiles where tenant_id=new.tenant_id and agent_id=new.agent_id;
  select count(*) into prior_count from task_lineage_evidence
    where tenant_id=new.tenant_id and task_id=new.task_id and event_type in('EXECUTOR_ASSIGNED','EXECUTOR_REASSIGNED');
  event_kind := case when prior_count=0 then 'EXECUTOR_ASSIGNED' else 'EXECUTOR_REASSIGNED' end;
  insert into task_lineage_evidence(
    tenant_id,evidence_id,root_task_id,task_id,parent_task_id,event_type,
    origin_principal_type,origin_principal_id,actor_principal_type,actor_principal_id,
    executor_agent_id,assignment_id,department_id,group_id,credential_id,oauth_client_id,source_system,
    correlation_id,trace_id,failure_domain,failure_code,reason,occurred_at)
  values(
    new.tenant_id,'assignment:'||new.assignment_id,coalesce(t.root_task_id,t.task_id),t.task_id,t.parent_task_id,event_kind,
    t.origin_principal_type,t.origin_principal_id,'SYSTEM','DISPATCH_AUTHORITY',new.agent_id,new.assignment_id,
    coalesce(ap.owner_department_id,t.executor_department_id,'UNASSIGNED'),coalesce(ap.owner_group_id,t.executor_group_id),
    t.origin_credential_id,t.origin_oauth_client_id,coalesce(t.origin_workload_source_system,t.source_system),
    coalesce(new.correlation_id,t.correlation_id),t.trace_id,'NONE',null,coalesce(new.reason,'Executor assignment captured'),coalesce(new.created_at,now()))
  on conflict(tenant_id,evidence_id) do nothing;

  perform set_config('app.current_tenant_id', coalesce(previous_tenant, ''), true);
  return new;
exception when others then
  perform set_config('app.current_tenant_id', coalesce(previous_tenant, ''), true);
  raise;
end $$;

create or replace function phase12_4_capture_task_outcome_lineage()
returns trigger language plpgsql as $$
declare
  kind varchar(32);
  eid varchar(180);
  latest_agent varchar(128);
  latest_assignment varchar(128);
  previous_tenant text := current_setting('app.current_tenant_id', true);
begin
  if old.status is not distinct from new.status then return new; end if;
  if new.status in ('FAILED','DEAD_LETTER','ESCALATED','TIMED_OUT','EXPIRED') then kind := 'EXECUTION_FAILED';
  elsif new.status in ('SUCCEEDED','COMPLETED') then kind := 'EXECUTION_COMPLETED';
  else return new; end if;

  perform set_config('app.current_tenant_id', new.tenant_id, true);

  select agent_id,assignment_id into latest_agent,latest_assignment from task_assignments
    where tenant_id=new.tenant_id and task_id=new.task_id order by created_at desc limit 1;
  eid := lower(kind)||':'||new.task_id||':'||coalesce(new.version,1)::text;
  insert into task_lineage_evidence(
    tenant_id,evidence_id,root_task_id,task_id,parent_task_id,event_type,
    origin_principal_type,origin_principal_id,actor_principal_type,actor_principal_id,
    executor_agent_id,assignment_id,department_id,group_id,credential_id,oauth_client_id,source_system,
    correlation_id,trace_id,failure_domain,failure_code,reason,occurred_at)
  values(
    new.tenant_id,eid,coalesce(new.root_task_id,new.task_id),new.task_id,new.parent_task_id,kind,
    new.origin_principal_type,new.origin_principal_id,coalesce(new.last_transition_actor_type,new.actor_principal_type,'SYSTEM'),coalesce(new.last_transition_actor_id,new.actor_principal_id,'CORE'),
    latest_agent,latest_assignment,new.executor_department_id,new.executor_group_id,new.origin_credential_id,new.origin_oauth_client_id,
    coalesce(new.origin_workload_source_system,new.source_system),new.correlation_id,new.trace_id,
    case when kind='EXECUTION_FAILED' then coalesce(new.failure_domain,'UNKNOWN') else 'NONE' end,
    case when kind='EXECUTION_FAILED' then new.failure_code else null end,
    coalesce(new.lifecycle_reason,new.dispatch_retry_reason,new.status),coalesce(new.failure_at,new.terminal_at,new.updated_at,now()))
  on conflict(tenant_id,evidence_id) do nothing;

  perform set_config('app.current_tenant_id', coalesce(previous_tenant, ''), true);
  return new;
exception when others then
  perform set_config('app.current_tenant_id', coalesce(previous_tenant, ''), true);
  raise;
end $$;
