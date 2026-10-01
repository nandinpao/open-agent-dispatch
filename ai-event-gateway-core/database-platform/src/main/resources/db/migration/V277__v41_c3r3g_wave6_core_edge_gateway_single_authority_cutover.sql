-- V41 C3R3-G Wave-6 Core / Edge / Gateway Single Authority Cutover foundation.
-- W6 is CRITICAL risk and requires W5 durable certification PASS plus a fresh PLATFORM_READINESS_RECOVERY attestation.
-- This migration does NOT perform runtime cutover; W6 remains live/operator driven.

comment on table runtime_config_cutover_wave_safety_attestations is
  'Durable expiring pre-cutover safety evidence. W4 Task/Dispatch, W5 External Integration/A2A, and W6 Platform Readiness/Recovery require fresh PASS evidence before PREPARE and through FINALIZE.';

do $$
declare
  w6_sets integer;
  w6_keys integer;
  runtime_targets integer;
  w6_migrated integer;
begin
  select count(*),coalesce(sum(expected_runtime_key_count),0)
    into w6_sets,w6_keys
    from runtime_config_cutover_wave_members where wave_id='C3R3-W6';
  if w6_sets <> 5 then raise exception 'C3R3G_W6_CONFIG_SET_COUNT_INVALID expected=5 actual=%',w6_sets; end if;
  if w6_keys <> 105 then raise exception 'C3R3G_W6_RUNTIME_KEY_COUNT_INVALID expected=105 actual=%',w6_keys; end if;

  select count(*) into runtime_targets from runtime_config_definitions
   where authority_class='RUNTIME_TUNABLE' and migration_authorized=true;
  if runtime_targets <> 296 then raise exception 'C3R3G_RUNTIME_TARGET_BASELINE_INVALID expected=296 actual=%',runtime_targets; end if;

  -- Flyway is intentionally not allowed to mutate W6 governance. Live platform readiness,
  -- PREPARE, Authority Contract v2 ACK convergence, FINALIZE and durable certification are mandatory.
  select count(*) into w6_migrated
    from runtime_config_inventory_governance g
    join runtime_config_definitions d on d.definition_key=g.configuration_key
    join runtime_config_cutover_wave_members m on m.set_key=d.config_set_key and m.wave_id='C3R3-W6'
   where g.status in ('MIGRATED','LEGACY_RETIRED');
  if w6_migrated <> 0 then raise exception 'C3R3G_PRE_DEPLOY_W6_RUNTIME_CUTOVER_ALREADY_PRESENT count=%',w6_migrated; end if;
end $$;
