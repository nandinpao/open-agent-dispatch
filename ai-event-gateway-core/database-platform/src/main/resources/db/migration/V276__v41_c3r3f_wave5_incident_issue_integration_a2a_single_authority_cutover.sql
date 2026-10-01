-- V41 C3R3-F Wave-5 Incident / Issue / Integration / A2A Single Authority Cutover foundation.
-- W5 is HIGH risk and reuses the durable safety-attestation store introduced by V275.
-- This migration does NOT perform runtime cutover; W5 remains live/operator driven after W4 durable certification PASS.

comment on table runtime_config_cutover_wave_safety_attestations is
  'Durable expiring pre-cutover safety evidence for high-risk Single Authority waves. W4 Task/Dispatch and W5 External Integration/A2A require fresh PASS evidence before PREPARE and through FINALIZE.';

do $$
declare
  w5_sets integer;
  w5_keys integer;
  runtime_targets integer;
  w5_migrated integer;
begin
  select count(*),coalesce(sum(expected_runtime_key_count),0)
    into w5_sets,w5_keys
    from runtime_config_cutover_wave_members where wave_id='C3R3-W5';
  if w5_sets <> 4 then raise exception 'C3R3F_W5_CONFIG_SET_COUNT_INVALID expected=4 actual=%',w5_sets; end if;
  if w5_keys <> 40 then raise exception 'C3R3F_W5_RUNTIME_KEY_COUNT_INVALID expected=40 actual=%',w5_keys; end if;

  select count(*) into runtime_targets from runtime_config_definitions
   where authority_class='RUNTIME_TUNABLE' and migration_authorized=true;
  if runtime_targets <> 296 then raise exception 'C3R3F_RUNTIME_TARGET_BASELINE_INVALID expected=296 actual=%',runtime_targets; end if;

  -- Flyway is intentionally not allowed to mutate W5 governance. Live external-integration/A2A
  -- safety attestation, PREPARE, Authority Contract v2 ACK convergence, FINALIZE and certification are required.
  select count(*) into w5_migrated
    from runtime_config_inventory_governance g
    join runtime_config_definitions d on d.definition_key=g.configuration_key
    join runtime_config_cutover_wave_members m on m.set_key=d.config_set_key and m.wave_id='C3R3-W5'
   where g.status in ('MIGRATED','LEGACY_RETIRED');
  if w5_migrated <> 0 then raise exception 'C3R3F_PRE_DEPLOY_W5_RUNTIME_CUTOVER_ALREADY_PRESENT count=%',w5_migrated; end if;
end $$;
