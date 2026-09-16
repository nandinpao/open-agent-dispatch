-- v24 Phase 2: distinguish the authenticated creator from the operational purpose of a workload.
alter table tasks add column if not exists workload_purpose varchar(32) not null default 'PRODUCTION';
alter table tasks add column if not exists synthetic boolean not null default false;
alter table tasks drop constraint if exists ck_tasks_workload_purpose;
alter table tasks add constraint ck_tasks_workload_purpose check (workload_purpose in ('PRODUCTION','TEST','CERTIFICATION','SIMULATION'));
create index if not exists idx_tasks_workload_purpose on tasks(tenant_id,workload_purpose,synthetic,created_at desc,task_id);

create or replace function v24_phase2_protect_workload_purpose() returns trigger language plpgsql as $$
begin
  if old.workload_purpose is distinct from new.workload_purpose or old.synthetic is distinct from new.synthetic then
    raise exception 'TASK_IMMUTABLE_WORKLOAD_PURPOSE';
  end if;
  return new;
end $$;
drop trigger if exists trg_v24_phase2_task_workload_purpose_immutable on tasks;
create trigger trg_v24_phase2_task_workload_purpose_immutable before update of workload_purpose,synthetic on tasks
for each row execute function v24_phase2_protect_workload_purpose();
comment on column tasks.workload_purpose is 'Immutable creation-time operational purpose; independent of authenticated principal type.';
comment on column tasks.synthetic is 'True for generated/test/certification/simulation workloads; never an authorization authority.';
