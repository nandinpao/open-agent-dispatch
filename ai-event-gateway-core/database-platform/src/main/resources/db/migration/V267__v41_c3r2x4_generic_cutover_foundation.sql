-- V41 C3R2-X4 Generic Cutover Foundation
-- 1) persist node authority-contract capability so cutover cannot outrun rolling upgrades
-- 2) persist two-phase cutover intent so RUNTIME_ONLY snapshots converge before MIGRATED governance commit

alter table runtime_config_required_node_targets
  add column if not exists supported_authority_contract_version integer not null default 1;

alter table runtime_config_required_node_targets
  drop constraint if exists ck_runtime_config_required_node_targets_authority_contract;
alter table runtime_config_required_node_targets
  add constraint ck_runtime_config_required_node_targets_authority_contract
  check (supported_authority_contract_version >= 1 and supported_authority_contract_version <= 100);

create table if not exists runtime_config_cutovers (
  cutover_id varchar(64) primary key,
  config_set_id varchar(128) not null references runtime_config_sets(config_set_id),
  set_key varchar(256) not null,
  environment varchar(32) not null,
  revision_id varchar(128) not null,
  authority_contract_version integer not null,
  target_authority_mode varchar(32) not null,
  required_keys_json jsonb not null,
  expected_snapshot_fingerprint varchar(64) not null,
  state varchar(32) not null,
  requested_by varchar(256) not null,
  reason varchar(2000) not null,
  prepared_at timestamptz not null default now(),
  finalized_at timestamptz,
  cancelled_at timestamptz,
  version bigint not null default 1,
  updated_at timestamptz not null default now(),
  constraint ck_runtime_config_cutover_contract check (authority_contract_version >= 2),
  constraint ck_runtime_config_cutover_authority check (target_authority_mode = 'RUNTIME_ONLY'),
  constraint ck_runtime_config_cutover_state check (state in ('PREPARED','FINALIZED','CANCELLED')),
  constraint ck_runtime_config_cutover_fingerprint check (expected_snapshot_fingerprint ~ '^[0-9a-fA-F]{64}$'),
  constraint ck_runtime_config_cutover_required_keys check (jsonb_typeof(required_keys_json)='array' and jsonb_array_length(required_keys_json)>0)
);

create unique index if not exists uq_runtime_config_cutovers_one_prepared_per_set
  on runtime_config_cutovers(config_set_id)
  where state='PREPARED';

create index if not exists ix_runtime_config_cutovers_set_history
  on runtime_config_cutovers(config_set_id,prepared_at desc);

comment on table runtime_config_cutovers is
  'Two-phase Runtime Configuration authority cutover intent. PREPARED projects RUNTIME_ONLY authority into signed snapshots before durable MIGRATED governance commit.';
