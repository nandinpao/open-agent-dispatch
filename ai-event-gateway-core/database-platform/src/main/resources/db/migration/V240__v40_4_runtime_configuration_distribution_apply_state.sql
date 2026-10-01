-- V40-4: Runtime Configuration transactional distribution outbox and node apply-state.
-- PostgreSQL remains authority. Redis is cache/distribution only. APPLIED requires explicit node ACK.

create table if not exists runtime_config_outbox (
  config_set_id varchar(128) not null references runtime_config_sets(config_set_id),
  revision_id varchar(128) not null,
  environment varchar(16) not null,
  status varchar(32) not null default 'PENDING',
  attempt_count integer not null default 0,
  next_attempt_at timestamptz not null default now(),
  lease_owner varchar(128),
  lease_until timestamptz,
  payload_fingerprint varchar(128),
  last_error text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  distributed_at timestamptz,
  primary key(config_set_id,revision_id),
  foreign key(config_set_id,revision_id) references runtime_config_revisions(config_set_id,revision_id),
  check (environment in ('PRD','UAT','SIT','QA','DEV','LOCAL')),
  check (status in ('PENDING','CLAIMED','DISTRIBUTED','FAILED','SUPERSEDED')),
  check (attempt_count >= 0),
  check (payload_fingerprint is null or payload_fingerprint ~ '^[0-9a-fA-F]{64}$')
);
create index if not exists idx_runtime_config_outbox_due
  on runtime_config_outbox(status,next_attempt_at,lease_until,created_at);
comment on table runtime_config_outbox is 'Transactional distribution outbox. Creation is coupled to active-revision publication; Redis delivery is asynchronous and retryable.';

create table if not exists runtime_config_apply_states (
  config_set_id varchar(128) not null references runtime_config_sets(config_set_id),
  node_id varchar(128) not null,
  node_role varchar(32) not null,
  node_instance_id varchar(128) not null,
  desired_revision_id varchar(128) not null,
  applied_revision_id varchar(128),
  state varchar(32) not null default 'DESIRED',
  snapshot_fingerprint varchar(128),
  desired_at timestamptz not null default now(),
  distributed_at timestamptz,
  applied_at timestamptz,
  last_seen_at timestamptz not null default now(),
  error_code varchar(128),
  error_detail text,
  updated_at timestamptz not null default now(),
  primary key(config_set_id,node_id),
  foreign key(config_set_id,desired_revision_id) references runtime_config_revisions(config_set_id,revision_id),
  foreign key(config_set_id,applied_revision_id) references runtime_config_revisions(config_set_id,revision_id),
  check (node_role in ('CORE','GATEWAY','WORKER')),
  check (state in ('DESIRED','DISTRIBUTED','APPLIED','FAILED','STALE')),
  check (snapshot_fingerprint is null or snapshot_fingerprint ~ '^[0-9a-fA-F]{64}$'),
  check (state <> 'APPLIED' or applied_revision_id = desired_revision_id)
);
create index if not exists idx_runtime_config_apply_states_revision
  on runtime_config_apply_states(config_set_id,desired_revision_id,state,last_seen_at);
comment on table runtime_config_apply_states is 'Per-node desired/applied revision truth. Redis publication never writes APPLIED; only explicit successful node ACK may do so.';

create or replace function enqueue_runtime_config_distribution() returns trigger language plpgsql as $$
declare
  v_environment varchar(16);
begin
  if tg_op='UPDATE' and new.revision_id is not distinct from old.revision_id then return new; end if;
  select environment into v_environment from runtime_config_sets where config_set_id=new.config_set_id;
  insert into runtime_config_outbox(config_set_id,revision_id,environment,status,next_attempt_at)
  values(new.config_set_id,new.revision_id,v_environment,'PENDING',now())
  on conflict(config_set_id,revision_id) do nothing;
  return new;
end $$;
drop trigger if exists trg_runtime_config_active_revision_outbox on runtime_config_active_revisions;
create trigger trg_runtime_config_active_revision_outbox
after insert or update on runtime_config_active_revisions
for each row execute function enqueue_runtime_config_distribution();

create or replace function guard_runtime_config_outbox_write() returns trigger language plpgsql as $$
declare
  v_state varchar(32);
begin
  if tg_op='INSERT' then
    select state into v_state from runtime_config_revisions
     where config_set_id=new.config_set_id and revision_id=new.revision_id;
    if v_state is distinct from 'PUBLISHED' then
      raise exception 'RUNTIME_CONFIG_OUTBOX_REVISION_NOT_PUBLISHED: % state %',new.revision_id,v_state;
    end if;
  elsif new.config_set_id is distinct from old.config_set_id
     or new.revision_id is distinct from old.revision_id
     or new.environment is distinct from old.environment
     or new.created_at is distinct from old.created_at then
    raise exception 'RUNTIME_CONFIG_OUTBOX_IDENTITY_IMMUTABLE';
  end if;
  new.updated_at=now();
  return new;
end $$;
drop trigger if exists trg_runtime_config_outbox_write on runtime_config_outbox;
create trigger trg_runtime_config_outbox_write before insert or update on runtime_config_outbox
for each row execute function guard_runtime_config_outbox_write();

create or replace function guard_runtime_config_apply_state() returns trigger language plpgsql as $$
begin
  if new.state='APPLIED' and new.applied_revision_id is distinct from new.desired_revision_id then
    raise exception 'RUNTIME_CONFIG_APPLIED_REVISION_MISMATCH: desired % applied %',new.desired_revision_id,new.applied_revision_id;
  end if;
  if new.state='APPLIED' and new.applied_at is null then new.applied_at=now(); end if;
  new.last_seen_at=now();
  new.updated_at=now();
  return new;
end $$;
drop trigger if exists trg_runtime_config_apply_state on runtime_config_apply_states;
create trigger trg_runtime_config_apply_state before insert or update on runtime_config_apply_states
for each row execute function guard_runtime_config_apply_state();
