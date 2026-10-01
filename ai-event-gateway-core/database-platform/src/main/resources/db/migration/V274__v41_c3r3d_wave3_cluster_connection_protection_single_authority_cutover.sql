-- V41 C3R3-D Wave-3 Cluster / Connection Protection Single Authority Cutover foundation.
-- This migration does NOT perform runtime cutover. W3 remains live/operator driven and is blocked
-- until W2 has a durable PASS convergence certification.

do $$
declare
  w3_sets integer;
  w3_keys integer;
  runtime_targets integer;
  w3_migrated integer;
begin
  select count(*),coalesce(sum(expected_runtime_key_count),0)
    into w3_sets,w3_keys
    from runtime_config_cutover_wave_members where wave_id='C3R3-W3';
  if w3_sets <> 3 then raise exception 'C3R3D_W3_CONFIG_SET_COUNT_INVALID expected=3 actual=%',w3_sets; end if;
  if w3_keys <> 20 then raise exception 'C3R3D_W3_RUNTIME_KEY_COUNT_INVALID expected=20 actual=%',w3_keys; end if;

  select count(*) into runtime_targets from runtime_config_definitions
   where authority_class='RUNTIME_TUNABLE' and migration_authorized=true;
  if runtime_targets <> 296 then raise exception 'C3R3D_RUNTIME_TARGET_BASELINE_INVALID expected=296 actual=%',runtime_targets; end if;

  -- Flyway is intentionally not allowed to mutate W3 governance. Live PREPARE/ACK/FINALIZE is required.
  select count(*) into w3_migrated
    from runtime_config_inventory_governance g
    join runtime_config_definitions d on d.definition_key=g.configuration_key
    join runtime_config_cutover_wave_members m on m.set_key=d.config_set_key and m.wave_id='C3R3-W3'
   where g.status in ('MIGRATED','LEGACY_RETIRED');
  if w3_migrated <> 0 then raise exception 'C3R3D_PRE_DEPLOY_W3_RUNTIME_CUTOVER_ALREADY_PRESENT count=%',w3_migrated; end if;
end $$;
