-- Phase 6C-2: certify Task List/Search as a governed read-only Shadow/Canary entry point.
-- Flyway executes each versioned migration in its own transaction. V101's transaction-local
-- RLS context is therefore not inherited by V102. Establish INSTANCE context before any
-- statement touches the FORCE-RLS Wave 0 control-plane tables.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','phase6c2-migration',true);

create index if not exists idx_tasks_tenant_created_task
    on tasks(tenant_id, created_at desc, task_id desc);
create index if not exists idx_tasks_tenant_incident_created_task
    on tasks(tenant_id, incident_id, created_at desc, task_id desc);

-- Independent Target source. security_invoker preserves FORCE-RLS and transaction-local Tenant context.
create or replace view enforcement_task_list_target_read_v1 with (security_invoker=true) as
select * from tasks;

alter table enforcement_wave0_read_observations
    drop constraint if exists enforcement_wave0_read_observations_mismatch_category_check;
alter table enforcement_wave0_read_observations
    add constraint enforcement_wave0_read_observations_mismatch_category_check
    check(mismatch_category in(
        'MATCH','PAYLOAD_MISMATCH','ORDER_MISMATCH','TARGET_SCOPE_EXPANSION',
        'LEGACY_ERROR','TARGET_ERROR','BOTH_ERROR','UNSUPPORTED_MODE','NOT_COMPARED'));

-- V101 intentionally blocked this entry point. V102 enables policy evaluation but leaves the Gate DISABLED.
update enforcement_wave0_read_policies
set enabled=true,
    compare_legacy_canary=true,
    minimum_samples=1000,
    maximum_mismatch_basis_points=10,
    maximum_target_error_basis_points=10,
    maximum_target_p95_ms=1500,
    observation_window_seconds=3600,
    auto_pause=true,
    version=version+1,
    updated_at=now(),
    updated_by='phase6c2-migration'
where entry_point_id='TASK_LIST_SEARCH';

create or replace function phase6c1_validate_wave0_gate_transition() returns trigger language plpgsql as $$
begin
 if TG_OP='UPDATE' and new.version<>old.version+1 then
   raise exception 'PHASE6C1_GATE_VERSION_INVALID' using errcode='23514';
 end if;
 if TG_OP='UPDATE' and old.state<>new.state then
   if not (
     (old.state='DISABLED' and new.state='OBSERVING') or
     (old.state='OBSERVING' and new.state in('OPEN','PAUSED','BLOCKED')) or
     (old.state='OPEN' and new.state in('OBSERVING','PAUSED','BLOCKED')) or
     (old.state='PAUSED' and new.state='OBSERVING') or
     (old.state='BLOCKED' and new.state in('OBSERVING','PAUSED'))
   ) then
     raise exception 'PHASE6C1_GATE_TRANSITION_INVALID:%->%',old.state,new.state using errcode='23514';
   end if;
 end if;
 return new;
end $$;

create table if not exists enforcement_task_read_certification_runs(
 certification_id uuid primary key default gen_random_uuid(),
 tenant_id varchar(128) not null,
 authority_revision bigint not null,
 route_mode varchar(24) not null check(route_mode in('SHADOW','TARGET_CANARY')),
 sample_count bigint not null check(sample_count>=0),
 deterministic_order_status varchar(16) not null check(deterministic_order_status in('PASS','FAIL','BLOCKED')),
 cursor_pagination_status varchar(16) not null check(cursor_pagination_status in('PASS','FAIL','BLOCKED')),
 n_plus_one_status varchar(16) not null check(n_plus_one_status in('PASS','FAIL','BLOCKED')),
 force_rls_status varchar(16) not null check(force_rls_status in('PASS','FAIL','BLOCKED')),
 sensitive_field_masking_status varchar(16) not null check(sensitive_field_masking_status in('PASS','FAIL','BLOCKED')),
 fallback_pause_status varchar(16) not null check(fallback_pause_status in('PASS','FAIL','BLOCKED')),
 load_test_status varchar(16) not null check(load_test_status in('PASS','FAIL','BLOCKED')),
 overall_status varchar(16) not null check(overall_status in('PASS','FAIL','BLOCKED')),
 evidence jsonb not null default '{}'::jsonb,
 idempotency_key varchar(180) not null,
 request_hash char(64) not null,
 certified_by varchar(128) not null,
 correlation_id varchar(128) not null,
 certified_at timestamptz not null default now()
);
create unique index if not exists uq_task_read_certification_idempotency
    on enforcement_task_read_certification_runs(tenant_id, idempotency_key);
create index if not exists idx_task_read_certification_tenant_time
    on enforcement_task_read_certification_runs(tenant_id, certified_at desc);

create trigger trg_phase6c2_task_certification_immutable
before update or delete on enforcement_task_read_certification_runs
for each row execute function phase6c1_reject_wave0_immutable_mutation();

alter table enforcement_task_read_certification_runs enable row level security;
alter table enforcement_task_read_certification_runs force row level security;
create policy phase6c2_task_certification_instance_scope
on enforcement_task_read_certification_runs
using(iam_current_tenant_id()='INSTANCE')
with check(iam_current_tenant_id()='INSTANCE');

comment on view enforcement_task_list_target_read_v1 is
'Phase 6C-2 independent Task List/Search Target base view. Scope predicates, deny precedence, keyset cursor and pagination remain compiled into the Target query before LIMIT.';
comment on table enforcement_task_read_certification_runs is
'Append-only Phase 6C-2 operational certification evidence. A PASS is required before expanding Task read Canary beyond the approved cohort.';
