-- Phase 1A-8 routing decision tenant evidence repair.
--
-- Routing decisions are tenant-owned Task lifecycle evidence. The application must
-- persist the Task tenant explicitly, while this trigger remains a database-level
-- compatibility and mismatch guard for historical/alternate writers.

create or replace function phase1a8_set_routing_decision_tenant()
returns trigger
language plpgsql
as $$
declare
  v_task_tenant_id varchar(64);
begin
  if tg_op = 'UPDATE' and new.task_id is distinct from old.task_id then
    raise exception using
      errcode = '23514',
      message = 'ROUTING_DECISION_TASK_REASSIGNMENT_DENIED',
      detail = format('Routing decision %L cannot move from Task %L to Task %L.', new.decision_id, old.task_id, new.task_id);
  end if;

  if new.task_id is null or btrim(new.task_id) = '' then
    raise exception using
      errcode = '23514',
      message = 'ROUTING_DECISION_TASK_REQUIRED';
  end if;

  select t.tenant_id
    into v_task_tenant_id
    from tasks t
   where t.task_id = new.task_id;

  if v_task_tenant_id is null then
    raise exception using
      errcode = '23503',
      message = 'ROUTING_DECISION_TASK_NOT_FOUND',
      detail = format('Routing decision %L references missing Task %L.', new.decision_id, new.task_id);
  end if;

  if new.tenant_id is not null
     and btrim(new.tenant_id) <> ''
     and new.tenant_id <> v_task_tenant_id then
    raise exception using
      errcode = '23514',
      message = 'ROUTING_DECISION_TENANT_MISMATCH',
      detail = format('Routing decision tenant %L differs from Task tenant %L.', new.tenant_id, v_task_tenant_id);
  end if;

  new.tenant_id := v_task_tenant_id;
  return new;
end $$;

drop trigger if exists trg_00_routing_decisions_phase1a8_tenant on routing_decisions;
create trigger trg_00_routing_decisions_phase1a8_tenant
before insert or update of task_id, tenant_id on routing_decisions
for each row execute function phase1a8_set_routing_decision_tenant();
