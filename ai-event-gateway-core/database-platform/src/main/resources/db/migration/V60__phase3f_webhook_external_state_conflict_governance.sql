-- Phase 3F: durable Provider Webhook Inbox, external observation and conflict governance.
alter table integration_inbox add column if not exists external_issue_key varchar(255);
alter table integration_inbox add column if not exists nonce varchar(255);
alter table integration_inbox add column if not exists provider_timestamp timestamptz;
alter table integration_inbox add column if not exists timestamp_verified boolean not null default false;
alter table integration_inbox add column if not exists nonce_accepted boolean not null default false;
alter table integration_inbox add column if not exists tenant_bound boolean not null default false;
alter table integration_inbox add column if not exists connection_bound boolean not null default false;
alter table integration_inbox add column if not exists retry_count integer not null default 0;
alter table integration_inbox add column if not exists next_retry_at timestamptz;
alter table integration_inbox add column if not exists row_version bigint not null default 1;
update integration_inbox set timestamp_verified=signature_verified,tenant_bound=true,connection_bound=true,row_version=greatest(row_version,1) where provider_timestamp is null;
alter table integration_inbox drop constraint if exists ck_integration_inbox_status;
alter table integration_inbox add constraint ck_integration_inbox_status check(status in ('RECEIVED','VERIFIED','PROCESSING','PROCESSED','FAILED_RETRYABLE','REPLAYED','REJECTED','FAILED','DEAD_LETTER'));
create unique index if not exists uk_provider_webhook_nonce_p3f on integration_inbox(tenant_id,connection_id,nonce) where nonce is not null;
create index if not exists idx_provider_webhook_due_p3f on integration_inbox(status,next_retry_at) where status='FAILED_RETRYABLE';

create table if not exists external_issue_observations (
 tenant_id varchar(64) not null, observation_id varchar(128) not null, inbox_id varchar(128) not null,
 connection_id varchar(128) not null, provider_type varchar(32) not null, external_project_id varchar(255),
 external_issue_id varchar(255) not null, external_issue_key varchar(255), external_issue_status varchar(128),
 observed_document_json jsonb not null default '{}'::jsonb, observed_document_hash varchar(128) not null,
 provider_identity_hash varchar(128) not null, mapping_schema_hash varchar(128), provider_observed_at timestamptz,
 created_at timestamptz not null default now(), correlation_id varchar(128) not null,
 primary key(tenant_id,observation_id), unique(tenant_id,inbox_id)
);
create index if not exists idx_external_observation_issue_p3f on external_issue_observations(tenant_id,connection_id,external_issue_id,created_at desc);

create table if not exists external_issue_observed_states (
 tenant_id varchar(64) not null, state_id varchar(128) not null, connection_id varchar(128) not null,
 external_project_id varchar(255), external_issue_id varchar(255) not null, external_issue_key varchar(255),
 task_issue_link_id varchar(128), projection_id varchar(128), latest_observation_id varchar(128) not null,
 desired_document_hash varchar(128), observed_document_hash varchar(128) not null, diff_json jsonb not null default '{}'::jsonb,
 diff_hash varchar(128) not null, provider_identity_hash varchar(128) not null, mapping_schema_hash varchar(128),
 conflict_classification varchar(64) not null default 'NONE', observed_at timestamptz, row_version bigint not null default 1,
 updated_at timestamptz not null default now(), primary key(tenant_id,state_id),
 unique(tenant_id,connection_id,external_issue_id),
 constraint ck_external_observed_conflict_p3f check(conflict_classification in ('NONE','EXTERNAL_FIELD_CHANGED','EXTERNAL_ISSUE_DELETED','PERMISSION_REVOKED','MAPPING_SCHEMA_DRIFT','DUPLICATE_EXTERNAL_ISSUE','PROVIDER_IDENTITY_CHANGED'))
);
create index if not exists idx_external_observed_conflict_p3f on external_issue_observed_states(tenant_id,conflict_classification,updated_at desc);

create table if not exists external_issue_conflicts (
 tenant_id varchar(64) not null, conflict_id varchar(128) not null, connection_id varchar(128) not null,
 external_project_id varchar(255), external_issue_id varchar(255) not null, task_issue_link_id varchar(128), projection_id varchar(128),
 observation_id varchar(128) not null, classification varchar(64) not null, resolution_policy varchar(32) not null,
 status varchar(32) not null default 'OPEN', desired_document_hash varchar(128), observed_document_hash varchar(128),
 state_diff_json jsonb not null default '{}'::jsonb, evidence_hash varchar(128) not null, reason_code varchar(128) not null,
 detected_at timestamptz not null default now(), resolved_at timestamptz, resolved_by varchar(128), resolution_reason text,
 row_version bigint not null default 1, correlation_id varchar(128) not null, primary key(tenant_id,conflict_id),
 constraint ck_external_conflict_class_p3f check(classification in ('EXTERNAL_FIELD_CHANGED','EXTERNAL_ISSUE_DELETED','PERMISSION_REVOKED','MAPPING_SCHEMA_DRIFT','DUPLICATE_EXTERNAL_ISSUE','PROVIDER_IDENTITY_CHANGED')),
 constraint ck_external_conflict_policy_p3f check(resolution_policy in ('OPENDISPATCH_WINS','PROVIDER_WINS','MERGE','MANUAL_REVIEW','IGNORE')),
 constraint ck_external_conflict_status_p3f check(status in ('OPEN','ACKNOWLEDGED','RESOLVED','IGNORED','WAIT_HUMAN'))
);
create index if not exists idx_external_conflict_ops_p3f on external_issue_conflicts(tenant_id,status,classification,detected_at desc);

create table if not exists provider_webhook_replay_evidence (
 tenant_id varchar(64) not null, evidence_id varchar(128) not null, inbox_id varchar(128) not null,
 event_type varchar(64) not null, decision varchar(32) not null, reason_code varchar(128) not null,
 evidence_hash varchar(128) not null, actor_id varchar(128), occurred_at timestamptz not null default now(),
 correlation_id varchar(128) not null, primary key(tenant_id,evidence_id)
);
create index if not exists idx_webhook_replay_evidence_p3f on provider_webhook_replay_evidence(tenant_id,inbox_id,occurred_at desc);

create or replace function phase3f_append_only_guard() returns trigger language plpgsql as $$ begin raise exception 'PHASE3F_APPEND_ONLY_EVIDENCE'; end $$;
drop trigger if exists trg_phase3f_observation_immutable on external_issue_observations;
create trigger trg_phase3f_observation_immutable before update or delete on external_issue_observations for each row execute function phase3f_append_only_guard();
drop trigger if exists trg_phase3f_replay_evidence_immutable on provider_webhook_replay_evidence;
create trigger trg_phase3f_replay_evidence_immutable before update or delete on provider_webhook_replay_evidence for each row execute function phase3f_append_only_guard();

create or replace function phase3f_inbox_binding_guard() returns trigger language plpgsql as $$
begin
 if old.tenant_id is distinct from new.tenant_id or old.inbox_id is distinct from new.inbox_id or
    old.connection_id is distinct from new.connection_id or old.provider_event_id is distinct from new.provider_event_id or
    old.payload_hash is distinct from new.payload_hash or old.nonce is distinct from new.nonce then
   raise exception 'PHASE3F_WEBHOOK_BINDING_IMMUTABLE';
 end if;
 if new.row_version <> old.row_version + 1 then raise exception 'PHASE3F_WEBHOOK_VERSION_CONFLICT'; end if;
 return new;
end $$;
drop trigger if exists trg_phase3f_inbox_binding_guard on integration_inbox;
create trigger trg_phase3f_inbox_binding_guard before update on integration_inbox for each row execute function phase3f_inbox_binding_guard();
