-- Phase 3I: ordered projection lanes, response-loss recovery, reconciliation and provider backpressure.
create table if not exists integration_projection_lanes (
 tenant_id varchar(64) not null,lane_id varchar(128) not null,aggregate_key varchar(512) not null,lane_type varchar(32) not null,
 connection_id varchar(128) not null,project_mapping_id varchar(128),status varchar(32) not null,next_sequence bigint not null default 1,
 active_generation bigint not null default 1,max_queue_depth integer not null default 1000,queued_count integer not null default 0,
 in_flight_count integer not null default 0,row_version bigint not null default 1,created_at timestamptz not null,updated_at timestamptz not null,
 correlation_id varchar(128),primary key(tenant_id,lane_id),unique(tenant_id,aggregate_key,lane_type),
 foreign key(tenant_id,connection_id) references integration_connections(tenant_id,connection_id),
 check(lane_type in('TASK','EXTERNAL_ISSUE','COMMENT','RELATION','TOPOLOGY')),
 check(status in('ACTIVE','THROTTLED','SATURATED','OPEN_CIRCUIT','RECOVERING','DISABLED')),
 check(next_sequence>0 and active_generation>0 and max_queue_depth>0 and queued_count>=0 and in_flight_count>=0 and row_version>0)
);
create table if not exists integration_projection_lane_work (
 tenant_id varchar(64) not null,work_id varchar(128) not null,lane_id varchar(128) not null,lane_sequence bigint not null,generation bigint not null,
 operation_type varchar(32) not null,status varchar(40) not null,aggregate_id varchar(160) not null,outbox_id varchar(128),projection_id varchar(160),
 payload_hash varchar(128) not null,coalesce_key varchar(255),depends_on_work_id varchar(128),superseded_by_work_id varchar(128),
 external_idempotency_marker varchar(255) not null,attempt_count integer not null default 0,max_attempts integer not null default 8,
 claim_owner varchar(160),claim_token_hash varchar(128),claim_until timestamptz,next_attempt_at timestamptz,last_error_code varchar(128),
 last_error_message text,row_version bigint not null default 1,created_at timestamptz not null,updated_at timestamptz not null,correlation_id varchar(128),
 primary key(tenant_id,work_id),unique(tenant_id,lane_id,generation,lane_sequence),unique(tenant_id,external_idempotency_marker),
 foreign key(tenant_id,lane_id) references integration_projection_lanes(tenant_id,lane_id),
 foreign key(tenant_id,depends_on_work_id) references integration_projection_lane_work(tenant_id,work_id),
 foreign key(tenant_id,superseded_by_work_id) references integration_projection_lane_work(tenant_id,work_id),
 check(operation_type in('CREATE','UPDATE','CLOSE','COMMENT','RELATION','RECONCILE','REPAIR')),
 check(status in('PENDING','READY','BLOCKED','CLAIMED','IN_PROGRESS','VERIFYING','ACKNOWLEDGED','RETRY_WAITING','RECOVERY_WAITING_INDEX','DEAD_LETTER','SUPERSEDED','CANCELLED')),
 check(lane_sequence>0 and generation>0 and attempt_count>=0 and max_attempts>0 and row_version>0),
 check((claim_owner is null and claim_token_hash is null and claim_until is null) or (claim_owner is not null and claim_token_hash is not null and claim_until is not null))
);
create table if not exists integration_projection_recovery_cases (
 tenant_id varchar(64) not null,case_id varchar(128) not null,case_type varchar(48) not null,status varchar(40) not null,
 lane_id varchar(128),work_id varchar(128),projection_id varchar(160),connection_id varchar(128) not null,project_mapping_id varchar(128),
 external_idempotency_marker varchar(255),external_issue_id varchar(255),expected_hash varchar(128),observed_hash varchar(128),
 reason_code varchar(128) not null,safe_summary text,attempt_count integer not null default 0,next_attempt_at timestamptz,row_version bigint not null default 1,
 created_at timestamptz not null,updated_at timestamptz not null,correlation_id varchar(128),primary key(tenant_id,case_id),
 foreign key(tenant_id,lane_id) references integration_projection_lanes(tenant_id,lane_id),
 foreign key(tenant_id,work_id) references integration_projection_lane_work(tenant_id,work_id),
 foreign key(tenant_id,connection_id) references integration_connections(tenant_id,connection_id),
 check(case_type in('RESPONSE_LOST','PROVIDER_INDEX_DELAY','DUPLICATE_ISSUE','MISSING_LINK','STATE_DRIFT','DUPLICATE_PROVIDER_OBJECT','PERMISSION_DRIFT','MAPPING_DRIFT')),
 check(status in('OPEN','INVESTIGATING','REPAIR_PENDING','VERIFYING','RESOLVED','FAILED_RETRYABLE','DEAD_LETTER','DECISION_REQUIRED','SUPERSEDED')),
 check(attempt_count>=0 and row_version>0)
);
create table if not exists integration_projection_reconciliation_runs (
 tenant_id varchar(64) not null,run_id varchar(128) not null,connection_id varchar(128) not null,project_mapping_id varchar(128),scope_type varchar(48) not null,
 scope_id varchar(160),status varchar(32) not null,dry_run boolean not null default true,scanned_count integer not null default 0,drift_count integer not null default 0,
 repaired_count integer not null default 0,failed_count integer not null default 0,row_version bigint not null default 1,created_at timestamptz not null,
 updated_at timestamptz not null,requested_by varchar(128) not null,correlation_id varchar(128),primary key(tenant_id,run_id),
 foreign key(tenant_id,connection_id) references integration_connections(tenant_id,connection_id),
 check(status in('REQUESTED','SCANNING','REPAIRING','PARTIAL','COMPLETED','FAILED','CANCELLED')),
 check(scope_type in('CONNECTION','PROJECT','TASK','PROJECTION','TOPOLOGY')),
 check(scanned_count>=0 and drift_count>=0 and repaired_count>=0 and failed_count>=0 and row_version>0)
);
create table if not exists integration_provider_health_state (
 tenant_id varchar(64) not null,health_id varchar(128) not null,connection_id varchar(128) not null,project_mapping_id varchar(128),status varchar(32) not null,
 consecutive_failures integer not null default 0,success_count integer not null default 0,failure_count integer not null default 0,queue_depth integer not null default 0,
 max_queue_depth integer not null default 1000,opened_at timestamptz,retry_after_at timestamptz,last_failure_code varchar(128),row_version bigint not null default 1,
 created_at timestamptz not null,updated_at timestamptz not null,correlation_id varchar(128),primary key(tenant_id,health_id),
 unique nulls not distinct(tenant_id,connection_id,project_mapping_id),foreign key(tenant_id,connection_id) references integration_connections(tenant_id,connection_id),
 check(status in('HEALTHY','THROTTLED','SATURATED','OPEN_CIRCUIT','RECOVERING','DISABLED')),
 check(consecutive_failures>=0 and success_count>=0 and failure_count>=0 and queue_depth>=0 and max_queue_depth>0 and row_version>0)
);
create table if not exists integration_projection_recovery_events (
 tenant_id varchar(64) not null,event_id varchar(128) not null,aggregate_type varchar(48) not null,aggregate_id varchar(128) not null,event_type varchar(64) not null,
 actor_id varchar(128),reason_code varchar(128) not null,metadata_json jsonb not null default '{}'::jsonb,previous_event_hash varchar(64),event_hash varchar(64) not null,
 occurred_at timestamptz not null,correlation_id varchar(128),primary key(tenant_id,event_id),unique(tenant_id,event_hash)
);
alter table issue_projection_outbox_reliability add column if not exists lane_id varchar(128);
alter table issue_projection_outbox_reliability add column if not exists lane_sequence bigint;
alter table issue_projection_outbox_reliability add column if not exists generation bigint not null default 1;
alter table issue_projection_outbox_reliability add column if not exists superseded_by_work_id varchar(128);
update issue_projection_outbox_reliability set lane_id=projection_id where lane_id is null;
update issue_projection_outbox_reliability set lane_sequence=operation_sequence where lane_sequence is null;
alter table issue_projection_outbox_reliability alter column lane_id set not null;
alter table issue_projection_outbox_reliability alter column lane_sequence set not null;
create index if not exists idx_projection_outbox_lane_order_p3i on issue_projection_outbox_reliability(tenant_id,lane_id,generation,lane_sequence,status);
create index if not exists idx_projection_lane_status_p3i on integration_projection_lanes(tenant_id,status,updated_at desc);
create index if not exists idx_projection_work_due_p3i on integration_projection_lane_work(tenant_id,status,next_attempt_at,lane_id,generation,lane_sequence) where status in('READY','BLOCKED','RETRY_WAITING','RECOVERY_WAITING_INDEX');
create index if not exists idx_projection_work_claim_p3i on integration_projection_lane_work(tenant_id,claim_until) where status in('CLAIMED','IN_PROGRESS','VERIFYING');
create index if not exists idx_projection_work_coalesce_p3i on integration_projection_lane_work(tenant_id,lane_id,generation,coalesce_key,updated_at desc) where status in('PENDING','READY','BLOCKED','RETRY_WAITING');
create unique index if not exists uq_projection_open_recovery_p3i on integration_projection_recovery_cases(tenant_id,work_id,case_type) where status not in('RESOLVED','DEAD_LETTER','SUPERSEDED');
create index if not exists idx_projection_recovery_due_p3i on integration_projection_recovery_cases(tenant_id,status,next_attempt_at) where status in('FAILED_RETRYABLE','REPAIR_PENDING','VERIFYING');
create index if not exists idx_reconciliation_runs_p3i on integration_projection_reconciliation_runs(tenant_id,status,updated_at desc);
create index if not exists idx_provider_health_p3i on integration_provider_health_state(tenant_id,status,retry_after_at,updated_at);
create index if not exists idx_projection_recovery_events_p3i on integration_projection_recovery_events(tenant_id,aggregate_type,aggregate_id,occurred_at desc);
create or replace function phase3i_projection_lane_identity_guard() returns trigger language plpgsql as $$ begin if old.tenant_id<>new.tenant_id or old.lane_id<>new.lane_id or old.aggregate_key<>new.aggregate_key or old.lane_type<>new.lane_type or old.connection_id<>new.connection_id then raise exception 'PHASE3I_PROJECTION_LANE_IDENTITY_IMMUTABLE'; end if;if new.row_version<>old.row_version+1 then raise exception 'PHASE3I_PROJECTION_LANE_VERSION_CONFLICT';end if;if new.next_sequence<old.next_sequence or new.active_generation<old.active_generation then raise exception 'PHASE3I_PROJECTION_LANE_SEQUENCE_CANNOT_DECREASE';end if;return new;end $$;
drop trigger if exists trg_projection_lane_identity_p3i on integration_projection_lanes;create trigger trg_projection_lane_identity_p3i before update on integration_projection_lanes for each row execute function phase3i_projection_lane_identity_guard();
create or replace function phase3i_projection_work_identity_guard() returns trigger language plpgsql as $$ begin if old.tenant_id<>new.tenant_id or old.work_id<>new.work_id or old.lane_id<>new.lane_id or old.lane_sequence<>new.lane_sequence or old.generation<>new.generation or old.operation_type<>new.operation_type or old.external_idempotency_marker<>new.external_idempotency_marker then raise exception 'PHASE3I_PROJECTION_WORK_IDENTITY_IMMUTABLE';end if;if new.row_version<>old.row_version+1 then raise exception 'PHASE3I_PROJECTION_WORK_VERSION_CONFLICT';end if;return new;end $$;
drop trigger if exists trg_projection_work_identity_p3i on integration_projection_lane_work;create trigger trg_projection_work_identity_p3i before update on integration_projection_lane_work for each row execute function phase3i_projection_work_identity_guard();
create or replace function phase3i_projection_recovery_event_guard() returns trigger language plpgsql as $$ begin raise exception 'PHASE3I_PROJECTION_RECOVERY_EVENTS_APPEND_ONLY';end $$;
drop trigger if exists trg_projection_recovery_event_p3i on integration_projection_recovery_events;create trigger trg_projection_recovery_event_p3i before update or delete on integration_projection_recovery_events for each row execute function phase3i_projection_recovery_event_guard();
comment on table integration_projection_lanes is 'Ordered lane authority. A lane sequence prevents CLOSE before CREATE and Comment/Relation before the external Issue exists.';
comment on column integration_projection_lane_work.generation is 'Superseding generation. Older non-terminal work may be retained as immutable SUPERSEDED evidence.';
comment on table integration_provider_health_state is 'Connection/project circuit breaker and bounded queue state; Task/A2A authority remains outside Issue Tracking.';

insert into permission_point_catalog(permission_point,resource_type,action_code,description,risk_level) values
 ('integration.projection_recovery.read','PROJECTION_RECOVERY','READ','Read ordered projection lanes, recovery cases and provider health.','MEDIUM'),
 ('integration.projection_recovery.manage','PROJECTION_RECOVERY','MANAGE','Manage projection generations and governed recovery work.','HIGH'),
 ('integration.projection_recovery.reconcile','PROJECTION_RECONCILIATION','RECONCILE','Start provider reconciliation and drift detection.','HIGH'),
 ('integration.projection_recovery.repair','PROJECTION_RECOVERY','REPAIR','Execute a controlled provider repair after readback evidence.','CRITICAL')
on conflict(permission_point) do nothing;
insert into reason_code_catalog(reason_code,http_status,category,retryable,message_template) values
 ('PROJECTION_LANE_SATURATED',429,'INTEGRATION',true,'Projection lane queue reached its configured bound.'),
 ('PROJECTION_LANE_VERSION_CONFLICT',409,'INTEGRATION',true,'Projection lane version changed.'),
 ('PROJECTION_WORK_VERSION_CONFLICT',409,'INTEGRATION',true,'Projection work version changed.'),
 ('PROJECTION_WORK_CLAIM_TOKEN_INVALID',409,'INTEGRATION',false,'Projection work claim token is invalid.'),
 ('PROJECTION_WORK_CLAIM_EXPIRED',409,'INTEGRATION',true,'Projection work claim lease expired.'),
 ('RECONCILIATION_RUN_VERSION_CONFLICT',409,'INTEGRATION',true,'Reconciliation run version changed.'),
 ('RECOVERY_CASE_VERSION_CONFLICT',409,'INTEGRATION',true,'Projection recovery case version changed.'),
 ('PROVIDER_INDEX_DELAY',503,'INTEGRATION',true,'Provider object may exist but is not yet visible in the provider index.'),
 ('DUPLICATE_OR_CONFLICTING_PROVIDER_OBJECT',409,'INTEGRATION',false,'Multiple provider objects match the projection marker.'),
 ('PROJECTION_RECOVERY_GATEWAY_UNAVAILABLE',503,'INTEGRATION',true,'Scoped provider recovery gateway is unavailable.')
on conflict(reason_code) do nothing;
