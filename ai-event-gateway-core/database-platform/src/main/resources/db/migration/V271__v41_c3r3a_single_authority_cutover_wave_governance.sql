-- V41 C3R3-A Single Authority Cutover Wave Governance
-- Release-governed ordering only. This migration MUST NOT transition any configuration key to MIGRATED.

create table if not exists runtime_config_cutover_waves (
  wave_id varchar(64) primary key,
  sequence_no integer not null unique,
  display_name varchar(256) not null,
  risk_tier varchar(32) not null,
  required_authority_contract_version integer not null default 2,
  release_stage varchar(128) not null,
  created_at timestamptz not null default now(),
  constraint ck_runtime_config_cutover_wave_sequence check (sequence_no >= 1),
  constraint ck_runtime_config_cutover_wave_contract check (required_authority_contract_version >= 2),
  constraint ck_runtime_config_cutover_wave_risk check (risk_tier in ('LOW','MEDIUM','HIGH','CRITICAL'))
);

create table if not exists runtime_config_cutover_wave_members (
  wave_id varchar(64) not null references runtime_config_cutover_waves(wave_id) on delete cascade,
  set_key varchar(256) not null,
  member_sequence_no integer not null,
  expected_runtime_key_count integer not null,
  primary key(wave_id,set_key),
  unique(set_key),
  constraint ck_runtime_config_cutover_wave_member_sequence check (member_sequence_no >= 1),
  constraint ck_runtime_config_cutover_wave_member_key_count check (expected_runtime_key_count >= 1)
);

comment on table runtime_config_cutover_waves is
  'Release-governed ordered waves for Runtime Configuration Single Authority cutover. Membership is governance metadata, not a hot Runtime Configuration value.';
comment on table runtime_config_cutover_wave_members is
  'Config Set membership and expected Runtime key count for ordered C3R3 Single Authority cutover waves.';

insert into runtime_config_cutover_waves(wave_id,sequence_no,display_name,risk_tier,required_authority_contract_version,release_stage)
values
 ('C3R3-W1',1,'Bounded operational controls','MEDIUM',2,'V41_C3R3A_SINGLE_AUTHORITY_CUTOVER_WAVE_GOVERNANCE'),
 ('C3R3-W2',2,'Adapter execution and worker controls','MEDIUM',2,'V41_C3R3A_SINGLE_AUTHORITY_CUTOVER_WAVE_GOVERNANCE'),
 ('C3R3-W3',3,'Cluster and connection protection controls','MEDIUM',2,'V41_C3R3A_SINGLE_AUTHORITY_CUTOVER_WAVE_GOVERNANCE'),
 ('C3R3-W4',4,'Task dispatch and task-authority controls','HIGH',2,'V41_C3R3A_SINGLE_AUTHORITY_CUTOVER_WAVE_GOVERNANCE'),
 ('C3R3-W5',5,'Incident issue integration and A2A controls','HIGH',2,'V41_C3R3A_SINGLE_AUTHORITY_CUTOVER_WAVE_GOVERNANCE'),
 ('C3R3-W6',6,'Core edge gateway and admin controls','CRITICAL',2,'V41_C3R3A_SINGLE_AUTHORITY_CUTOVER_WAVE_GOVERNANCE')
on conflict(wave_id) do update set
 sequence_no=excluded.sequence_no,display_name=excluded.display_name,risk_tier=excluded.risk_tier,
 required_authority_contract_version=excluded.required_authority_contract_version,release_stage=excluded.release_stage;

insert into runtime_config_cutover_wave_members(wave_id,set_key,member_sequence_no,expected_runtime_key_count)
values
 ('C3R3-W1','RUNTIME/OPENDISPATCH/SYSTEM',1,3),
 ('C3R3-W1','RUNTIME/EVENT/SYSTEM',2,2),
 ('C3R3-W1','RUNTIME/GATEWAY_NODES/SYSTEM',3,1),
 ('C3R3-W1','RUNTIME/AGENT_DIRECTORY/SYSTEM',4,1),
 ('C3R3-W1','RUNTIME/CAPABILITY_OPERATIONAL/SYSTEM',5,1),
 ('C3R3-W1','RUNTIME/AGENT_REMEDIATION/SYSTEM',6,4),
 ('C3R3-W2','RUNTIME/ADAPTER_ACTION/SYSTEM',1,13),
 ('C3R3-W2','RUNTIME/ADAPTER_WORKER/SYSTEM',2,5),
 ('C3R3-W2','RUNTIME/ADAPTER_EXECUTION/SYSTEM',3,24),
 ('C3R3-W3','RUNTIME/AGENT/SYSTEM',1,2),
 ('C3R3-W3','RUNTIME/CLUSTER/SYSTEM',2,11),
 ('C3R3-W3','RUNTIME/CONNECTION_PROTECTION/SYSTEM',3,7),
 ('C3R3-W4','RUNTIME/TASK/SYSTEM',1,38),
 ('C3R3-W4','RUNTIME/DISPATCH/SYSTEM',2,26),
 ('C3R3-W4','RUNTIME/TASK_AUTHORITY/SYSTEM',3,13),
 ('C3R3-W5','RUNTIME/INCIDENT/SYSTEM',1,6),
 ('C3R3-W5','RUNTIME/ISSUE/SYSTEM',2,5),
 ('C3R3-W5','RUNTIME/INTEGRATION_SYNC/SYSTEM',3,10),
 ('C3R3-W5','RUNTIME/A2A_DELEGATION/SYSTEM',4,19),
 ('C3R3-W6','RUNTIME/ANALYTICS/SYSTEM',1,4),
 ('C3R3-W6','RUNTIME/ADMIN/SYSTEM',2,3),
 ('C3R3-W6','RUNTIME/NETTY/SYSTEM',3,7),
 ('C3R3-W6','RUNTIME/GATEWAY/SYSTEM',4,24),
 ('C3R3-W6','RUNTIME/CORE/SYSTEM',5,67)
on conflict(set_key) do update set
 wave_id=excluded.wave_id,member_sequence_no=excluded.member_sequence_no,expected_runtime_key_count=excluded.expected_runtime_key_count;

do $$
declare
  wave_count integer;
  member_count integer;
  planned_key_count integer;
  runtime_target_count integer;
  proposed_count integer;
  migrated_count integer;
begin
  select count(*) into wave_count from runtime_config_cutover_waves where release_stage='V41_C3R3A_SINGLE_AUTHORITY_CUTOVER_WAVE_GOVERNANCE';
  if wave_count <> 6 then raise exception 'C3R3A_CUTOVER_WAVE_COUNT_INVALID expected=6 actual=%',wave_count; end if;

  select count(*),coalesce(sum(expected_runtime_key_count),0) into member_count,planned_key_count
    from runtime_config_cutover_wave_members m join runtime_config_cutover_waves w on w.wave_id=m.wave_id
   where w.release_stage='V41_C3R3A_SINGLE_AUTHORITY_CUTOVER_WAVE_GOVERNANCE';
  if member_count <> 24 then raise exception 'C3R3A_CUTOVER_WAVE_MEMBER_COUNT_INVALID expected=24 actual=%',member_count; end if;
  if planned_key_count <> 296 then raise exception 'C3R3A_CUTOVER_WAVE_KEY_COUNT_INVALID expected=296 actual=%',planned_key_count; end if;

  select count(*) into runtime_target_count from runtime_config_definitions
   where authority_class='RUNTIME_TUNABLE' and migration_authorized=true;
  if runtime_target_count <> 296 then raise exception 'C3R3A_RUNTIME_TARGET_BASELINE_INVALID expected=296 actual=%',runtime_target_count; end if;

  if exists (
    select 1
      from runtime_config_cutover_wave_members m
      left join (
        select config_set_key,count(*) key_count
          from runtime_config_definitions
         where authority_class='RUNTIME_TUNABLE' and migration_authorized=true
         group by config_set_key
      ) d on d.config_set_key=m.set_key
     where coalesce(d.key_count,0)<>m.expected_runtime_key_count
  ) then
    raise exception 'C3R3A_CUTOVER_WAVE_CONFIG_SET_KEY_COUNT_DRIFT';
  end if;

  select count(*) into proposed_count from runtime_config_inventory_governance
   where status not in ('MIGRATION_READY','MIGRATED','LEGACY_RETIRED')
     and configuration_key in (select definition_key from runtime_config_definitions where authority_class='RUNTIME_TUNABLE' and migration_authorized=true);
  if proposed_count <> 0 then raise exception 'C3R3A_RUNTIME_TARGET_NOT_MIGRATION_READY count=%',proposed_count; end if;

  select count(*) into migrated_count from runtime_config_inventory_governance
   where status in ('MIGRATED','LEGACY_RETIRED')
     and configuration_key in (select definition_key from runtime_config_definitions where authority_class='RUNTIME_TUNABLE' and migration_authorized=true);
  if migrated_count <> 0 then raise exception 'C3R3A_PREMATURE_SINGLE_AUTHORITY_CUTOVER count=%',migrated_count; end if;
end $$;
