-- V41 C3R3-E Wave-4 Task / Dispatch / Task Authority Single Authority Cutover foundation.
-- High-risk W4 adds a durable, expiring pre-cutover safety attestation. This migration does NOT
-- perform runtime cutover; W4 remains live/operator driven after W3 durable certification PASS.

create table if not exists runtime_config_cutover_wave_safety_attestations (
  attestation_id varchar(64) primary key,
  wave_id varchar(64) not null references runtime_config_cutover_waves(wave_id),
  status varchar(16) not null,
  evidence_json jsonb not null,
  attested_by varchar(256) not null,
  reason text not null,
  captured_at timestamptz not null,
  expires_at timestamptz not null,
  constraint ck_runtime_config_cutover_wave_safety_status check (status in ('PASS','FAIL')),
  constraint ck_runtime_config_cutover_wave_safety_window check (expires_at > captured_at)
);

create index if not exists ix_runtime_config_cutover_wave_safety_latest
  on runtime_config_cutover_wave_safety_attestations(wave_id,captured_at desc);

comment on table runtime_config_cutover_wave_safety_attestations is
  'Durable expiring pre-cutover safety evidence for high-risk Single Authority waves. W4 PASS evidence is required before PREPARE and through FINALIZE.';

do $$
declare
  w4_sets integer;
  w4_keys integer;
  runtime_targets integer;
  w4_migrated integer;
begin
  select count(*),coalesce(sum(expected_runtime_key_count),0)
    into w4_sets,w4_keys
    from runtime_config_cutover_wave_members where wave_id='C3R3-W4';
  if w4_sets <> 3 then raise exception 'C3R3E_W4_CONFIG_SET_COUNT_INVALID expected=3 actual=%',w4_sets; end if;
  if w4_keys <> 77 then raise exception 'C3R3E_W4_RUNTIME_KEY_COUNT_INVALID expected=77 actual=%',w4_keys; end if;

  select count(*) into runtime_targets from runtime_config_definitions
   where authority_class='RUNTIME_TUNABLE' and migration_authorized=true;
  if runtime_targets <> 296 then raise exception 'C3R3E_RUNTIME_TARGET_BASELINE_INVALID expected=296 actual=%',runtime_targets; end if;

  -- Flyway is intentionally not allowed to mutate W4 governance. Live safety attestation,
  -- PREPARE, Authority Contract v2 ACK convergence, FINALIZE and certification are required.
  select count(*) into w4_migrated
    from runtime_config_inventory_governance g
    join runtime_config_definitions d on d.definition_key=g.configuration_key
    join runtime_config_cutover_wave_members m on m.set_key=d.config_set_key and m.wave_id='C3R3-W4'
   where g.status in ('MIGRATED','LEGACY_RETIRED');
  if w4_migrated <> 0 then raise exception 'C3R3E_PRE_DEPLOY_W4_RUNTIME_CUTOVER_ALREADY_PRESENT count=%',w4_migrated; end if;
end $$;
