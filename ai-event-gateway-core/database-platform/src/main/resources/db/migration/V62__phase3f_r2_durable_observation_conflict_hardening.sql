-- Phase 3F-R2: durable three-stage observation pipeline, claim leases, ordered state and conflict ledger.

alter table integration_inbox add column if not exists claim_owner varchar(128);
alter table integration_inbox add column if not exists claim_token_hash varchar(128);
alter table integration_inbox add column if not exists claimed_at timestamptz;
alter table integration_inbox add column if not exists lease_until timestamptz;
alter table integration_inbox add column if not exists processing_attempt_id varchar(128);
update integration_inbox set status='PROCESSED' where status='REPLAYED';
update integration_inbox set status='DEAD_LETTER' where status='FAILED';
alter table integration_inbox drop constraint if exists ck_integration_inbox_status;
alter table integration_inbox add constraint ck_integration_inbox_status check(status in (
 'RECEIVED','VERIFIED','PROCESSING','PROCESSED','FAILED_RETRYABLE','STALE_CLAIM','REJECTED','DEAD_LETTER'
));
alter table integration_inbox drop constraint if exists ck_integration_inbox_claim_p3fr2;
alter table integration_inbox add constraint ck_integration_inbox_claim_p3fr2 check(
 (status <> 'PROCESSING') or (claim_owner is not null and claim_token_hash is not null and claimed_at is not null and lease_until is not null and processing_attempt_id is not null)
);
create index if not exists idx_provider_webhook_claim_due_p3fr2 on integration_inbox(status,next_retry_at,lease_until,received_at)
 where status in ('VERIFIED','FAILED_RETRYABLE','STALE_CLAIM','PROCESSING');

alter table external_issue_observations add column if not exists provider_event_id varchar(255);
alter table external_issue_observations add column if not exists provider_event_sequence bigint;
alter table external_issue_observations add column if not exists provider_change_version varchar(255);
alter table external_issue_observations add column if not exists raw_observed_document_json jsonb;
alter table external_issue_observations add column if not exists normalized_observation_json jsonb;
alter table external_issue_observations add column if not exists normalization_profile_version varchar(64);
update external_issue_observations o
 set provider_event_id=coalesce(o.provider_event_id,i.provider_event_id)
 from integration_inbox i
 where o.tenant_id=i.tenant_id and o.inbox_id=i.inbox_id and o.provider_event_id is null;
update external_issue_observations
 set raw_observed_document_json=coalesce(raw_observed_document_json,observed_document_json),
     normalized_observation_json=coalesce(normalized_observation_json,observed_document_json),
     normalization_profile_version=coalesce(normalization_profile_version,'phase3f-r2-v1')
 where raw_observed_document_json is null or normalized_observation_json is null or normalization_profile_version is null;
alter table external_issue_observations alter column provider_event_id set not null;
alter table external_issue_observations alter column raw_observed_document_json set not null;
alter table external_issue_observations alter column normalized_observation_json set not null;
alter table external_issue_observations alter column normalization_profile_version set not null;
create index if not exists idx_external_observation_order_p3fr2 on external_issue_observations(
 tenant_id,connection_id,external_issue_id,provider_event_sequence desc nulls last,provider_observed_at desc,created_at desc
);

alter table external_issue_observed_states add column if not exists last_applied_provider_event_id varchar(255);
alter table external_issue_observed_states add column if not exists provider_event_sequence bigint;
alter table external_issue_observed_states add column if not exists provider_change_version varchar(255);
alter table external_issue_observed_states add column if not exists last_applied_event_at timestamptz;
alter table external_issue_observed_states add column if not exists normalized_observation_json jsonb;
update external_issue_observed_states
 set normalized_observation_json=coalesce(normalized_observation_json,'{}'::jsonb),
     last_applied_event_at=coalesce(last_applied_event_at,observed_at,updated_at)
 where normalized_observation_json is null or last_applied_event_at is null;
alter table external_issue_observed_states alter column normalized_observation_json set not null;
alter table external_issue_observed_states drop constraint if exists ck_external_observed_conflict_p3f;
alter table external_issue_observed_states add constraint ck_external_observed_conflict_p3f check(conflict_classification in (
 'NONE','EXTERNAL_FIELD_CHANGED','EXTERNAL_ISSUE_DELETED','PERMISSION_REVOKED','MAPPING_SCHEMA_DRIFT',
 'DUPLICATE_EXTERNAL_ISSUE','PROVIDER_IDENTITY_CHANGED','STALE_OR_OUT_OF_ORDER_EVENT'
));

alter table external_issue_conflicts add column if not exists decision_at timestamptz;
alter table external_issue_conflicts add column if not exists resolution_idempotency_key varchar(255);
alter table external_issue_conflicts add column if not exists resolution_request_hash varchar(128);
alter table external_issue_conflicts drop constraint if exists ck_external_conflict_class_p3f;
alter table external_issue_conflicts add constraint ck_external_conflict_class_p3f check(classification in (
 'EXTERNAL_FIELD_CHANGED','EXTERNAL_ISSUE_DELETED','PERMISSION_REVOKED','MAPPING_SCHEMA_DRIFT',
 'DUPLICATE_EXTERNAL_ISSUE','PROVIDER_IDENTITY_CHANGED','STALE_OR_OUT_OF_ORDER_EVENT'
));
alter table external_issue_conflicts drop constraint if exists ck_external_conflict_status_p3f;
alter table external_issue_conflicts add constraint ck_external_conflict_status_p3f check(status in (
 'OPEN','INVESTIGATING','WAIT_HUMAN','RESOLUTION_PENDING','VERIFYING','RESOLVED','IGNORED','SUPERSEDED','REOPENED'
));
alter table external_issue_conflicts drop constraint if exists ck_external_conflict_resolution_p3fr2;
alter table external_issue_conflicts add constraint ck_external_conflict_resolution_p3fr2 check(
 (status not in ('RESOLVED','IGNORED')) or
 (decision_at is not null and resolved_at is not null and resolved_by is not null and resolution_reason is not null and resolution_idempotency_key is not null)
);
create unique index if not exists uk_external_conflict_resolution_idem_p3fr2
 on external_issue_conflicts(tenant_id,resolution_idempotency_key) where resolution_idempotency_key is not null;

create table if not exists external_issue_conflict_events (
 tenant_id varchar(64) not null,
 event_id varchar(128) not null,
 conflict_id varchar(128) not null,
 event_type varchar(64) not null,
 actor_id varchar(128),
 reason_code varchar(128) not null,
 metadata_json jsonb not null default '{}'::jsonb,
 previous_event_hash varchar(128),
 event_hash varchar(128) not null,
 occurred_at timestamptz not null default now(),
 correlation_id varchar(128) not null,
 primary key(tenant_id,event_id),
 constraint fk_external_conflict_event_p3fr2 foreign key(tenant_id,conflict_id)
  references external_issue_conflicts(tenant_id,conflict_id),
 constraint ck_external_conflict_event_type_p3fr2 check(event_type in (
  'CONFLICT_CREATED','INVESTIGATION_STARTED','POLICY_SELECTED','RESOLUTION_STARTED','RESOLUTION_FAILED',
  'VERIFICATION_STARTED','VERIFICATION_PASSED','VERIFICATION_FAILED','WAIT_HUMAN','IGNORED','RESOLVED','REOPENED','SUPERSEDED'
 ))
);
create index if not exists idx_external_conflict_event_timeline_p3fr2
 on external_issue_conflict_events(tenant_id,conflict_id,occurred_at desc,event_id desc);

drop trigger if exists trg_phase3f_conflict_event_immutable on external_issue_conflict_events;
create trigger trg_phase3f_conflict_event_immutable before update or delete on external_issue_conflict_events
 for each row execute function phase3f_append_only_guard();

-- Observation and conflict identity references remain durable and auditable.
do $$ begin
 if not exists(select 1 from pg_constraint where conname='fk_external_observation_inbox_p3fr2') then
  alter table external_issue_observations add constraint fk_external_observation_inbox_p3fr2
   foreign key(tenant_id,inbox_id) references integration_inbox(tenant_id,inbox_id);
 end if;
 if not exists(select 1 from pg_constraint where conname='fk_external_state_observation_p3fr2') then
  alter table external_issue_observed_states add constraint fk_external_state_observation_p3fr2
   foreign key(tenant_id,latest_observation_id) references external_issue_observations(tenant_id,observation_id);
 end if;
 if not exists(select 1 from pg_constraint where conname='fk_external_conflict_observation_p3fr2') then
  alter table external_issue_conflicts add constraint fk_external_conflict_observation_p3fr2
   foreign key(tenant_id,observation_id) references external_issue_observations(tenant_id,observation_id);
 end if;
end $$;
