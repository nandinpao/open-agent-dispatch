-- V41 C3R3-B Authority Contract v2 rollout + Wave-1 runtime cutover certification foundation.
-- This migration creates durable certification evidence only. Runtime cutover remains operator-driven.

create table if not exists runtime_config_cutover_wave_certifications (
  certification_id varchar(64) primary key,
  wave_id varchar(64) not null references runtime_config_cutover_waves(wave_id),
  status varchar(16) not null,
  authority_contract_version integer not null,
  config_set_count integer not null,
  runtime_key_count integer not null,
  required_node_count integer not null,
  converged_node_count integer not null,
  evidence_json jsonb not null,
  certified_by varchar(256) not null,
  reason text not null,
  certified_at timestamptz not null,
  constraint ck_runtime_config_cutover_wave_cert_status check (status in ('PASS')),
  constraint ck_runtime_config_cutover_wave_cert_contract check (authority_contract_version >= 2),
  constraint ck_runtime_config_cutover_wave_cert_counts check (
    config_set_count >= 1 and runtime_key_count >= 1 and required_node_count >= 1 and converged_node_count = required_node_count)
);

create index if not exists ix_runtime_config_cutover_wave_cert_latest
  on runtime_config_cutover_wave_certifications(wave_id,certified_at desc);

comment on table runtime_config_cutover_wave_certifications is
  'Durable runtime convergence evidence for finalized Single Authority cutover waves. A PASS is not full product runtime certification.';

do $$
declare
  w1_sets integer;
  w1_keys integer;
  runtime_targets integer;
  migrated integer;
begin
  select count(*),coalesce(sum(expected_runtime_key_count),0)
    into w1_sets,w1_keys
    from runtime_config_cutover_wave_members where wave_id='C3R3-W1';
  if w1_sets <> 6 then raise exception 'C3R3B_W1_CONFIG_SET_COUNT_INVALID expected=6 actual=%',w1_sets; end if;
  if w1_keys <> 12 then raise exception 'C3R3B_W1_RUNTIME_KEY_COUNT_INVALID expected=12 actual=%',w1_keys; end if;

  select count(*) into runtime_targets from runtime_config_definitions
   where authority_class='RUNTIME_TUNABLE' and migration_authorized=true;
  if runtime_targets <> 296 then raise exception 'C3R3B_RUNTIME_TARGET_BASELINE_INVALID expected=296 actual=%',runtime_targets; end if;

  -- Flyway must never perform the runtime cutover. PREPARE/FINALIZE require live node capability and fingerprint ACK evidence.
  select count(*) into migrated from runtime_config_inventory_governance
   where status in ('MIGRATED','LEGACY_RETIRED')
     and configuration_key in (select definition_key from runtime_config_definitions where authority_class='RUNTIME_TUNABLE');
  if migrated <> 0 then raise exception 'C3R3B_PRE_DEPLOY_RUNTIME_CUTOVER_ALREADY_PRESENT count=%',migrated; end if;
end $$;
