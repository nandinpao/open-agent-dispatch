-- V41-C3R3G-HF5: expose remote Runtime Configuration authority-health evidence to the control plane.
-- These fields are telemetry about the signed snapshot already accepted by a node. They never become
-- a configuration authority and they never alter desired/applied revision convergence by themselves.

alter table runtime_config_apply_states
    add column if not exists authority_runtime_state varchar(32),
    add column if not exists snapshot_expires_at timestamptz,
    add column if not exists authority_observed_at timestamptz;

alter table runtime_config_apply_states
    drop constraint if exists chk_runtime_config_apply_states_authority_runtime_state;

alter table runtime_config_apply_states
    add constraint chk_runtime_config_apply_states_authority_runtime_state
    check (authority_runtime_state is null or authority_runtime_state in ('ACTIVE','STALE_LKG','EXPIRED','INVALID','MISSING'));

create index if not exists idx_runtime_config_apply_states_authority_health
    on runtime_config_apply_states(config_set_id, authority_runtime_state, authority_observed_at);

comment on column runtime_config_apply_states.authority_runtime_state is
    'Node-reported signed snapshot usability state. Telemetry only; PostgreSQL desired revision remains durable authority.';
comment on column runtime_config_apply_states.snapshot_expires_at is
    'Expiry embedded in the signed snapshot currently held by the node.';
comment on column runtime_config_apply_states.authority_observed_at is
    'Last time the node reported local Runtime Configuration authority health.';
