-- A0-R1 — Source Registration + Intake Authority
-- Contract: OpenDispatch MRS v5.2.1 A0-C FROZEN.
-- This migration is additive and preserves the existing SourceSystem -> Event -> Task dispatch baseline.
-- SourceSystem remains master data. WorkloadSourceRegistration is a governed 1:N ingress contract.

create table if not exists workload_source_registrations (
  tenant_id varchar(64) not null,
  source_registration_id varchar(160) not null,
  source_system_id varchar(128) not null,
  registration_name varchar(255) not null,
  channel_type varchar(32) not null default 'EVENT',
  principal_binding_mode varchar(16) not null default 'DYNAMIC',
  allowed_principal_types varchar(48)[] not null default array[]::varchar[],
  static_principal_ref varchar(255),
  owner_department_id varchar(128),
  owner_group_id varchar(128),
  allowed_event_types varchar(128)[] not null default array[]::varchar[],
  allowed_object_types varchar(128)[] not null default array[]::varchar[],
  input_schemas varchar(160)[] not null default array[]::varchar[],
  idempotency_strategy varchar(32) not null default 'OPTIONAL_KEY',
  idempotency_retention_seconds bigint not null default 86400,
  ordering_strategy varchar(32) not null default 'NONE',
  acknowledgement_mode varchar(32) not null default 'SYNC_RESPONSE',
  rate_limit_per_minute integer,
  quota_per_day bigint,
  data_classification_profile varchar(128),
  residency_profile varchar(128),
  status varchar(24) not null default 'ACTIVE',
  effective_from timestamptz not null default now(),
  effective_to timestamptz,
  is_default boolean not null default false,
  version bigint not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key(tenant_id,source_registration_id),
  foreign key(tenant_id,source_system_id) references source_systems(tenant_id,source_system_id),
  constraint a0r1_registration_channel check(channel_type in ('EVENT','API','HUMAN','SCHEDULE','A2A_INBOUND','REPLAY')),
  constraint a0r1_registration_binding check(principal_binding_mode in ('STATIC','DYNAMIC')),
  constraint a0r1_registration_static_principal check(principal_binding_mode<>'STATIC' or nullif(btrim(static_principal_ref),'') is not null),
  constraint a0r1_registration_idempotency check(idempotency_strategy in ('NONE','OPTIONAL_KEY','REQUIRED_KEY','SOURCE_EVENT_ID')),
  constraint a0r1_registration_idempotency_retention check(idempotency_retention_seconds>=60),
  constraint a0r1_registration_ordering check(ordering_strategy in ('NONE','SOURCE_SEQUENCE','AGGREGATE_SEQUENCE')),
  constraint a0r1_registration_ack check(acknowledgement_mode in ('SYNC_RESPONSE','CALLBACK','OUTBOX_EVENT','POLL','NONE')),
  constraint a0r1_registration_status check(status in ('ACTIVE','DISABLED','RETIRED')),
  constraint a0r1_registration_effective_window check(effective_to is null or effective_to>effective_from),
  constraint a0r1_registration_rate check(rate_limit_per_minute is null or rate_limit_per_minute>0),
  constraint a0r1_registration_quota check(quota_per_day is null or quota_per_day>0)
);
create index if not exists idx_a0r1_registration_source on workload_source_registrations(tenant_id,source_system_id,channel_type,status,is_default);
create unique index if not exists uq_a0r1_default_registration on workload_source_registrations(tenant_id,source_system_id,channel_type)
  where is_default=true and status='ACTIVE';

-- Existing production Source Systems receive a single compatibility registration. It is intentionally
-- DYNAMIC and permits the current machine JWT principal plus the legacy integration compatibility lane.
insert into workload_source_registrations(
  tenant_id,source_registration_id,source_system_id,registration_name,channel_type,principal_binding_mode,
  allowed_principal_types,owner_department_id,owner_group_id,idempotency_strategy,idempotency_retention_seconds,
  ordering_strategy,acknowledgement_mode,status,is_default,effective_from,created_at,updated_at)
select s.tenant_id,
       'event-default-'||substr(md5(s.tenant_id||':'||s.source_system_id),1,20),
       s.source_system_id,
       coalesce(nullif(s.display_name,''),s.source_system_id)||' Event Intake',
       'EVENT','DYNAMIC',array['SERVICE_ACCOUNT','INTEGRATION']::varchar[],
       s.owner_department_id,s.owner_group_id,
       'OPTIONAL_KEY',86400,'NONE','SYNC_RESPONSE',
       case when s.status='ACTIVE' then 'ACTIVE' else 'DISABLED' end,
       case when s.status='ACTIVE' then true else false end,now(),now(),now()
  from source_systems s
 where s.status<>'RETIRED'
on conflict(tenant_id,source_registration_id) do nothing;

create table if not exists intake_records (
  tenant_id varchar(64) not null,
  ingestion_id varchar(180) not null,
  source_registration_id varchar(160),
  source_system_id varchar(128) not null,
  channel_type varchar(32) not null default 'EVENT',
  source_event_id varchar(255),
  idempotency_key varchar(255),
  payload_digest varchar(96) not null,
  digest_algorithm varchar(24) not null default 'SHA256',
  canonicalization_version varchar(48) not null default 'A0R1_JSON_V1',
  envelope_metadata_json jsonb not null default '{}'::jsonb,
  authenticated_principal_type varchar(48) not null,
  authenticated_principal_ref varchar(255) not null,
  origin_principal_type varchar(48) not null,
  origin_principal_ref varchar(255) not null,
  asserted_origin_principal varchar(255),
  owner_department_id varchar(128),
  owner_group_id varchar(128),
  disposition varchar(24) not null,
  disposition_reason varchar(128) not null,
  disposition_at timestamptz not null default now(),
  materialized_task_id varchar(128),
  materialization_status varchar(32) not null default 'NOT_STARTED',
  deferred_until timestamptz,
  retention_policy_ref varchar(128),
  schema_version varchar(64) not null default '1',
  key_expired boolean not null default false,
  decision_response_json jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key(tenant_id,ingestion_id),
  foreign key(tenant_id,source_registration_id) references workload_source_registrations(tenant_id,source_registration_id),
  constraint a0r1_intake_disposition check(disposition in ('ACCEPTED','DEFERRED','THROTTLED','REJECTED','QUARANTINED')),
  constraint a0r1_intake_materialization check(materialization_status in ('NOT_STARTED','PENDING','MATERIALIZED','DECIDED_NO_TASK','FAILED_RETRYABLE','NOT_APPLICABLE')),
  constraint a0r1_intake_payload_digest check(payload_digest ~ '^[0-9a-f]{64}$'),
  constraint a0r1_intake_envelope_object check(jsonb_typeof(envelope_metadata_json)='object'),
  constraint a0r1_intake_response_object check(decision_response_json is null or jsonb_typeof(decision_response_json)='object')
);
create index if not exists idx_a0r1_intake_source_time on intake_records(tenant_id,source_system_id,created_at desc);
create index if not exists idx_a0r1_intake_registration_time on intake_records(tenant_id,source_registration_id,created_at desc);
create index if not exists idx_a0r1_intake_disposition on intake_records(tenant_id,disposition,created_at desc);
create index if not exists idx_a0r1_intake_task on intake_records(tenant_id,materialized_task_id) where materialized_task_id is not null;

-- Fixed-window admission counters. They avoid counting the 10M+ Intake history on every request.
create table if not exists intake_registration_rate_buckets (
  tenant_id varchar(64) not null,
  source_registration_id varchar(160) not null,
  bucket_type varchar(16) not null,
  bucket_start timestamptz not null,
  request_count bigint not null default 0,
  updated_at timestamptz not null default now(),
  primary key(tenant_id,source_registration_id,bucket_type,bucket_start),
  foreign key(tenant_id,source_registration_id) references workload_source_registrations(tenant_id,source_registration_id) on delete cascade,
  constraint a0r1_rate_bucket_type check(bucket_type in ('MINUTE','DAY')),
  constraint a0r1_rate_bucket_count check(request_count>=0)
);
create index if not exists idx_a0r1_rate_bucket_cleanup on intake_registration_rate_buckets(bucket_type,bucket_start);

create table if not exists intake_disposition_history (
  tenant_id varchar(64) not null,
  history_id varchar(180) not null,
  ingestion_id varchar(180) not null,
  from_disposition varchar(24),
  to_disposition varchar(24) not null,
  reason varchar(128) not null,
  materialized_task_id varchar(128),
  occurred_at timestamptz not null default now(),
  primary key(tenant_id,history_id),
  foreign key(tenant_id,ingestion_id) references intake_records(tenant_id,ingestion_id) on delete cascade,
  constraint a0r1_history_from check(from_disposition is null or from_disposition in ('ACCEPTED','DEFERRED','THROTTLED','REJECTED','QUARANTINED')),
  constraint a0r1_history_to check(to_disposition in ('ACCEPTED','DEFERRED','THROTTLED','REJECTED','QUARANTINED'))
);
create index if not exists idx_a0r1_intake_history on intake_disposition_history(tenant_id,ingestion_id,occurred_at);

create table if not exists ingestion_idempotency_records (
  tenant_id varchar(64) not null,
  source_registration_id varchar(160) not null,
  idempotency_key varchar(255) not null,
  generation integer not null,
  payload_digest varchar(96) not null,
  digest_algorithm varchar(24) not null default 'SHA256',
  canonicalization_version varchar(48) not null default 'A0R1_JSON_V1',
  intake_id varchar(180) not null,
  first_seen_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now(),
  seen_count bigint not null default 1,
  expires_at timestamptz not null,
  primary key(tenant_id,source_registration_id,idempotency_key,generation),
  foreign key(tenant_id,intake_id) references intake_records(tenant_id,ingestion_id) on delete cascade,
  constraint a0r1_idempotency_generation check(generation>=1),
  constraint a0r1_idempotency_seen check(seen_count>=1),
  constraint a0r1_idempotency_digest check(payload_digest ~ '^[0-9a-f]{64}$')
);
create index if not exists idx_a0r1_idempotency_latest on ingestion_idempotency_records(tenant_id,source_registration_id,idempotency_key,generation desc);

create table if not exists intake_security_evidence (
  tenant_id varchar(64) not null,
  security_event_id varchar(180) not null,
  ingestion_id varchar(180) not null,
  source_registration_id varchar(160),
  event_type varchar(64) not null,
  severity varchar(24) not null default 'HIGH',
  reason varchar(512) not null,
  evidence_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  primary key(tenant_id,security_event_id),
  foreign key(tenant_id,ingestion_id) references intake_records(tenant_id,ingestion_id) on delete cascade,
  constraint a0r1_security_evidence_object check(jsonb_typeof(evidence_json)='object')
);
create index if not exists idx_a0r1_security_evidence on intake_security_evidence(tenant_id,event_type,created_at desc);

-- RLS: registrations and Intake authority evidence are tenant-scoped independently of UI filtering.
alter table workload_source_registrations enable row level security;
alter table intake_records enable row level security;
alter table intake_registration_rate_buckets enable row level security;
alter table intake_disposition_history enable row level security;
alter table ingestion_idempotency_records enable row level security;
alter table intake_security_evidence enable row level security;

do $$ declare r text; begin
  foreach r in array array['workload_source_registrations','intake_records','intake_registration_rate_buckets','intake_disposition_history','ingestion_idempotency_records','intake_security_evidence'] loop
    execute format('drop policy if exists tenant_isolation on %I',r);
    execute format('create policy tenant_isolation on %I using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id())',r);
  end loop;
end $$;

-- Immutable provenance. Only disposition/materialization/result bookkeeping may evolve.
create or replace function a0r1_protect_intake_provenance() returns trigger language plpgsql as $$
begin
  if old.tenant_id is distinct from new.tenant_id
     or old.ingestion_id is distinct from new.ingestion_id
     or old.source_registration_id is distinct from new.source_registration_id
     or old.source_system_id is distinct from new.source_system_id
     or old.channel_type is distinct from new.channel_type
     or old.source_event_id is distinct from new.source_event_id
     or old.idempotency_key is distinct from new.idempotency_key
     or old.payload_digest is distinct from new.payload_digest
     or old.digest_algorithm is distinct from new.digest_algorithm
     or old.canonicalization_version is distinct from new.canonicalization_version
     or old.envelope_metadata_json is distinct from new.envelope_metadata_json
     or old.authenticated_principal_type is distinct from new.authenticated_principal_type
     or old.authenticated_principal_ref is distinct from new.authenticated_principal_ref
     or old.origin_principal_type is distinct from new.origin_principal_type
     or old.origin_principal_ref is distinct from new.origin_principal_ref
     or old.asserted_origin_principal is distinct from new.asserted_origin_principal
     or old.owner_department_id is distinct from new.owner_department_id
     or old.owner_group_id is distinct from new.owner_group_id
     or old.created_at is distinct from new.created_at then
    raise exception 'A0R1_INTAKE_PROVENANCE_IMMUTABLE';
  end if;
  return new;
end $$;
drop trigger if exists trg_a0r1_intake_provenance on intake_records;
create trigger trg_a0r1_intake_provenance before update on intake_records for each row execute function a0r1_protect_intake_provenance();

comment on table workload_source_registrations is 'A0-R1 governed ingress contracts. SourceSystem remains the master; each source may have multiple registrations.';
comment on table intake_records is 'A0-R1 authoritative admission record. Intake disposition is not Task outcome.';
comment on table intake_registration_rate_buckets is 'A0-R1 fixed-window counters for registration rate/quota admission; never a Task authority.';
comment on column intake_records.asserted_origin_principal is 'Caller declaration only; never authorization authority.';
comment on table ingestion_idempotency_records is 'A0-R1 source-registration scoped idempotency generations. Same key/different digest is a quarantine event.';

-- Flyway INSTANCE governance context for FORCE-RLS IAM/catalog DML below.
-- set_config(..., true) is transaction-local; earlier tenant-data backfills remain context-neutral.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','a0-r1-source-registration-intake-migration',true);

-- A0-R1 Human Admin entry-point authority. Registration remains a child resource of SourceSystem.
insert into permission_entry_point_inventory(
 entry_point_id,entry_point_type,application_id,owner_module,display_name,route_pattern,http_method,authority_state,
 target_permission_code,legacy_authority_type,legacy_authorities,resource_type,resource_resolver_id,exemption_reason,
 migration_deadline,manifest_revision,source_ref,source_hash,last_verified_at,created_by,updated_by)
values
('REST:GET:/admin/source-systems/{sourceSystemId}/registrations','REST','control-plane-app','control-plane-app','WorkloadSourceRegistrationController.list','/admin/source-systems/{sourceSystemId}/registrations','GET','TARGET_ONLY','admin.source.system.detail',null,'[]'::jsonb,'SOURCE_SYSTEM','R3_PATH_RESOURCE_RESOLVER',null,null,'a0-r1-source-registration-intake-authority-2026-08-24','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/WorkloadSourceRegistrationController.java#list','dd0df4566bf5e9188bf8b8014aa77abf1e305b2be07ae4f2f6d34cbf2e07133f',now(),'a0-r1-source-registration-intake-authority','a0-r1-source-registration-intake-authority'),
('REST:POST:/admin/source-systems/{sourceSystemId}/registrations','REST','control-plane-app','control-plane-app','WorkloadSourceRegistrationController.create','/admin/source-systems/{sourceSystemId}/registrations','POST','TARGET_ONLY','admin.source.system.update',null,'[]'::jsonb,'SOURCE_SYSTEM','R3_PATH_RESOURCE_RESOLVER',null,null,'a0-r1-source-registration-intake-authority-2026-08-24','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/WorkloadSourceRegistrationController.java#create','dd0df4566bf5e9188bf8b8014aa77abf1e305b2be07ae4f2f6d34cbf2e07133f',now(),'a0-r1-source-registration-intake-authority','a0-r1-source-registration-intake-authority'),
('REST:PUT:/admin/source-systems/{sourceSystemId}/registrations/{registrationId}','REST','control-plane-app','control-plane-app','WorkloadSourceRegistrationController.update','/admin/source-systems/{sourceSystemId}/registrations/{registrationId}','PUT','TARGET_ONLY','admin.source.system.update',null,'[]'::jsonb,'SOURCE_SYSTEM','R3_PATH_RESOURCE_RESOLVER',null,null,'a0-r1-source-registration-intake-authority-2026-08-24','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/WorkloadSourceRegistrationController.java#update','dd0df4566bf5e9188bf8b8014aa77abf1e305b2be07ae4f2f6d34cbf2e07133f',now(),'a0-r1-source-registration-intake-authority','a0-r1-source-registration-intake-authority'),
('REST:DELETE:/admin/source-systems/{sourceSystemId}/registrations/{registrationId}','REST','control-plane-app','control-plane-app','WorkloadSourceRegistrationController.retire','/admin/source-systems/{sourceSystemId}/registrations/{registrationId}','DELETE','TARGET_ONLY','admin.source.system.update',null,'[]'::jsonb,'SOURCE_SYSTEM','R3_PATH_RESOURCE_RESOLVER',null,null,'a0-r1-source-registration-intake-authority-2026-08-24','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/WorkloadSourceRegistrationController.java#retire','dd0df4566bf5e9188bf8b8014aa77abf1e305b2be07ae4f2f6d34cbf2e07133f',now(),'a0-r1-source-registration-intake-authority','a0-r1-source-registration-intake-authority')
on conflict(entry_point_id) do update set
 display_name=excluded.display_name,route_pattern=excluded.route_pattern,http_method=excluded.http_method,
 authority_state=excluded.authority_state,target_permission_code=excluded.target_permission_code,
 legacy_authority_type=null,legacy_authorities='[]'::jsonb,resource_type=excluded.resource_type,
 resource_resolver_id=excluded.resource_resolver_id,exemption_reason=null,migration_deadline=null,
 manifest_revision=excluded.manifest_revision,source_ref=excluded.source_ref,source_hash=excluded.source_hash,
 last_verified_at=now(),updated_at=now(),updated_by='a0-r1-source-registration-intake-authority',
 version=permission_entry_point_inventory.version+1;

update rbac_policy_versions
set policy_version=policy_version+1,updated_at=now(),updated_by='a0-r1-source-registration-intake-authority';

