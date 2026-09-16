-- Phase 12.4 — Task / Agent Lineage & Resource Governance
-- Lineage is immutable historical evidence. Current RBAC / Resource Access remains authorization authority.

alter table tasks add column if not exists failure_domain varchar(32) not null default 'NONE';
alter table tasks add column if not exists failure_code varchar(128);
alter table tasks add column if not exists failure_at timestamptz;

alter table tasks drop constraint if exists ck_tasks_failure_domain;
alter table tasks add constraint ck_tasks_failure_domain check (failure_domain in (
  'NONE','CALLER_INPUT','AUTHENTICATION','AUTHORIZATION','ROUTING','AGENT_RUNTIME','A2A','EXTERNAL_SYSTEM','PLATFORM','UNKNOWN'
));
create index if not exists idx_tasks_failure_investigation
  on tasks(tenant_id,failure_domain,failure_at desc,task_id) where failure_domain <> 'NONE';
create index if not exists idx_tasks_lineage_root
  on tasks(tenant_id,root_task_id,created_at,task_id);
create index if not exists idx_tasks_lineage_parent
  on tasks(tenant_id,parent_task_id,created_at,task_id) where parent_task_id is not null;
create index if not exists idx_tasks_requesting_agent
  on tasks(tenant_id,requesting_agent_id,created_at desc,task_id) where requesting_agent_id is not null;

create table if not exists task_lineage_evidence (
  tenant_id varchar(64) not null,
  evidence_id varchar(180) not null,
  root_task_id varchar(128) not null,
  task_id varchar(128) not null,
  parent_task_id varchar(128),
  event_type varchar(32) not null,
  origin_principal_type varchar(32) not null,
  origin_principal_id varchar(128) not null,
  actor_principal_type varchar(32) not null,
  actor_principal_id varchar(128) not null,
  executor_agent_id varchar(128),
  assignment_id varchar(128),
  department_id varchar(128),
  group_id varchar(128),
  credential_id varchar(128),
  oauth_client_id varchar(96),
  source_system varchar(160),
  correlation_id varchar(128),
  trace_id varchar(64),
  failure_domain varchar(32) not null default 'NONE',
  failure_code varchar(128),
  reason text,
  occurred_at timestamptz not null,
  created_at timestamptz not null default now(),
  primary key(tenant_id,evidence_id),
  check(event_type in ('TASK_CREATED','A2A_DELEGATED','EXECUTOR_ASSIGNED','EXECUTOR_REASSIGNED','EXECUTION_FAILED','EXECUTION_COMPLETED')),
  check(failure_domain in ('NONE','CALLER_INPUT','AUTHENTICATION','AUTHORIZATION','ROUTING','AGENT_RUNTIME','A2A','EXTERNAL_SYSTEM','PLATFORM','UNKNOWN'))
);

create index if not exists idx_task_lineage_evidence_task
  on task_lineage_evidence(tenant_id,task_id,occurred_at,evidence_id);
create index if not exists idx_task_lineage_evidence_root
  on task_lineage_evidence(tenant_id,root_task_id,occurred_at,evidence_id);
create index if not exists idx_task_lineage_evidence_agent
  on task_lineage_evidence(tenant_id,executor_agent_id,occurred_at desc,evidence_id) where executor_agent_id is not null;
create index if not exists idx_task_lineage_evidence_correlation
  on task_lineage_evidence(tenant_id,correlation_id,occurred_at desc,evidence_id) where correlation_id is not null;
create index if not exists idx_task_lineage_evidence_failure
  on task_lineage_evidence(tenant_id,failure_domain,occurred_at desc,evidence_id) where failure_domain <> 'NONE';

create or replace function phase12_4_reject_lineage_mutation()
returns trigger language plpgsql as $$
begin
  raise exception 'TASK_LINEAGE_IMMUTABLE';
end $$;
drop trigger if exists trg_task_lineage_immutable on task_lineage_evidence;
create trigger trg_task_lineage_immutable before update or delete on task_lineage_evidence
for each row execute function phase12_4_reject_lineage_mutation();

-- Deterministic coarse failure classification. This is investigation metadata, never blame or authorization.
create or replace function phase12_4_classify_task_failure()
returns trigger language plpgsql as $$
declare material text;
begin
  if new.status not in ('FAILED','DEAD_LETTER','ESCALATED','TIMED_OUT','EXPIRED','BLOCKED','RETRY_WAIT') then
    if new.status in ('SUCCEEDED','COMPLETED') then
      new.failure_domain := 'NONE'; new.failure_code := null; new.failure_at := null;
    end if;
    return new;
  end if;
  material := upper(coalesce(new.dispatch_retry_reason,'')||' '||coalesce(new.lifecycle_reason,'')||' '||coalesce(new.error_code,''));
  if material ~ '(UNAUTHENTICATED|INVALID_TOKEN|TOKEN_EXPIRED|CREDENTIAL|LOGIN|AUTHENTICATION)' then new.failure_domain := 'AUTHENTICATION';
  elsif material ~ '(FORBIDDEN|PERMISSION|ACCESS_DENIED|AUTHORIZATION|IAM_|RBAC_)' then new.failure_domain := 'AUTHORIZATION';
  elsif material ~ '(NO_CANDIDATE|ROUTING|DISPATCH_PROFILE|CONFIGURATION|POOL|WAITING_CONFIGURATION)' then new.failure_domain := 'ROUTING';
  elsif coalesce(new.a2a_policy_id,'') <> '' and material ~ '(A2A|CHILD|DELEGAT|HANDOFF)' then new.failure_domain := 'A2A';
  elsif material ~ '(CALLBACK|AGENT|EXECUTION|LEASE|FENCING|SESSION|HEARTBEAT)' then new.failure_domain := 'AGENT_RUNTIME';
  elsif material ~ '(INVALID_INPUT|VALIDATION|MALFORMED|BAD_REQUEST|CALLER)' then new.failure_domain := 'CALLER_INPUT';
  elsif material ~ '(UPSTREAM|DOWNSTREAM|EXTERNAL|ERP|MES|SAP)' then new.failure_domain := 'EXTERNAL_SYSTEM';
  elsif new.status in ('FAILED','DEAD_LETTER','ESCALATED','TIMED_OUT','EXPIRED') then new.failure_domain := 'UNKNOWN';
  else new.failure_domain := coalesce(nullif(new.failure_domain,'NONE'),'UNKNOWN');
  end if;
  new.failure_code := coalesce(nullif(new.failure_code,''),nullif(new.error_code,''),nullif(split_part(coalesce(new.dispatch_retry_reason,''),':',1),''),new.status);
  new.failure_at := coalesce(new.failure_at,new.updated_at,now());
  return new;
end $$;

drop trigger if exists trg_tasks_classify_failure on tasks;
create trigger trg_tasks_classify_failure
before insert or update of status,error_code,lifecycle_reason,dispatch_retry_reason on tasks
for each row execute function phase12_4_classify_task_failure();

-- Classify historical failure rows without inventing a caller/Agent cause.
update tasks set status=status
where status in ('FAILED','DEAD_LETTER','ESCALATED','TIMED_OUT','EXPIRED','BLOCKED','RETRY_WAIT') and failure_domain='NONE';

-- Creation/A2A lineage is derived only from server-owned Task evidence.
create or replace function phase12_4_capture_task_creation_lineage()
returns trigger language plpgsql as $$
declare event_kind varchar(32); actor_type varchar(32); actor_id varchar(128);
begin
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
  return new;
end $$;

drop trigger if exists trg_tasks_capture_creation_lineage on tasks;
create trigger trg_tasks_capture_creation_lineage after insert on tasks
for each row execute function phase12_4_capture_task_creation_lineage();

-- Assignment history is immutable even when current executor changes later.
create or replace function phase12_4_capture_assignment_lineage()
returns trigger language plpgsql as $$
declare t tasks%rowtype; ap agent_profiles%rowtype; prior_count integer; event_kind varchar(32);
begin
  select * into t from tasks where tenant_id=new.tenant_id and task_id=new.task_id;
  if not found then return new; end if;
  select * into ap from agent_profiles where tenant_id=new.tenant_id and agent_id=new.agent_id;
  select count(*) into prior_count from task_lineage_evidence where tenant_id=new.tenant_id and task_id=new.task_id and event_type in('EXECUTOR_ASSIGNED','EXECUTOR_REASSIGNED');
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
  return new;
end $$;

drop trigger if exists trg_task_assignments_capture_lineage on task_assignments;
create trigger trg_task_assignments_capture_lineage after insert on task_assignments
for each row execute function phase12_4_capture_assignment_lineage();

-- Failure/completion milestones are appended only when entering the terminal outcome.
create or replace function phase12_4_capture_task_outcome_lineage()
returns trigger language plpgsql as $$
declare kind varchar(32); eid varchar(180); latest_agent varchar(128); latest_assignment varchar(128);
begin
  if old.status is not distinct from new.status then return new; end if;
  if new.status in ('FAILED','DEAD_LETTER','ESCALATED','TIMED_OUT','EXPIRED') then kind := 'EXECUTION_FAILED';
  elsif new.status in ('SUCCEEDED','COMPLETED') then kind := 'EXECUTION_COMPLETED';
  else return new; end if;
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
  return new;
end $$;

drop trigger if exists trg_tasks_capture_outcome_lineage on tasks;
create trigger trg_tasks_capture_outcome_lineage after update of status on tasks
for each row execute function phase12_4_capture_task_outcome_lineage();

-- Conservative historical backfill: only facts already stored in canonical Task/Assignment rows are used.
insert into task_lineage_evidence(
  tenant_id,evidence_id,root_task_id,task_id,parent_task_id,event_type,
  origin_principal_type,origin_principal_id,actor_principal_type,actor_principal_id,
  department_id,group_id,credential_id,oauth_client_id,source_system,correlation_id,trace_id,
  failure_domain,failure_code,reason,occurred_at)
select t.tenant_id,'create:'||t.task_id,coalesce(t.root_task_id,t.task_id),t.task_id,t.parent_task_id,
       case when t.parent_task_id is null then 'TASK_CREATED' else 'A2A_DELEGATED' end,
       t.origin_principal_type,t.origin_principal_id,
       case when t.parent_task_id is not null and t.requesting_agent_id is not null then 'AGENT' else t.actor_principal_type end,
       case when t.parent_task_id is not null and t.requesting_agent_id is not null then t.requesting_agent_id else t.actor_principal_id end,
       case when t.parent_task_id is null then t.origin_department_id else t.requester_department_id end,
       case when t.parent_task_id is null then t.origin_group_id else t.requester_group_id end,
       t.origin_credential_id,t.origin_oauth_client_id,coalesce(t.origin_workload_source_system,t.source_system),
       coalesce(t.origin_correlation_id,t.correlation_id),t.trace_id,coalesce(t.failure_domain,'NONE'),t.failure_code,
       'Historical lineage backfill from canonical Task evidence',coalesce(t.provenance_captured_at,t.created_at,now())
from tasks t on conflict(tenant_id,evidence_id) do nothing;

insert into task_lineage_evidence(
  tenant_id,evidence_id,root_task_id,task_id,parent_task_id,event_type,
  origin_principal_type,origin_principal_id,actor_principal_type,actor_principal_id,
  executor_agent_id,assignment_id,department_id,group_id,credential_id,oauth_client_id,source_system,
  correlation_id,trace_id,failure_domain,failure_code,reason,occurred_at)
select a.tenant_id,'assignment:'||a.assignment_id,coalesce(t.root_task_id,t.task_id),t.task_id,t.parent_task_id,
       case when row_number() over(partition by a.tenant_id,a.task_id order by a.created_at,a.assignment_id)=1 then 'EXECUTOR_ASSIGNED' else 'EXECUTOR_REASSIGNED' end,
       t.origin_principal_type,t.origin_principal_id,'SYSTEM','DISPATCH_AUTHORITY',a.agent_id,a.assignment_id,
       coalesce(ap.owner_department_id,t.executor_department_id,'UNASSIGNED'),coalesce(ap.owner_group_id,t.executor_group_id),
       t.origin_credential_id,t.origin_oauth_client_id,coalesce(t.origin_workload_source_system,t.source_system),
       coalesce(a.correlation_id,t.correlation_id),t.trace_id,'NONE',null,coalesce(a.reason,'Historical assignment evidence'),coalesce(a.created_at,t.created_at,now())
from task_assignments a join tasks t on t.tenant_id=a.tenant_id and t.task_id=a.task_id
left join agent_profiles ap on ap.tenant_id=a.tenant_id and ap.agent_id=a.agent_id
on conflict(tenant_id,evidence_id) do nothing;

insert into task_lineage_evidence(
  tenant_id,evidence_id,root_task_id,task_id,parent_task_id,event_type,
  origin_principal_type,origin_principal_id,actor_principal_type,actor_principal_id,
  executor_agent_id,assignment_id,department_id,group_id,credential_id,oauth_client_id,source_system,
  correlation_id,trace_id,failure_domain,failure_code,reason,occurred_at)
select t.tenant_id,'historical-outcome:'||t.task_id,coalesce(t.root_task_id,t.task_id),t.task_id,t.parent_task_id,
       case when t.status in('SUCCEEDED','COMPLETED') then 'EXECUTION_COMPLETED' else 'EXECUTION_FAILED' end,
       t.origin_principal_type,t.origin_principal_id,coalesce(t.last_transition_actor_type,t.actor_principal_type,'SYSTEM'),
       coalesce(t.last_transition_actor_id,t.actor_principal_id,'CORE'),
       a.agent_id,a.assignment_id,t.executor_department_id,t.executor_group_id,t.origin_credential_id,t.origin_oauth_client_id,
       coalesce(t.origin_workload_source_system,t.source_system),t.correlation_id,t.trace_id,
       case when t.status in('SUCCEEDED','COMPLETED') then 'NONE' else coalesce(t.failure_domain,'UNKNOWN') end,
       case when t.status in('SUCCEEDED','COMPLETED') then null else t.failure_code end,
       coalesce(t.lifecycle_reason,t.dispatch_retry_reason,'Historical terminal Task outcome'),
       coalesce(t.failure_at,t.terminal_at,t.updated_at,t.created_at,now())
from tasks t
left join lateral (
  select ta.agent_id,ta.assignment_id from task_assignments ta
  where ta.tenant_id=t.tenant_id and ta.task_id=t.task_id order by ta.created_at desc limit 1
) a on true
where t.status in('SUCCEEDED','COMPLETED','FAILED','DEAD_LETTER','ESCALATED','TIMED_OUT','EXPIRED')
on conflict(tenant_id,evidence_id) do nothing;

alter table task_lineage_evidence enable row level security;
alter table task_lineage_evidence force row level security;
drop policy if exists tenant_isolation on task_lineage_evidence;
create policy tenant_isolation on task_lineage_evidence
  using (tenant_id = iam_current_tenant_id()) with check (tenant_id = iam_current_tenant_id());

comment on table task_lineage_evidence is 'Phase 12.4 immutable Task/Agent execution lineage. It is historical investigation evidence, not current RBAC authority.';
comment on column tasks.failure_domain is 'Coarse RCA classification. It identifies where failure manifested and must never be interpreted as human/agent blame.';
