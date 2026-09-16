-- Phase 3G: provider-neutral external change policy, bidirectional comment/link sync and Human Action Candidate governance.

create table if not exists integration_external_change_policies (
 tenant_id varchar(64) not null,
 policy_id varchar(128) not null,
 policy_name varchar(255) not null,
 connection_id varchar(128),
 project_mapping_id varchar(128),
 external_project_id varchar(255),
 issue_type varchar(128),
 resource_type varchar(32) not null,
 field_path varchar(512),
 direction varchar(40) not null,
 decision_action varchar(32) not null,
 risk_level varchar(16) not null default 'UNKNOWN',
 requires_reauthentication boolean not null default false,
 requires_approval boolean not null default false,
 allow_provider_mutation boolean not null default false,
 priority integer not null default 1000,
 status varchar(32) not null default 'DRAFT',
 row_version bigint not null default 1,
 effective_from timestamptz,
 expires_at timestamptz,
 updated_at timestamptz not null default now(),
 primary key(tenant_id,policy_id),
 constraint fk_external_change_policy_connection_p3g foreign key(tenant_id,connection_id) references integration_connections(tenant_id,connection_id),
 constraint fk_external_change_policy_mapping_p3g foreign key(tenant_id,project_mapping_id) references integration_project_mappings(tenant_id,mapping_id),
 constraint ck_external_change_resource_p3g check(resource_type in ('COMMENT','LINK','STATUS','ASSIGNEE','PRIORITY','LABEL','APPROVAL','ATTACHMENT','ISSUE_FIELD','ISSUE_DELETE','PROJECT_MOVE','UNKNOWN')),
 constraint ck_external_change_direction_p3g check(direction in ('PROVIDER_TO_OPENDISPATCH','OPENDISPATCH_TO_PROVIDER','BIDIRECTIONAL')),
 constraint ck_external_change_action_p3g check(decision_action in ('ACCEPT','PROJECT','CREATE_CANDIDATE','CREATE_CONFLICT','IGNORE','REJECT')),
 constraint ck_external_change_risk_p3g check(risk_level in ('UNKNOWN','LOW','MEDIUM','HIGH','CRITICAL')),
 constraint ck_external_change_status_p3g check(status in ('DRAFT','ACTIVE','DISABLED','SUPERSEDED')),
 constraint ck_external_change_version_p3g check(row_version > 0),
 constraint ck_external_change_priority_p3g check(priority >= 0),
 constraint ck_external_change_window_p3g check(expires_at is null or effective_from is null or expires_at > effective_from),
 constraint ck_external_change_provider_mutation_p3g check(not allow_provider_mutation or decision_action in ('ACCEPT','PROJECT'))
);
create index if not exists idx_external_change_policy_resolution_p3g on integration_external_change_policies(tenant_id,status,resource_type,direction,priority desc);

create table if not exists integration_external_comment_sync (
 tenant_id varchar(64) not null,
 sync_id varchar(128) not null,
 connection_id varchar(128) not null,
 project_mapping_id varchar(128),
 external_project_id varchar(255),
 external_issue_id varchar(255) not null,
 task_issue_link_id varchar(128),
 source_comment_id varchar(255) not null,
 provider_comment_id varchar(255),
 direction varchar(40) not null,
 source_marker varchar(160) not null,
 body_hash varchar(128) not null,
 body_preview varchar(512) not null default '',
 status varchar(32) not null,
 retry_count integer not null default 0,
 last_error_code varchar(128),
 last_error_message text,
 row_version bigint not null default 1,
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(),
 correlation_id varchar(128) not null,
 primary key(tenant_id,sync_id),
 constraint fk_external_comment_connection_p3g foreign key(tenant_id,connection_id) references integration_connections(tenant_id,connection_id),
 constraint fk_external_comment_mapping_p3g foreign key(tenant_id,project_mapping_id) references integration_project_mappings(tenant_id,mapping_id),
 constraint ck_external_comment_direction_p3g check(direction in ('PROVIDER_TO_OPENDISPATCH','OPENDISPATCH_TO_PROVIDER')),
 constraint ck_external_comment_status_p3g check(status in ('RECEIVED','PENDING','IN_PROGRESS','SYNCED','LOOP_SUPPRESSED','RETRY_WAITING','WAIT_HUMAN','FAILED_PERMANENT','SUPERSEDED')),
 constraint ck_external_comment_retry_p3g check(retry_count >= 0),
 constraint ck_external_comment_version_p3g check(row_version > 0)
);
create unique index if not exists uk_external_comment_idem_p3g on integration_external_comment_sync(tenant_id,source_comment_id,direction);
create unique index if not exists uk_external_comment_marker_p3g on integration_external_comment_sync(tenant_id,connection_id,source_marker,direction);
create index if not exists idx_external_comment_ops_p3g on integration_external_comment_sync(tenant_id,status,updated_at desc);

create table if not exists integration_external_relation_sync (
 tenant_id varchar(64) not null,
 sync_id varchar(128) not null,
 connection_id varchar(128) not null,
 project_mapping_id varchar(128),
 external_project_id varchar(255),
 source_external_issue_id varchar(255) not null,
 target_external_issue_id varchar(255) not null,
 source_task_issue_link_id varchar(128),
 target_task_issue_link_id varchar(128),
 relation_type varchar(128) not null,
 direction varchar(40) not null,
 source_marker varchar(160) not null,
 provider_relation_id varchar(255),
 status varchar(32) not null,
 retry_count integer not null default 0,
 last_error_code varchar(128),
 last_error_message text,
 row_version bigint not null default 1,
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(),
 correlation_id varchar(128) not null,
 primary key(tenant_id,sync_id),
 constraint fk_external_relation_connection_p3g foreign key(tenant_id,connection_id) references integration_connections(tenant_id,connection_id),
 constraint fk_external_relation_mapping_p3g foreign key(tenant_id,project_mapping_id) references integration_project_mappings(tenant_id,mapping_id),
 constraint ck_external_relation_direction_p3g check(direction in ('PROVIDER_TO_OPENDISPATCH','OPENDISPATCH_TO_PROVIDER')),
 constraint ck_external_relation_status_p3g check(status in ('RECEIVED','PENDING','IN_PROGRESS','SYNCED','LOOP_SUPPRESSED','RETRY_WAITING','WAIT_HUMAN','FAILED_PERMANENT','SUPERSEDED')),
 constraint ck_external_relation_retry_p3g check(retry_count >= 0),
 constraint ck_external_relation_version_p3g check(row_version > 0),
 constraint ck_external_relation_distinct_p3g check(source_external_issue_id <> target_external_issue_id)
);
create unique index if not exists uk_external_relation_idem_p3g on integration_external_relation_sync(tenant_id,source_external_issue_id,target_external_issue_id,relation_type,direction);
create unique index if not exists uk_external_relation_marker_p3g on integration_external_relation_sync(tenant_id,connection_id,source_marker,direction);
create index if not exists idx_external_relation_ops_p3g on integration_external_relation_sync(tenant_id,status,updated_at desc);

create table if not exists integration_provider_action_candidates (
 tenant_id varchar(64) not null,
 candidate_id varchar(128) not null,
 conflict_id varchar(128),
 observation_id varchar(128),
 connection_id varchar(128) not null,
 external_project_id varchar(255),
 external_issue_id varchar(255) not null,
 candidate_type varchar(64) not null,
 risk_level varchar(16) not null,
 status varchar(40) not null,
 requested_command_json jsonb not null default '{}'::jsonb,
 request_hash varchar(128) not null,
 provider_identity_hash varchar(128),
 requires_reauthentication boolean not null default true,
 requires_approval boolean not null default true,
 reviewed_by varchar(128),
 reviewed_at timestamptz,
 approved_by varchar(128),
 approved_at timestamptz,
 executed_by varchar(128),
 executed_at timestamptz,
 execution_evidence text,
 decision_reason text,
 idempotency_key varchar(255) not null,
 row_version bigint not null default 1,
 correlation_id varchar(128) not null,
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(),
 primary key(tenant_id,candidate_id),
 constraint fk_provider_candidate_connection_p3g foreign key(tenant_id,connection_id) references integration_connections(tenant_id,connection_id),
 constraint fk_provider_candidate_conflict_p3g foreign key(tenant_id,conflict_id) references external_issue_conflicts(tenant_id,conflict_id),
 constraint fk_provider_candidate_observation_p3g foreign key(tenant_id,observation_id) references external_issue_observations(tenant_id,observation_id),
 constraint ck_provider_candidate_type_p3g check(candidate_type in ('CLOSE_TASK','CANCEL_TASK','APPROVE_TASK','REJECT_TASK','CHANGE_STATUS','CHANGE_PRIORITY','CHANGE_ASSIGNEE','APPLY_FIELD_UPDATE','DELETE_EXTERNAL_ISSUE','MOVE_EXTERNAL_PROJECT')),
 constraint ck_provider_candidate_risk_p3g check(risk_level in ('UNKNOWN','LOW','MEDIUM','HIGH','CRITICAL')),
 constraint ck_provider_candidate_status_p3g check(status in ('PENDING_REVIEW','WAIT_REAUTHENTICATION','WAIT_APPROVAL','APPROVED','REJECTED','EXECUTION_PENDING','EXECUTING','EXECUTED','EXECUTION_FAILED','SUPERSEDED')),
 constraint ck_provider_candidate_version_p3g check(row_version > 0),
 constraint ck_provider_candidate_sod_p3g check(approved_by is null or reviewed_by is null or approved_by <> reviewed_by)
);
create unique index if not exists uk_provider_candidate_idem_p3g on integration_provider_action_candidates(tenant_id,idempotency_key);
create index if not exists idx_provider_candidate_queue_p3g on integration_provider_action_candidates(tenant_id,status,risk_level,updated_at desc);

create table if not exists integration_provider_action_candidate_events (
 tenant_id varchar(64) not null,
 event_id varchar(128) not null,
 candidate_id varchar(128) not null,
 event_type varchar(64) not null,
 actor_id varchar(128),
 reason_code varchar(128) not null,
 metadata_json jsonb not null default '{}'::jsonb,
 previous_event_hash varchar(128),
 event_hash varchar(128) not null,
 occurred_at timestamptz not null default now(),
 correlation_id varchar(128) not null,
 primary key(tenant_id,event_id),
 constraint fk_provider_candidate_event_p3g foreign key(tenant_id,candidate_id) references integration_provider_action_candidates(tenant_id,candidate_id),
 constraint ck_provider_candidate_event_type_p3g check(event_type in ('CANDIDATE_CREATED','REVIEW_STARTED','REAUTHENTICATION_VERIFIED','APPROVAL_GRANTED','APPROVAL_REJECTED','EXECUTION_REQUESTED','EXECUTION_STARTED','EXECUTION_SUCCEEDED','EXECUTION_FAILED','SUPERSEDED'))
);
create index if not exists idx_provider_candidate_event_timeline_p3g on integration_provider_action_candidate_events(tenant_id,candidate_id,occurred_at desc,event_id desc);

drop trigger if exists trg_provider_candidate_event_immutable_p3g on integration_provider_action_candidate_events;
create trigger trg_provider_candidate_event_immutable_p3g before update or delete on integration_provider_action_candidate_events for each row execute function phase3f_append_only_guard();

create or replace function phase3g_sync_identity_guard() returns trigger language plpgsql as $$
begin
 if old.tenant_id is distinct from new.tenant_id or old.sync_id is distinct from new.sync_id or
    old.connection_id is distinct from new.connection_id or old.direction is distinct from new.direction or
    old.source_marker is distinct from new.source_marker then
   raise exception 'PHASE3G_SYNC_IDENTITY_IMMUTABLE';
 end if;
 if new.row_version < old.row_version then raise exception 'PHASE3G_SYNC_VERSION_REGRESSION'; end if;
 return new;
end $$;
drop trigger if exists trg_external_comment_identity_p3g on integration_external_comment_sync;
create trigger trg_external_comment_identity_p3g before update on integration_external_comment_sync for each row execute function phase3g_sync_identity_guard();
drop trigger if exists trg_external_relation_identity_p3g on integration_external_relation_sync;
create trigger trg_external_relation_identity_p3g before update on integration_external_relation_sync for each row execute function phase3g_sync_identity_guard();

insert into permission_point_catalog(permission_point,resource_type,action_code,description,risk_level)
values
 ('integration.external_change_policy.read','EXTERNAL_CHANGE_POLICY','READ','Read Provider external-change policies.','MEDIUM'),
 ('integration.external_change_policy.manage','EXTERNAL_CHANGE_POLICY','MANAGE','Manage Provider external-change policies.','HIGH'),
 ('integration.external_comment.sync','EXTERNAL_COMMENT_SYNC','SYNC','Synchronize approved comments with an external Issue Provider.','HIGH'),
 ('integration.external_relation.sync','EXTERNAL_RELATION_SYNC','SYNC','Synchronize approved Issue relations.','HIGH'),
 ('integration.provider_action.review','PROVIDER_ACTION_CANDIDATE','REVIEW','Review a Provider-originated Task action candidate.','HIGH'),
 ('integration.provider_action.execute','PROVIDER_ACTION_CANDIDATE','EXECUTE','Execute an approved Provider-originated Task action candidate.','CRITICAL')
on conflict(permission_point) do nothing;

insert into reason_code_catalog(reason_code,http_status,category,retryable,message_template)
values
 ('EXTERNAL_CHANGE_POLICY_VERSION_MISMATCH',409,'INTEGRATION',true,'External change policy version changed.'),
 ('CANDIDATE_EXPECTED_VERSION_MISMATCH',409,'INTEGRATION',true,'Provider action candidate version changed.'),
 ('REAUTHENTICATION_EVIDENCE_REQUIRED',403,'SECURITY',false,'Reauthentication evidence is required.'),
 ('SEPARATION_OF_DUTIES_REQUIRED',403,'SECURITY',false,'A different operator must approve this candidate.'),
 ('COMMENT_COMMAND_PORT_UNAVAILABLE',503,'INTEGRATION',true,'OpenDispatch comment command port is unavailable.'),
 ('RELATION_COMMAND_PORT_UNAVAILABLE',503,'INTEGRATION',true,'OpenDispatch relation command port is unavailable.'),
 ('COMMAND_PORT_UNAVAILABLE',503,'INTEGRATION',true,'Controlled Task command port is unavailable.')
on conflict(reason_code) do nothing;
