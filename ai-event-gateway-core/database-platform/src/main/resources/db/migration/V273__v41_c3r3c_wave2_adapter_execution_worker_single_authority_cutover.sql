-- V41 C3R3-C Wave-2 Adapter Execution / Worker Single Authority Cutover foundation.
-- This migration does NOT perform runtime cutover. W2 remains live/operator driven and is blocked
-- until W1 has a durable PASS convergence certification.

do $$
declare
  w2_sets integer;
  w2_keys integer;
  runtime_targets integer;
  w2_migrated integer;
begin
  select count(*),coalesce(sum(expected_runtime_key_count),0)
    into w2_sets,w2_keys
    from runtime_config_cutover_wave_members where wave_id='C3R3-W2';
  if w2_sets <> 3 then raise exception 'C3R3C_W2_CONFIG_SET_COUNT_INVALID expected=3 actual=%',w2_sets; end if;
  if w2_keys <> 42 then raise exception 'C3R3C_W2_RUNTIME_KEY_COUNT_INVALID expected=42 actual=%',w2_keys; end if;

  select count(*) into runtime_targets from runtime_config_definitions
   where authority_class='RUNTIME_TUNABLE' and migration_authorized=true;
  if runtime_targets <> 296 then raise exception 'C3R3C_RUNTIME_TARGET_BASELINE_INVALID expected=296 actual=%',runtime_targets; end if;

  -- Flyway is intentionally not allowed to mutate W2 governance. Live PREPARE/ACK/FINALIZE is required.
  select count(*) into w2_migrated
    from runtime_config_inventory_governance g
    join runtime_config_definitions d on d.definition_key=g.configuration_key
    join runtime_config_cutover_wave_members m on m.set_key=d.config_set_key and m.wave_id='C3R3-W2'
   where g.status in ('MIGRATED','LEGACY_RETIRED');
  if w2_migrated <> 0 then raise exception 'C3R3C_PRE_DEPLOY_W2_RUNTIME_CUTOVER_ALREADY_PRESENT count=%',w2_migrated; end if;
end $$;
