-- Phase 12.7: Enterprise Dashboard & Large-scale Performance Engineering.
-- Analytics remains a rebuildable read model. These rollups MUST NOT become IAM/RBAC/Task authority.

select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','phase12-7-dashboard-performance',true);

-- Hourly workload rollups keep common Dashboard trend queries bounded as analytics_workload_fact grows.
create table if not exists analytics_workload_hourly_rollup (
  tenant_id varchar(64) not null,
  bucket_start timestamptz not null,
  department_id varchar(128) not null default '',
  group_id varchar(128) not null default '',
  origin_principal_type varchar(32) not null default 'UNKNOWN',
  failure_domain varchar(32) not null default 'NONE',
  source_system varchar(160) not null default '',
  task_count bigint not null default 0,
  failed_count bigint not null default 0,
  critical_count bigint not null default 0,
  duration_sample_count bigint not null default 0,
  total_duration_ms numeric(30,0) not null default 0,
  refreshed_at timestamptz not null default now(),
  primary key(tenant_id,bucket_start,department_id,group_id,origin_principal_type,failure_domain,source_system)
);
create index if not exists idx_phase12_7_rollup_tenant_time on analytics_workload_hourly_rollup(tenant_id,bucket_start desc);
create index if not exists idx_phase12_7_rollup_org_time on analytics_workload_hourly_rollup(tenant_id,department_id,group_id,bucket_start desc);
create index if not exists idx_phase12_7_rollup_origin_time on analytics_workload_hourly_rollup(tenant_id,origin_principal_type,bucket_start desc);
create index if not exists idx_phase12_7_rollup_failure_time on analytics_workload_hourly_rollup(tenant_id,failure_domain,bucket_start desc) where failure_domain<>'NONE';

-- Dirty buckets decouple expensive aggregation from OLTP/fact upserts and permit multi-worker refresh with SKIP LOCKED.
create table if not exists analytics_workload_rollup_dirty (
  tenant_id varchar(64) not null,
  bucket_start timestamptz not null,
  marked_at timestamptz not null default now(),
  attempt_count integer not null default 0,
  last_error varchar(500),
  primary key(tenant_id,bucket_start)
);
create index if not exists idx_phase12_7_rollup_dirty on analytics_workload_rollup_dirty(tenant_id,marked_at,bucket_start);

-- Seed only bounded recent hours without scanning the Task fact table during migration. The async worker rebuilds them gradually.
insert into analytics_workload_rollup_dirty(tenant_id,bucket_start,marked_at)
select t.tenant_id,g,now()
  from tenants t
  cross join lateral generate_series(date_trunc('hour',now()-interval '90 days'),date_trunc('hour',now()),interval '1 hour') g
 where t.status='ACTIVE'
on conflict(tenant_id,bucket_start) do nothing;

alter table analytics_workload_hourly_rollup enable row level security;
alter table analytics_workload_hourly_rollup force row level security;
drop policy if exists analytics_workload_hourly_rollup_tenant_isolation on analytics_workload_hourly_rollup;
create policy analytics_workload_hourly_rollup_tenant_isolation on analytics_workload_hourly_rollup
  using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());

alter table analytics_workload_rollup_dirty enable row level security;
alter table analytics_workload_rollup_dirty force row level security;
drop policy if exists analytics_workload_rollup_dirty_tenant_isolation on analytics_workload_rollup_dirty;
create policy analytics_workload_rollup_dirty_tenant_isolation on analytics_workload_rollup_dirty
  using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());

-- Covering/BRIN indexes support high-cardinality drill-down and keyset investigation without OFFSET scans.
create index if not exists idx_phase12_7_workload_dashboard_cover
  on analytics_workload_fact(tenant_id,occurred_at desc,task_id)
  include(status,failure_domain,initial_priority,initial_severity,origin_principal_type,origin_department_id,origin_group_id,assigned_agent_id,credential_id,source_system);
create index if not exists idx_phase12_7_workload_agent_rank
  on analytics_workload_fact(tenant_id,assigned_agent_id,occurred_at desc)
  include(status,failure_domain,initial_priority,initial_severity) where assigned_agent_id is not null;
create index if not exists idx_phase12_7_workload_source_rank
  on analytics_workload_fact(tenant_id,source_system,occurred_at desc)
  include(status,failure_domain,initial_priority,initial_severity) where source_system is not null and source_system<>'';
create index if not exists idx_phase12_7_workload_credential_rank
  on analytics_workload_fact(tenant_id,credential_id,occurred_at desc)
  include(status,failure_domain,initial_priority,initial_severity) where credential_id is not null;
create index if not exists idx_phase12_7_workload_time_brin on analytics_workload_fact using brin(occurred_at) with(pages_per_range=64);
create index if not exists idx_phase12_7_agent_execution_time_brin on analytics_agent_execution_fact using brin(occurred_at) with(pages_per_range=64);
create index if not exists idx_phase12_7_security_incident_time_brin on analytics_security_incident_fact using brin(opened_at) with(pages_per_range=64);

create or replace function phase12_7_mark_workload_rollup_dirty() returns trigger language plpgsql as $$
begin
  insert into analytics_workload_rollup_dirty(tenant_id,bucket_start,marked_at)
  values(new.tenant_id,date_trunc('hour',new.occurred_at),now())
  on conflict(tenant_id,bucket_start) do update set marked_at=least(analytics_workload_rollup_dirty.marked_at,excluded.marked_at);
  return new;
end $$;
drop trigger if exists trg_phase12_7_mark_workload_rollup_dirty on analytics_workload_fact;
create trigger trg_phase12_7_mark_workload_rollup_dirty after insert or update on analytics_workload_fact
for each row execute function phase12_7_mark_workload_rollup_dirty();

create or replace function phase12_7_rebuild_workload_hour(p_tenant varchar,p_bucket timestamptz) returns void language plpgsql as $$
begin
  delete from analytics_workload_hourly_rollup where tenant_id=p_tenant and bucket_start=p_bucket;
  insert into analytics_workload_hourly_rollup(
    tenant_id,bucket_start,department_id,group_id,origin_principal_type,failure_domain,source_system,
    task_count,failed_count,critical_count,duration_sample_count,total_duration_ms,refreshed_at)
  select tenant_id,date_trunc('hour',occurred_at),coalesce(origin_department_id,''),coalesce(origin_group_id,''),
         coalesce(origin_principal_type,'UNKNOWN'),coalesce(failure_domain,'NONE'),coalesce(source_system,''),
         count(*),
         count(*) filter(where status in('FAILED','DEAD_LETTER','ESCALATED','TIMED_OUT','EXPIRED','BLOCKED','RETRY_WAIT') or coalesce(failure_domain,'NONE')<>'NONE'),
         count(*) filter(where initial_priority in('P0','P1','CRITICAL','URGENT') or initial_severity='CRITICAL'),
         count(duration_ms),coalesce(sum(duration_ms),0),now()
    from analytics_workload_fact
   where tenant_id=p_tenant and occurred_at>=p_bucket and occurred_at<p_bucket+interval '1 hour'
   group by tenant_id,date_trunc('hour',occurred_at),coalesce(origin_department_id,''),coalesce(origin_group_id,''),
            coalesce(origin_principal_type,'UNKNOWN'),coalesce(failure_domain,'NONE'),coalesce(source_system,'');
end $$;

create or replace function phase12_7_refresh_dirty_rollups(p_tenant varchar,p_limit integer default 48) returns integer language plpgsql as $$
declare r record; processed integer:=0;
begin
  for r in
    select tenant_id,bucket_start from analytics_workload_rollup_dirty
     where tenant_id=p_tenant order by marked_at,bucket_start
     for update skip locked limit greatest(1,least(coalesce(p_limit,48),500))
  loop
    begin
      perform phase12_7_rebuild_workload_hour(r.tenant_id,r.bucket_start);
      delete from analytics_workload_rollup_dirty where tenant_id=r.tenant_id and bucket_start=r.bucket_start;
      processed:=processed+1;
    exception when others then
      update analytics_workload_rollup_dirty set attempt_count=attempt_count+1,last_error=left(sqlerrm,500),marked_at=now()
       where tenant_id=r.tenant_id and bucket_start=r.bucket_start;
    end;
  end loop;
  return processed;
end $$;

-- Controlled historical rebuild primitive. It marks hours dirty instead of doing an unbounded migration-time aggregate.
create or replace function phase12_7_enqueue_rollup_rebuild(p_tenant varchar,p_from timestamptz,p_to timestamptz) returns bigint language plpgsql as $$
declare inserted bigint;
begin
  if p_from is null or p_to is null or p_from>=p_to then raise exception 'ANALYTICS_ROLLUP_INVALID_WINDOW'; end if;
  if p_to-p_from>interval '400 days' then raise exception 'ANALYTICS_ROLLUP_WINDOW_TOO_LARGE'; end if;
  insert into analytics_workload_rollup_dirty(tenant_id,bucket_start,marked_at)
  select p_tenant,g,now() from generate_series(date_trunc('hour',p_from),date_trunc('hour',p_to-interval '1 microsecond'),interval '1 hour') g
  on conflict(tenant_id,bucket_start) do update set marked_at=least(analytics_workload_rollup_dirty.marked_at,excluded.marked_at);
  get diagnostics inserted=row_count;
  return inserted;
end $$;

comment on table analytics_workload_hourly_rollup is 'Phase 12.7 rebuildable hourly Dashboard rollup. Never an authorization source of truth.';
comment on table analytics_workload_rollup_dirty is 'Phase 12.7 bounded asynchronous dirty-hour queue for Dashboard rollup refresh.';
