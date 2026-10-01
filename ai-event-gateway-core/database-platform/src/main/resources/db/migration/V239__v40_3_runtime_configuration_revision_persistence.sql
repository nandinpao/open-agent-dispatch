-- V40-3: Runtime Configuration revision persistence and append-only audit.
-- Scope: persistence only. Redis distribution, node apply-state/ACK, promotion and emergency override are later stages.

create table if not exists runtime_config_definitions (
  definition_key varchar(255) primary key,
  owner varchar(128) not null,
  authority_class varchar(64) not null,
  scope varchar(64) not null,
  data_type varchar(64) not null,
  risk varchar(32) not null,
  mutability varchar(64) not null,
  consumer_contract varchar(64) not null,
  target_mutability varchar(64),
  target_consumer_contract varchar(64),
  environment_applicability jsonb not null default '[]'::jsonb,
  validation_rule jsonb not null default '{}'::jsonb,
  review_status varchar(64) not null,
  migration_authorized boolean not null default false,
  schema_version integer not null default 1,
  source_ref text not null,
  source_fingerprint varchar(128),
  synchronized_at timestamptz not null default now(),
  check (authority_class in ('BOOTSTRAP_INFRA','SECURITY_INVARIANT','RUNTIME_TUNABLE','DOMAIN_CONFIG','SECRET_MATERIAL','TEST_RELEASE_CONFIG')),
  check (scope in ('SYSTEM','COMPONENT','NODE_ROLE','TENANT','DEPARTMENT','SOURCE_SYSTEM','PROVIDER','FLOW','AGENT','USER')),
  check (risk in ('LOW','MEDIUM','HIGH','CRITICAL')),
  check (mutability in ('HOT_IMMEDIATE','HOT_NEXT_CYCLE','RESTART_REQUIRED','IMMUTABLE')),
  check (consumer_contract in ('RUNTIME_SNAPSHOT','DYNAMIC_SCHEDULER','STARTUP_BINDING','SPRING_CONDITION','DEPLOYMENT_ONLY')),
  check (target_mutability is null or target_mutability in ('HOT_IMMEDIATE','HOT_NEXT_CYCLE','RESTART_REQUIRED','IMMUTABLE')),
  check (target_consumer_contract is null or target_consumer_contract in ('RUNTIME_SNAPSHOT','DYNAMIC_SCHEDULER','STARTUP_BINDING','SPRING_CONDITION','DEPLOYMENT_ONLY')),
  check (review_status in ('PROPOSED','OWNER_REVIEWED','ARCHITECTURE_APPROVED','MIGRATION_READY','RETIRED')),
  check (schema_version >= 1)
);
comment on table runtime_config_definitions is 'V40 source-definition materialization. Rows do not become runtime value authority; migration_authorized must be true before use in revisions.';

create table if not exists runtime_config_sets (
  config_set_id varchar(128) primary key,
  set_key varchar(255) not null,
  environment varchar(16) not null,
  scope varchar(64) not null,
  scope_ref varchar(255),
  owner_component varchar(128) not null,
  status varchar(32) not null default 'ACTIVE',
  resource_version bigint not null default 1,
  created_at timestamptz not null default now(),
  created_by varchar(128) not null,
  updated_at timestamptz not null default now(),
  updated_by varchar(128) not null,
  unique(environment,set_key),
  check (environment in ('PRD','UAT','SIT','QA','DEV','LOCAL')),
  check (scope in ('SYSTEM','COMPONENT','NODE_ROLE')),
  check (status in ('ACTIVE','RETIRED')),
  check (resource_version >= 1),
  check ((scope='SYSTEM' and scope_ref is null) or (scope in ('COMPONENT','NODE_ROLE') and nullif(trim(scope_ref),'') is not null))
);
comment on table runtime_config_sets is 'V40 revision aggregate / concurrency boundary. Generic scope is limited to SYSTEM, COMPONENT and NODE_ROLE.';

create table if not exists runtime_config_revisions (
  revision_id varchar(128) primary key,
  config_set_id varchar(128) not null references runtime_config_sets(config_set_id),
  sequence_no bigint not null,
  base_revision_id varchar(128),
  rollback_of_revision_id varchar(128),
  restore_source_revision_id varchar(128),
  state varchar(32) not null default 'DRAFT',
  definition_schema_version integer not null default 1,
  reason text not null,
  created_at timestamptz not null default now(),
  created_by varchar(128) not null,
  validated_at timestamptz,
  validated_by varchar(128),
  submitted_at timestamptz,
  submitted_by varchar(128),
  approved_at timestamptz,
  approved_by varchar(128),
  published_at timestamptz,
  published_by varchar(128),
  updated_at timestamptz not null default now(),
  updated_by varchar(128) not null,
  unique(config_set_id,sequence_no),
  unique(config_set_id,revision_id),
  check (sequence_no >= 1),
  check (definition_schema_version >= 1),
  check (state in ('DRAFT','VALIDATED','PENDING_APPROVAL','APPROVED','PUBLISHED','SUPERSEDED','REJECTED','CANCELLED')),
  foreign key(config_set_id,base_revision_id) references runtime_config_revisions(config_set_id,revision_id),
  foreign key(config_set_id,rollback_of_revision_id) references runtime_config_revisions(config_set_id,revision_id),
  foreign key(config_set_id,restore_source_revision_id) references runtime_config_revisions(config_set_id,revision_id)
);
create index if not exists idx_runtime_config_revisions_set_state on runtime_config_revisions(config_set_id,state,sequence_no desc);
comment on table runtime_config_revisions is 'Immutable revision identity and controlled lifecycle. Published history is never edited in place.';

create table if not exists runtime_config_revision_items (
  revision_id varchar(128) not null references runtime_config_revisions(revision_id) on delete cascade,
  definition_key varchar(255) not null references runtime_config_definitions(definition_key),
  value_json jsonb not null,
  value_fingerprint varchar(128) not null,
  created_at timestamptz not null default now(),
  created_by varchar(128) not null,
  updated_at timestamptz not null default now(),
  updated_by varchar(128) not null,
  primary key(revision_id,definition_key),
  check (value_fingerprint ~ '^[0-9a-fA-F]{64}$')
);
comment on table runtime_config_revision_items is 'Typed canonical JSON values. Only DRAFT items may be inserted/updated/deleted.';

create table if not exists runtime_config_active_revisions (
  config_set_id varchar(128) primary key references runtime_config_sets(config_set_id),
  revision_id varchar(128) not null,
  activation_version bigint not null default 1,
  updated_at timestamptz not null default now(),
  updated_by varchar(128) not null,
  foreign key(config_set_id,revision_id) references runtime_config_revisions(config_set_id,revision_id),
  check (activation_version >= 1)
);
comment on table runtime_config_active_revisions is 'Single authoritative revision pointer per Config Set. Publication updates this pointer atomically.';

create table if not exists runtime_config_audit_logs (
  audit_id varchar(128) primary key,
  config_set_id varchar(128) not null references runtime_config_sets(config_set_id),
  revision_id varchar(128) references runtime_config_revisions(revision_id),
  action varchar(64) not null,
  actor varchar(128) not null,
  reason text,
  correlation_id varchar(128),
  metadata_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);
create index if not exists idx_runtime_config_audit_set_time on runtime_config_audit_logs(config_set_id,created_at desc);
create index if not exists idx_runtime_config_audit_revision on runtime_config_audit_logs(revision_id,created_at);
comment on table runtime_config_audit_logs is 'Append-only configuration governance evidence. Raw secret material is forbidden by contract.';

create or replace function protect_runtime_config_audit_append_only() returns trigger language plpgsql as $$
begin
  raise exception 'RUNTIME_CONFIG_AUDIT_IMMUTABLE';
end $$;
drop trigger if exists trg_runtime_config_audit_append_only on runtime_config_audit_logs;
create trigger trg_runtime_config_audit_append_only before update or delete on runtime_config_audit_logs
for each row execute function protect_runtime_config_audit_append_only();

create or replace function guard_runtime_config_revision_transition() returns trigger language plpgsql as $$
declare
  v_item_count integer;
  v_invalid_count integer;
begin
  if new.revision_id is distinct from old.revision_id
     or new.config_set_id is distinct from old.config_set_id
     or new.sequence_no is distinct from old.sequence_no
     or new.base_revision_id is distinct from old.base_revision_id
     or new.rollback_of_revision_id is distinct from old.rollback_of_revision_id
     or new.restore_source_revision_id is distinct from old.restore_source_revision_id
     or new.definition_schema_version is distinct from old.definition_schema_version
     or new.reason is distinct from old.reason
     or new.created_at is distinct from old.created_at
     or new.created_by is distinct from old.created_by then
    raise exception 'RUNTIME_CONFIG_REVISION_IDENTITY_IMMUTABLE';
  end if;

  if new.state is distinct from old.state and not (
       (old.state='DRAFT' and new.state in ('VALIDATED','CANCELLED'))
    or (old.state='VALIDATED' and new.state in ('DRAFT','PENDING_APPROVAL','APPROVED','CANCELLED'))
    or (old.state='PENDING_APPROVAL' and new.state in ('APPROVED','REJECTED','CANCELLED'))
    or (old.state='APPROVED' and new.state in ('PUBLISHED','CANCELLED'))
    or (old.state='PUBLISHED' and new.state='SUPERSEDED')
  ) then
    raise exception 'RUNTIME_CONFIG_REVISION_TRANSITION_DENIED: % -> %', old.state,new.state;
  end if;

  if new.state in ('VALIDATED','PENDING_APPROVAL','APPROVED','PUBLISHED') and new.state is distinct from old.state then
    select count(*) into v_item_count from runtime_config_revision_items where revision_id=new.revision_id;
    if v_item_count < 1 then
      raise exception 'RUNTIME_CONFIG_REVISION_EMPTY: %',new.revision_id;
    end if;
    select count(*) into v_invalid_count
      from runtime_config_revision_items i
      join runtime_config_definitions d on d.definition_key=i.definition_key
     where i.revision_id=new.revision_id
       and (d.migration_authorized is not true or d.authority_class <> 'RUNTIME_TUNABLE');
    if v_invalid_count > 0 then
      raise exception 'RUNTIME_CONFIG_REVISION_DEFINITION_AUTHORITY_CHANGED: % invalid items %',new.revision_id,v_invalid_count;
    end if;
  end if;

  if old.state in ('SUPERSEDED','REJECTED','CANCELLED') then
    raise exception 'RUNTIME_CONFIG_REVISION_TERMINAL_IMMUTABLE';
  end if;

  new.updated_at=now();
  return new;
end $$;
drop trigger if exists trg_runtime_config_revision_transition on runtime_config_revisions;
create trigger trg_runtime_config_revision_transition before update on runtime_config_revisions
for each row execute function guard_runtime_config_revision_transition();

create or replace function guard_runtime_config_revision_item_write() returns trigger language plpgsql as $$
declare
  v_revision_id varchar(128);
  v_state varchar(32);
  v_authorized boolean;
  v_authority varchar(64);
begin
  v_revision_id := case when tg_op='DELETE' then old.revision_id else new.revision_id end;
  select state into v_state from runtime_config_revisions where revision_id=v_revision_id;
  if v_state is distinct from 'DRAFT' then
    raise exception 'RUNTIME_CONFIG_REVISION_ITEMS_IMMUTABLE: revision % state %',v_revision_id,v_state;
  end if;
  if tg_op <> 'DELETE' then
    select migration_authorized,authority_class into v_authorized,v_authority
      from runtime_config_definitions where definition_key=new.definition_key;
    if coalesce(v_authorized,false) is not true then
      raise exception 'RUNTIME_CONFIG_DEFINITION_NOT_MIGRATION_AUTHORIZED: %',new.definition_key;
    end if;
    if v_authority is distinct from 'RUNTIME_TUNABLE' then
      raise exception 'RUNTIME_CONFIG_DEFINITION_AUTHORITY_DENIED: % -> %',new.definition_key,v_authority;
    end if;
    perform 1 from runtime_config_definitions d
     where d.definition_key=new.definition_key and (
       d.data_type='JSON'
       or (d.data_type='BOOLEAN' and jsonb_typeof(new.value_json)='boolean')
       or (d.data_type in ('INTEGER','LONG') and jsonb_typeof(new.value_json)='number' and (new.value_json #>> '{}') ~ '^-?[0-9]+$')
       or (d.data_type='DECIMAL' and jsonb_typeof(new.value_json)='number')
       or (d.data_type in ('STRING','DURATION','ENUM','URI') and jsonb_typeof(new.value_json)='string')
     );
    if not found then
      raise exception 'RUNTIME_CONFIG_VALUE_TYPE_MISMATCH: %',new.definition_key;
    end if;
    new.updated_at=now();
  end if;
  return case when tg_op='DELETE' then old else new end;
end $$;
drop trigger if exists trg_runtime_config_revision_item_write on runtime_config_revision_items;
create trigger trg_runtime_config_revision_item_write before insert or update or delete on runtime_config_revision_items
for each row execute function guard_runtime_config_revision_item_write();

create or replace function guard_runtime_config_active_revision() returns trigger language plpgsql as $$
declare
  v_state varchar(32);
begin
  select state into v_state from runtime_config_revisions
   where config_set_id=new.config_set_id and revision_id=new.revision_id;
  if v_state is distinct from 'PUBLISHED' then
    raise exception 'RUNTIME_CONFIG_ACTIVE_REVISION_NOT_PUBLISHED: % state %',new.revision_id,v_state;
  end if;
  if tg_op='UPDATE' and new.activation_version <> old.activation_version + 1 then
    raise exception 'RUNTIME_CONFIG_ACTIVE_REVISION_VERSION_CONFLICT';
  end if;
  new.updated_at=now();
  return new;
end $$;
drop trigger if exists trg_runtime_config_active_revision on runtime_config_active_revisions;
create trigger trg_runtime_config_active_revision before insert or update on runtime_config_active_revisions
for each row execute function guard_runtime_config_active_revision();
