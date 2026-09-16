-- Phase 12.6 — Enterprise Analytics Projection & Historical Organization Dimensions
-- Analytics is a rebuildable read model only. IAM/RBAC, Task, Agent and Security Incident OLTP remain authority.
-- Flyway runs instance-scoped, but every FORCE-RLS tenant source read below is explicitly switched to its tenant.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','phase12-6-analytics-migration',true);
-- Temporary compatibility for Phase 12.5 tables until their policy is converged below.
select set_config('app.tenant_id','INSTANCE',true);

-- ---------------------------------------------------------------------------
-- Slowly changing organization dimensions (SCD2). Initial rows cover historical data; future changes close/open versions.
-- ---------------------------------------------------------------------------
create table if not exists analytics_dim_tenant_history (
  tenant_id varchar(64) not null,
  dimension_version_id varchar(160) not null,
  display_name varchar(255) not null,
  status varchar(32) not null,
  row_hash varchar(64) not null,
  valid_from timestamptz not null,
  valid_to timestamptz,
  is_current boolean not null default true,
  primary key(tenant_id,dimension_version_id)
);
create unique index if not exists ux_analytics_dim_tenant_current on analytics_dim_tenant_history(tenant_id) where is_current;
create index if not exists idx_analytics_dim_tenant_validity on analytics_dim_tenant_history(tenant_id,valid_from,valid_to);

create table if not exists analytics_dim_department_history (
  tenant_id varchar(64) not null,
  department_id varchar(128) not null,
  dimension_version_id varchar(220) not null,
  department_code varchar(128) not null,
  department_name varchar(255) not null,
  status varchar(32) not null,
  row_hash varchar(64) not null,
  valid_from timestamptz not null,
  valid_to timestamptz,
  is_current boolean not null default true,
  primary key(tenant_id,department_id,dimension_version_id)
);
create unique index if not exists ux_analytics_dim_department_current on analytics_dim_department_history(tenant_id,department_id) where is_current;
create index if not exists idx_analytics_dim_department_validity on analytics_dim_department_history(tenant_id,department_id,valid_from,valid_to);

create table if not exists analytics_dim_group_history (
  tenant_id varchar(64) not null,
  group_id varchar(128) not null,
  dimension_version_id varchar(220) not null,
  group_code varchar(128) not null,
  group_name varchar(255) not null,
  group_type varchar(32) not null,
  status varchar(32) not null,
  row_hash varchar(64) not null,
  valid_from timestamptz not null,
  valid_to timestamptz,
  is_current boolean not null default true,
  primary key(tenant_id,group_id,dimension_version_id)
);
create unique index if not exists ux_analytics_dim_group_current on analytics_dim_group_history(tenant_id,group_id) where is_current;
create index if not exists idx_analytics_dim_group_validity on analytics_dim_group_history(tenant_id,group_id,valid_from,valid_to);

-- Seed current organization rows so existing historical Tasks resolve deterministically. We cannot invent pre-Phase-12.6 rename history.
insert into analytics_dim_tenant_history(tenant_id,dimension_version_id,display_name,status,row_hash,valid_from,is_current)
select t.tenant_id,'tenant:'||t.tenant_id||':'||substr(md5(t.display_name||'|'||t.status),1,20),t.display_name,t.status,
       md5(t.display_name||'|'||t.status),'1970-01-01'::timestamptz,true
from tenants t
on conflict do nothing;

-- Department and Group are FORCE-RLS authorities. Seed them one Tenant at a time; INSTANCE cannot scan them.
do $$
declare tenant_value varchar(64); previous_tenant text := current_setting('app.current_tenant_id',true);
begin
  for tenant_value in select tenant_id from tenants order by tenant_id loop
    perform set_config('app.current_tenant_id',tenant_value,true);
    perform set_config('app.tenant_id',tenant_value,true);
    insert into analytics_dim_department_history(tenant_id,department_id,dimension_version_id,department_code,department_name,status,row_hash,valid_from,is_current)
    select d.tenant_id,d.department_id,'dept:'||d.department_id||':'||substr(md5(d.department_code||'|'||d.department_name||'|'||d.status),1,20),
           d.department_code,d.department_name,d.status,md5(d.department_code||'|'||d.department_name||'|'||d.status),'1970-01-01'::timestamptz,true
      from departments d where d.tenant_id=tenant_value
    on conflict do nothing;

    insert into analytics_dim_group_history(tenant_id,group_id,dimension_version_id,group_code,group_name,group_type,status,row_hash,valid_from,is_current)
    select g.tenant_id,g.group_id,'group:'||g.group_id||':'||substr(md5(g.group_code||'|'||g.group_name||'|'||g.group_type||'|'||g.status),1,20),
           g.group_code,g.group_name,g.group_type,g.status,md5(g.group_code||'|'||g.group_name||'|'||g.group_type||'|'||g.status),'1970-01-01'::timestamptz,true
      from organization_groups g where g.tenant_id=tenant_value
    on conflict do nothing;
  end loop;
  perform set_config('app.current_tenant_id',coalesce(nullif(previous_tenant,''),'INSTANCE'),true);
  perform set_config('app.tenant_id','INSTANCE',true);
exception when others then
  perform set_config('app.current_tenant_id',coalesce(nullif(previous_tenant,''),'INSTANCE'),true);
  perform set_config('app.tenant_id','INSTANCE',true);
  raise;
end $$;

create or replace function phase12_6_sync_tenant_dimension() returns trigger language plpgsql as $$
declare h varchar(64); at_time timestamptz := now(); current_hash varchar(64);
begin
  h:=md5(new.display_name||'|'||new.status);
  select row_hash into current_hash from analytics_dim_tenant_history where tenant_id=new.tenant_id and is_current;
  if current_hash is not distinct from h then return new; end if;
  update analytics_dim_tenant_history set valid_to=at_time,is_current=false where tenant_id=new.tenant_id and is_current;
  insert into analytics_dim_tenant_history(tenant_id,dimension_version_id,display_name,status,row_hash,valid_from,is_current)
  values(new.tenant_id,'tenant:'||new.tenant_id||':'||substr(h,1,12)||':'||to_char(at_time,'YYYYMMDDHH24MISSUS'),new.display_name,new.status,h,at_time,true)
  on conflict do nothing;
  return new;
end $$;
drop trigger if exists trg_phase12_6_tenant_dimension on tenants;
create trigger trg_phase12_6_tenant_dimension after insert or update of display_name,status on tenants for each row execute function phase12_6_sync_tenant_dimension();

create or replace function phase12_6_sync_department_dimension() returns trigger language plpgsql as $$
declare h varchar(64); at_time timestamptz := now(); current_hash varchar(64);
begin
  h:=md5(new.department_code||'|'||new.department_name||'|'||new.status);
  select row_hash into current_hash from analytics_dim_department_history where tenant_id=new.tenant_id and department_id=new.department_id and is_current;
  if current_hash is not distinct from h then return new; end if;
  update analytics_dim_department_history set valid_to=at_time,is_current=false where tenant_id=new.tenant_id and department_id=new.department_id and is_current;
  insert into analytics_dim_department_history(tenant_id,department_id,dimension_version_id,department_code,department_name,status,row_hash,valid_from,is_current)
  values(new.tenant_id,new.department_id,'dept:'||new.department_id||':'||substr(h,1,12)||':'||to_char(at_time,'YYYYMMDDHH24MISSUS'),new.department_code,new.department_name,new.status,h,at_time,true)
  on conflict do nothing;
  return new;
end $$;
drop trigger if exists trg_phase12_6_department_dimension on departments;
create trigger trg_phase12_6_department_dimension after insert or update of department_code,department_name,status on departments for each row execute function phase12_6_sync_department_dimension();

create or replace function phase12_6_sync_group_dimension() returns trigger language plpgsql as $$
declare h varchar(64); at_time timestamptz := now(); current_hash varchar(64);
begin
  h:=md5(new.group_code||'|'||new.group_name||'|'||new.group_type||'|'||new.status);
  select row_hash into current_hash from analytics_dim_group_history where tenant_id=new.tenant_id and group_id=new.group_id and is_current;
  if current_hash is not distinct from h then return new; end if;
  update analytics_dim_group_history set valid_to=at_time,is_current=false where tenant_id=new.tenant_id and group_id=new.group_id and is_current;
  insert into analytics_dim_group_history(tenant_id,group_id,dimension_version_id,group_code,group_name,group_type,status,row_hash,valid_from,is_current)
  values(new.tenant_id,new.group_id,'group:'||new.group_id||':'||substr(h,1,12)||':'||to_char(at_time,'YYYYMMDDHH24MISSUS'),new.group_code,new.group_name,new.group_type,new.status,h,at_time,true)
  on conflict do nothing;
  return new;
end $$;
drop trigger if exists trg_phase12_6_group_dimension on organization_groups;
create trigger trg_phase12_6_group_dimension after insert or update of group_code,group_name,group_type,status on organization_groups for each row execute function phase12_6_sync_group_dimension();

-- ---------------------------------------------------------------------------
-- Rebuildable facts. Dimension version ids preserve the historical label used at occurrence time.
-- ---------------------------------------------------------------------------
create table if not exists analytics_workload_fact (
  tenant_id varchar(64) not null,
  task_id varchar(128) not null,
  root_task_id varchar(128), parent_task_id varchar(128),
  occurred_at timestamptz not null, terminal_at timestamptz, updated_at timestamptz not null,
  origin_principal_type varchar(32), origin_principal_id varchar(128),
  actor_principal_type varchar(32), actor_principal_id varchar(128),
  credential_id varchar(128), oauth_client_id varchar(96), source_system varchar(160),
  origin_department_id varchar(128), origin_group_id varchar(128),
  origin_department_version_id varchar(220), origin_group_version_id varchar(220),
  owner_department_id varchar(128), owner_group_id varchar(128),
  assigned_agent_id varchar(128),
  task_type varchar(128), initial_priority varchar(32), initial_severity varchar(32), current_priority varchar(32), current_severity varchar(32),
  status varchar(32) not null, failure_domain varchar(32) not null default 'NONE', failure_code varchar(128),
  reassignment_count integer not null default 0, dispatch_attempt_count integer not null default 0,
  duration_ms bigint,
  primary key(tenant_id,task_id)
);
create index if not exists idx_analytics_workload_time on analytics_workload_fact(tenant_id,occurred_at desc,task_id);
create index if not exists idx_analytics_workload_origin_org on analytics_workload_fact(tenant_id,origin_department_id,origin_group_id,occurred_at desc);
create index if not exists idx_analytics_workload_origin_type on analytics_workload_fact(tenant_id,origin_principal_type,occurred_at desc);
create index if not exists idx_analytics_workload_agent on analytics_workload_fact(tenant_id,assigned_agent_id,occurred_at desc) where assigned_agent_id is not null;
create index if not exists idx_analytics_workload_credential on analytics_workload_fact(tenant_id,credential_id,occurred_at desc) where credential_id is not null;
create index if not exists idx_analytics_workload_failure on analytics_workload_fact(tenant_id,failure_domain,occurred_at desc) where failure_domain<>'NONE';
create index if not exists idx_analytics_workload_status on analytics_workload_fact(tenant_id,status,occurred_at desc);

create table if not exists analytics_agent_execution_fact (
  tenant_id varchar(64) not null, evidence_id varchar(180) not null,
  task_id varchar(128) not null, root_task_id varchar(128) not null, parent_task_id varchar(128),
  occurred_at timestamptz not null, event_type varchar(32) not null,
  origin_principal_type varchar(32), origin_principal_id varchar(128), actor_principal_type varchar(32), actor_principal_id varchar(128),
  executor_agent_id varchar(128), department_id varchar(128), group_id varchar(128), department_version_id varchar(220), group_version_id varchar(220),
  credential_id varchar(128), oauth_client_id varchar(96), source_system varchar(160), failure_domain varchar(32), failure_code varchar(128),
  primary key(tenant_id,evidence_id)
);
create index if not exists idx_analytics_agent_execution_agent on analytics_agent_execution_fact(tenant_id,executor_agent_id,occurred_at desc,evidence_id) where executor_agent_id is not null;
create index if not exists idx_analytics_agent_execution_org on analytics_agent_execution_fact(tenant_id,department_id,group_id,occurred_at desc);
create index if not exists idx_analytics_agent_execution_failure on analytics_agent_execution_fact(tenant_id,failure_domain,occurred_at desc) where failure_domain not in('NONE','');

create table if not exists analytics_security_incident_fact (
  tenant_id varchar(64) not null, case_id varchar(128) not null,
  opened_at timestamptz not null, contained_at timestamptz, resolved_at timestamptz,
  severity varchar(32) not null, status varchar(32) not null,
  root_task_id varchar(128), source_system_id varchar(128), owner_department_id varchar(128), owner_group_id varchar(128),
  owner_department_version_id varchar(220), owner_group_version_id varchar(220),
  active_control_count integer not null default 0, total_control_action_count integer not null default 0,
  containment_latency_ms bigint, resolution_latency_ms bigint,
  primary key(tenant_id,case_id)
);
create index if not exists idx_analytics_security_incident_time on analytics_security_incident_fact(tenant_id,opened_at desc,case_id);
create index if not exists idx_analytics_security_incident_org on analytics_security_incident_fact(tenant_id,owner_department_id,owner_group_id,opened_at desc);
create index if not exists idx_analytics_security_incident_status on analytics_security_incident_fact(tenant_id,status,severity,opened_at desc);

create table if not exists analytics_security_control_fact (
  tenant_id varchar(64) not null, action_id varchar(128) not null,
  case_id varchar(128) not null, control_id varchar(128), occurred_at timestamptz not null,
  target_type varchar(32) not null, target_id varchar(128) not null, action_type varchar(32) not null, actor_id varchar(128) not null,
  authorization_decision_id varchar(128), correlation_id varchar(128),
  primary key(tenant_id,action_id)
);
create index if not exists idx_analytics_security_control_case on analytics_security_control_fact(tenant_id,case_id,occurred_at desc);
create index if not exists idx_analytics_security_control_target on analytics_security_control_fact(tenant_id,target_type,target_id,occurred_at desc);
create index if not exists idx_analytics_security_control_actor on analytics_security_control_fact(tenant_id,actor_id,occurred_at desc);

-- Projection event ledger and checkpoint make incremental delivery observable and externally replayable.
create table if not exists enterprise_analytics_projection_events (
  tenant_id varchar(64) not null, event_id varchar(220) not null,
  source_type varchar(40) not null, source_id varchar(180) not null, event_type varchar(64) not null,
  occurred_at timestamptz not null, payload_hash varchar(64) not null,
  projection_status varchar(24) not null default 'PROJECTED', projected_at timestamptz, created_at timestamptz not null default now(),
  primary key(tenant_id,event_id), check(projection_status in('PENDING','PROJECTED','FAILED','DEAD_LETTER'))
);
create index if not exists idx_enterprise_analytics_projection_events_status on enterprise_analytics_projection_events(tenant_id,projection_status,occurred_at,event_id);

create table if not exists enterprise_analytics_projection_checkpoints (
  tenant_id varchar(64) not null, projection_name varchar(64) not null,
  last_event_id varchar(220), last_event_at timestamptz, last_projected_at timestamptz,
  projected_event_count bigint not null default 0, failure_count bigint not null default 0,
  rebuild_required boolean not null default false, updated_at timestamptz not null default now(),
  primary key(tenant_id,projection_name)
);

create or replace function phase12_6_dimension_version(p_tenant varchar,p_type varchar,p_id varchar,p_at timestamptz) returns varchar language plpgsql stable as $$
declare v varchar(220);
begin
  if p_id is null or btrim(p_id)='' then return null; end if;
  if p_type='DEPARTMENT' then
    select dimension_version_id into v from analytics_dim_department_history where tenant_id=p_tenant and department_id=p_id and valid_from<=p_at and (valid_to is null or valid_to>p_at) order by valid_from desc limit 1;
  elsif p_type='GROUP' then
    select dimension_version_id into v from analytics_dim_group_history where tenant_id=p_tenant and group_id=p_id and valid_from<=p_at and (valid_to is null or valid_to>p_at) order by valid_from desc limit 1;
  end if;
  return v;
end $$;

create or replace function phase12_6_touch_checkpoint(p_tenant varchar,p_event_id varchar,p_occurred timestamptz) returns void language plpgsql as $$
begin
  insert into enterprise_analytics_projection_checkpoints(tenant_id,projection_name,last_event_id,last_event_at,last_projected_at,projected_event_count,updated_at)
  values(p_tenant,'ENTERPRISE_ANALYTICS',p_event_id,p_occurred,now(),1,now())
  on conflict(tenant_id,projection_name) do update set last_event_id=excluded.last_event_id,last_event_at=greatest(enterprise_analytics_projection_checkpoints.last_event_at,excluded.last_event_at),
    last_projected_at=now(),projected_event_count=enterprise_analytics_projection_checkpoints.projected_event_count+1,updated_at=now();
end $$;

-- OLTP triggers enqueue only. Fact rebuild happens asynchronously in bounded batches.
create or replace function phase12_6_enqueue_task_projection() returns trigger language plpgsql as $$
declare event_id varchar(220); payload_hash varchar(64); at_time timestamptz := coalesce(new.updated_at,new.created_at,now());
begin
  payload_hash:=md5(new.task_id||'|'||new.status||'|'||coalesce(new.updated_at::text,'')||'|'||coalesce(new.failure_domain,''));
  event_id:='task:'||new.task_id||':'||substr(payload_hash,1,24);
  insert into enterprise_analytics_projection_events(tenant_id,event_id,source_type,source_id,event_type,occurred_at,payload_hash,projection_status)
  values(new.tenant_id,event_id,'TASK',new.task_id,'TASK_UPSERT',at_time,payload_hash,'PENDING') on conflict do nothing;
  return new;
end $$;
drop trigger if exists trg_phase12_6_project_task_fact on tasks;
drop trigger if exists trg_phase12_6_enqueue_task_projection on tasks;
create trigger trg_phase12_6_enqueue_task_projection after insert or update on tasks for each row execute function phase12_6_enqueue_task_projection();

create or replace function phase12_6_enqueue_lineage_projection() returns trigger language plpgsql as $$
begin
  insert into enterprise_analytics_projection_events(tenant_id,event_id,source_type,source_id,event_type,occurred_at,payload_hash,projection_status)
  values(new.tenant_id,'lineage:'||new.evidence_id,'TASK_LINEAGE',new.evidence_id,new.event_type,new.occurred_at,md5(row_to_json(new)::text),'PENDING') on conflict do nothing;
  return new;
end $$;
drop trigger if exists trg_phase12_6_project_agent_execution_fact on task_lineage_evidence;
drop trigger if exists trg_phase12_6_enqueue_lineage_projection on task_lineage_evidence;
create trigger trg_phase12_6_enqueue_lineage_projection after insert on task_lineage_evidence for each row execute function phase12_6_enqueue_lineage_projection();

create or replace function phase12_6_enqueue_incident_projection() returns trigger language plpgsql as $$
declare at_time timestamptz:=coalesce(new.last_action_at,new.opened_at,now());
begin
  insert into enterprise_analytics_projection_events(tenant_id,event_id,source_type,source_id,event_type,occurred_at,payload_hash,projection_status)
  values(new.tenant_id,'incident:'||new.case_id||':'||new.version::text,'SECURITY_INCIDENT',new.case_id,'INCIDENT_UPSERT',at_time,
         md5(new.case_id||'|'||new.status||'|'||new.version::text),'PENDING') on conflict do nothing;
  return new;
end $$;
drop trigger if exists trg_phase12_6_project_incident_fact on security_incident_cases;
drop trigger if exists trg_phase12_6_enqueue_incident_projection on security_incident_cases;
create trigger trg_phase12_6_enqueue_incident_projection after insert or update on security_incident_cases for each row execute function phase12_6_enqueue_incident_projection();

create or replace function phase12_6_enqueue_security_control_projection() returns trigger language plpgsql as $$
begin
  insert into enterprise_analytics_projection_events(tenant_id,event_id,source_type,source_id,event_type,occurred_at,payload_hash,projection_status)
  values(new.tenant_id,'control-action:'||new.action_id,'SECURITY_CONTROL',new.action_id,new.action_type,new.occurred_at,md5(row_to_json(new)::text),'PENDING') on conflict do nothing;
  return new;
end $$;
drop trigger if exists trg_phase12_6_project_security_control_fact on security_control_action_evidence;
drop trigger if exists trg_phase12_6_enqueue_security_control_projection on security_control_action_evidence;
create trigger trg_phase12_6_enqueue_security_control_projection after insert on security_control_action_evidence for each row execute function phase12_6_enqueue_security_control_projection();

create or replace function phase12_6_rebuild_task_fact(p_tenant varchar,p_task varchar) returns void language plpgsql as $$
begin
  insert into analytics_workload_fact(tenant_id,task_id,root_task_id,parent_task_id,occurred_at,terminal_at,updated_at,
    origin_principal_type,origin_principal_id,actor_principal_type,actor_principal_id,credential_id,oauth_client_id,source_system,
    origin_department_id,origin_group_id,origin_department_version_id,origin_group_version_id,owner_department_id,owner_group_id,assigned_agent_id,
    task_type,initial_priority,initial_severity,current_priority,current_severity,status,failure_domain,failure_code,reassignment_count,dispatch_attempt_count,duration_ms)
  select t.tenant_id,t.task_id,t.root_task_id,t.parent_task_id,t.created_at,t.terminal_at,t.updated_at,t.origin_principal_type,t.origin_principal_id,t.actor_principal_type,t.actor_principal_id,
    t.origin_credential_id,t.origin_oauth_client_id,coalesce(t.origin_workload_source_system,t.source_system),t.origin_department_id,t.origin_group_id,
    phase12_6_dimension_version(t.tenant_id,'DEPARTMENT',t.origin_department_id,t.created_at),phase12_6_dimension_version(t.tenant_id,'GROUP',t.origin_group_id,t.created_at),
    t.owner_department_id,t.owner_group_id,(select a.agent_id from task_assignments a where a.task_id=t.task_id order by a.created_at desc limit 1),t.task_type,
    coalesce(t.initial_priority,t.priority),coalesce(t.initial_severity,t.severity),t.priority,t.severity,t.status,coalesce(t.failure_domain,'NONE'),t.failure_code,
    coalesce(t.reassignment_count,0),coalesce(t.dispatch_attempt_count,0),case when t.terminal_at is null then null else greatest(0,(extract(epoch from(t.terminal_at-t.created_at))*1000)::bigint) end
  from tasks t where t.tenant_id=p_tenant and t.task_id=p_task
  on conflict(tenant_id,task_id) do update set root_task_id=excluded.root_task_id,parent_task_id=excluded.parent_task_id,terminal_at=excluded.terminal_at,updated_at=excluded.updated_at,
    owner_department_id=excluded.owner_department_id,owner_group_id=excluded.owner_group_id,assigned_agent_id=excluded.assigned_agent_id,current_priority=excluded.current_priority,current_severity=excluded.current_severity,
    status=excluded.status,failure_domain=excluded.failure_domain,failure_code=excluded.failure_code,reassignment_count=excluded.reassignment_count,dispatch_attempt_count=excluded.dispatch_attempt_count,duration_ms=excluded.duration_ms;
end $$;

create or replace function phase12_6_rebuild_lineage_fact(p_tenant varchar,p_evidence varchar) returns void language plpgsql as $$
declare e task_lineage_evidence%rowtype;
begin
  select * into e from task_lineage_evidence where tenant_id=p_tenant and evidence_id=p_evidence;
  if not found then return; end if;
  insert into analytics_agent_execution_fact(tenant_id,evidence_id,task_id,root_task_id,parent_task_id,occurred_at,event_type,origin_principal_type,origin_principal_id,
    actor_principal_type,actor_principal_id,executor_agent_id,department_id,group_id,department_version_id,group_version_id,credential_id,oauth_client_id,source_system,failure_domain,failure_code)
  values(e.tenant_id,e.evidence_id,e.task_id,e.root_task_id,e.parent_task_id,e.occurred_at,e.event_type,e.origin_principal_type,e.origin_principal_id,
    e.actor_principal_type,e.actor_principal_id,e.executor_agent_id,e.department_id,e.group_id,
    phase12_6_dimension_version(e.tenant_id,'DEPARTMENT',e.department_id,e.occurred_at),phase12_6_dimension_version(e.tenant_id,'GROUP',e.group_id,e.occurred_at),
    e.credential_id,e.oauth_client_id,e.source_system,e.failure_domain,e.failure_code) on conflict do nothing;
  if e.event_type in('EXECUTOR_ASSIGNED','EXECUTOR_REASSIGNED') and e.executor_agent_id is not null then
    update analytics_workload_fact set assigned_agent_id=e.executor_agent_id,updated_at=greatest(updated_at,e.occurred_at)
     where tenant_id=e.tenant_id and task_id=e.task_id;
  end if;
end $$;

create or replace function phase12_6_refresh_incident_fact(p_tenant varchar,p_case varchar) returns void language plpgsql as $$
begin
  insert into analytics_security_incident_fact(tenant_id,case_id,opened_at,contained_at,resolved_at,severity,status,root_task_id,source_system_id,owner_department_id,owner_group_id,
    owner_department_version_id,owner_group_version_id,active_control_count,total_control_action_count,containment_latency_ms,resolution_latency_ms)
  select c.tenant_id,c.case_id,c.opened_at,c.contained_at,c.resolved_at,c.severity,c.status,c.root_task_id,c.source_system_id,c.owner_department_id,c.owner_group_id,
    phase12_6_dimension_version(c.tenant_id,'DEPARTMENT',c.owner_department_id,c.opened_at),phase12_6_dimension_version(c.tenant_id,'GROUP',c.owner_group_id,c.opened_at),
    (select count(*) from security_resource_controls r where r.tenant_id=c.tenant_id and r.case_id=c.case_id and r.status='ACTIVE' and (r.expires_at is null or r.expires_at>now())),
    (select count(*) from security_control_action_evidence x where x.tenant_id=c.tenant_id and x.case_id=c.case_id),
    case when c.contained_at is null then null else greatest(0,(extract(epoch from(c.contained_at-c.opened_at))*1000)::bigint) end,
    case when c.resolved_at is null then null else greatest(0,(extract(epoch from(c.resolved_at-c.opened_at))*1000)::bigint) end
  from security_incident_cases c where c.tenant_id=p_tenant and c.case_id=p_case
  on conflict(tenant_id,case_id) do update set contained_at=excluded.contained_at,resolved_at=excluded.resolved_at,severity=excluded.severity,status=excluded.status,
    active_control_count=excluded.active_control_count,total_control_action_count=excluded.total_control_action_count,containment_latency_ms=excluded.containment_latency_ms,resolution_latency_ms=excluded.resolution_latency_ms;
end $$;

create or replace function phase12_6_rebuild_security_control_fact(p_tenant varchar,p_action varchar) returns void language plpgsql as $$
declare case_key varchar(128);
begin
  insert into analytics_security_control_fact(tenant_id,action_id,case_id,control_id,occurred_at,target_type,target_id,action_type,actor_id,authorization_decision_id,correlation_id)
  select tenant_id,action_id,case_id,control_id,occurred_at,target_type,target_id,action_type,actor_id,authorization_decision_id,correlation_id
    from security_control_action_evidence where tenant_id=p_tenant and action_id=p_action
  on conflict do nothing;
  select case_id into case_key from security_control_action_evidence where tenant_id=p_tenant and action_id=p_action;
  if case_key is not null then perform phase12_6_refresh_incident_fact(p_tenant,case_key); end if;
end $$;

-- Bounded SKIP LOCKED worker primitive. Multiple workers may safely process one Tenant without double counting.
create or replace function phase12_6_project_pending(p_tenant varchar,p_limit integer default 250) returns integer language plpgsql as $$
declare r record; processed integer:=0;
begin
  for r in
    select tenant_id,event_id,source_type,source_id,event_type,occurred_at from enterprise_analytics_projection_events
     where tenant_id=p_tenant and projection_status='PENDING'
     order by occurred_at,event_id
     for update skip locked limit greatest(1,least(coalesce(p_limit,250),1000))
  loop
    begin
      if r.source_type='TASK' then perform phase12_6_rebuild_task_fact(r.tenant_id,r.source_id);
      elsif r.source_type='TASK_LINEAGE' then perform phase12_6_rebuild_lineage_fact(r.tenant_id,r.source_id);
      elsif r.source_type='SECURITY_INCIDENT' then perform phase12_6_refresh_incident_fact(r.tenant_id,r.source_id);
      elsif r.source_type='SECURITY_CONTROL' then perform phase12_6_rebuild_security_control_fact(r.tenant_id,r.source_id);
      end if;
      update enterprise_analytics_projection_events set projection_status='PROJECTED',projected_at=now() where tenant_id=r.tenant_id and event_id=r.event_id;
      perform phase12_6_touch_checkpoint(r.tenant_id,r.event_id,r.occurred_at); processed:=processed+1;
    exception when others then
      update enterprise_analytics_projection_events set projection_status='FAILED' where tenant_id=r.tenant_id and event_id=r.event_id;
      insert into enterprise_analytics_projection_checkpoints(tenant_id,projection_name,failure_count,rebuild_required,updated_at)
      values(r.tenant_id,'ENTERPRISE_ANALYTICS',1,true,now())
      on conflict(tenant_id,projection_name) do update set failure_count=enterprise_analytics_projection_checkpoints.failure_count+1,rebuild_required=true,updated_at=now();
    end;
  end loop;
  return processed;
end $$;

-- Historical backfill is deterministic and idempotent. All source authorities are FORCE-RLS,
-- so migration backfill is deliberately tenant-by-tenant instead of performing an INSTANCE cross-tenant scan.
do $$
declare tenant_value varchar(64); r record; previous_tenant text := current_setting('app.current_tenant_id',true);
begin
  for tenant_value in select tenant_id from tenants order by tenant_id loop
    perform set_config('app.current_tenant_id',tenant_value,true);
    perform set_config('app.tenant_id',tenant_value,true);

    insert into analytics_workload_fact(tenant_id,task_id,root_task_id,parent_task_id,occurred_at,terminal_at,updated_at,origin_principal_type,origin_principal_id,actor_principal_type,actor_principal_id,
     credential_id,oauth_client_id,source_system,origin_department_id,origin_group_id,origin_department_version_id,origin_group_version_id,owner_department_id,owner_group_id,assigned_agent_id,
     task_type,initial_priority,initial_severity,current_priority,current_severity,status,failure_domain,failure_code,reassignment_count,dispatch_attempt_count,duration_ms)
    select t.tenant_id,t.task_id,t.root_task_id,t.parent_task_id,t.created_at,t.terminal_at,t.updated_at,t.origin_principal_type,t.origin_principal_id,t.actor_principal_type,t.actor_principal_id,
     t.origin_credential_id,t.origin_oauth_client_id,coalesce(t.origin_workload_source_system,t.source_system),t.origin_department_id,t.origin_group_id,
     phase12_6_dimension_version(t.tenant_id,'DEPARTMENT',t.origin_department_id,t.created_at),phase12_6_dimension_version(t.tenant_id,'GROUP',t.origin_group_id,t.created_at),
     t.owner_department_id,t.owner_group_id,(select a.agent_id from task_assignments a where a.task_id=t.task_id order by a.created_at desc limit 1),t.task_type,coalesce(t.initial_priority,t.priority),coalesce(t.initial_severity,t.severity),t.priority,t.severity,t.status,coalesce(t.failure_domain,'NONE'),t.failure_code,
     coalesce(t.reassignment_count,0),coalesce(t.dispatch_attempt_count,0),case when t.terminal_at is null then null else greatest(0,(extract(epoch from(t.terminal_at-t.created_at))*1000)::bigint) end
      from tasks t where t.tenant_id=tenant_value
    on conflict(tenant_id,task_id) do nothing;

    insert into analytics_agent_execution_fact(tenant_id,evidence_id,task_id,root_task_id,parent_task_id,occurred_at,event_type,origin_principal_type,origin_principal_id,actor_principal_type,actor_principal_id,
     executor_agent_id,department_id,group_id,department_version_id,group_version_id,credential_id,oauth_client_id,source_system,failure_domain,failure_code)
    select e.tenant_id,e.evidence_id,e.task_id,e.root_task_id,e.parent_task_id,e.occurred_at,e.event_type,e.origin_principal_type,e.origin_principal_id,e.actor_principal_type,e.actor_principal_id,
     e.executor_agent_id,e.department_id,e.group_id,phase12_6_dimension_version(e.tenant_id,'DEPARTMENT',e.department_id,e.occurred_at),phase12_6_dimension_version(e.tenant_id,'GROUP',e.group_id,e.occurred_at),
     e.credential_id,e.oauth_client_id,e.source_system,e.failure_domain,e.failure_code from task_lineage_evidence e where e.tenant_id=tenant_value
    on conflict do nothing;

    insert into analytics_security_control_fact(tenant_id,action_id,case_id,control_id,occurred_at,target_type,target_id,action_type,actor_id,authorization_decision_id,correlation_id)
    select tenant_id,action_id,case_id,control_id,occurred_at,target_type,target_id,action_type,actor_id,authorization_decision_id,correlation_id
      from security_control_action_evidence where tenant_id=tenant_value
    on conflict do nothing;

    for r in select tenant_id,case_id from security_incident_cases where tenant_id=tenant_value loop
      perform phase12_6_refresh_incident_fact(r.tenant_id,r.case_id);
    end loop;

    insert into enterprise_analytics_projection_checkpoints(tenant_id,projection_name,last_projected_at,projected_event_count,failure_count,rebuild_required,updated_at)
    values(tenant_value,'ENTERPRISE_ANALYTICS',now(),0,0,false,now())
    on conflict(tenant_id,projection_name) do nothing;
  end loop;
  perform set_config('app.current_tenant_id',coalesce(nullif(previous_tenant,''),'INSTANCE'),true);
  perform set_config('app.tenant_id','INSTANCE',true);
exception when others then
  perform set_config('app.current_tenant_id',coalesce(nullif(previous_tenant,''),'INSTANCE'),true);
  perform set_config('app.tenant_id','INSTANCE',true);
  raise;
end $$;

-- Tenant isolation applies to every analytics table. These projections are not a cross-tenant reporting bypass.
do $$
declare table_name text;
begin
  foreach table_name in array array[
    'analytics_dim_department_history','analytics_dim_group_history',
    'analytics_workload_fact','analytics_agent_execution_fact','analytics_security_incident_fact','analytics_security_control_fact',
    'enterprise_analytics_projection_events','enterprise_analytics_projection_checkpoints'
  ] loop
    execute format('alter table %I enable row level security',table_name);
    execute format('alter table %I force row level security',table_name);
    execute format('drop policy if exists tenant_isolation on %I',table_name);
    execute format('create policy tenant_isolation on %I using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id())',table_name);
  end loop;
end $$;

-- Phase 12.5 originally introduced a second app.tenant_id key for these direct-JDBC tables.
-- Converge them onto the canonical IAM tenant context before application runtime starts.
do $$
declare table_name text;
begin
  foreach table_name in array array['security_incident_cases','security_resource_controls','security_control_action_evidence'] loop
    execute format('drop policy if exists tenant_isolation on %I',table_name);
    execute format('create policy tenant_isolation on %I using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id())',table_name);
  end loop;
end $$;

-- Tenant dimension is also written by instance-level Tenant administration. INSTANCE may maintain the dimension, tenant callers remain isolated.
alter table analytics_dim_tenant_history enable row level security;
alter table analytics_dim_tenant_history force row level security;
drop policy if exists tenant_isolation on analytics_dim_tenant_history;
create policy tenant_isolation on analytics_dim_tenant_history
  using (iam_current_tenant_id()='INSTANCE' or tenant_id=iam_current_tenant_id())
  with check (iam_current_tenant_id()='INSTANCE' or tenant_id=iam_current_tenant_id());

comment on table analytics_workload_fact is 'Phase 12.6 rebuildable Task analytics read model. Never IAM/RBAC or Task lifecycle authority.';
comment on table analytics_agent_execution_fact is 'Phase 12.6 rebuildable immutable-lineage analytics read model.';
comment on table analytics_security_incident_fact is 'Phase 12.6 rebuildable Security Incident analytics read model.';
comment on table enterprise_analytics_projection_events is 'Projection delivery ledger/checkpoint source for later external analytics sinks. Replaying it must not alter business authority.';
comment on table analytics_dim_department_history is 'SCD2 Department labels from Phase 12.6 forward. Initial 1970 row represents the best-known pre-Phase-12.6 label, not invented rename history.';
