-- Phase 0H expand: API mutation receipts, authorization decisions, audit evidence and governance catalogs.
create table if not exists authorization_decisions (
 tenant_id varchar(128) not null, decision_id varchar(128) not null, permission_point varchar(160) not null,
 resource_type varchar(96), resource_id varchar(256), actor_type varchar(32) not null, actor_id varchar(256) not null,
 decision varchar(24) not null, reason_code varchar(128) not null, evaluated_scopes_json jsonb not null default '[]'::jsonb,
 correlation_id varchar(128) not null, decided_at timestamptz not null default now(), primary key(tenant_id,decision_id)
);
create table if not exists audit_evidence (
 tenant_id varchar(128) not null, evidence_id varchar(128) not null, event_type varchar(160) not null,
 aggregate_type varchar(96), aggregate_id varchar(256), root_task_id varchar(128), actor_type varchar(32) not null,
 actor_id varchar(256) not null, action varchar(160) not null, reason_code varchar(128), audit_reason text,
 correlation_id varchar(128) not null, causation_id varchar(128), authorization_decision_id varchar(128), request_id varchar(128),
 client_address varchar(256), payload_hash varchar(128), outcome varchar(32) not null, evidence_json jsonb not null default '{}'::jsonb,
 occurred_at timestamptz not null default now(), primary key(tenant_id,evidence_id)
);
create table if not exists api_mutation_receipts (
 tenant_id varchar(128) not null, receipt_id varchar(128) not null, request_method varchar(16) not null,
 request_path varchar(512) not null, idempotency_key varchar(256) not null, request_hash varchar(128) not null,
 expected_version bigint, actor_type varchar(32) not null, actor_id varchar(256) not null, audit_reason text,
 correlation_id varchar(128) not null, authorization_decision_id varchar(128) not null, permission_point varchar(160) not null,
 status varchar(32) not null, response_hash varchar(128), result_resource_type varchar(96), result_resource_id varchar(256),
 result_version bigint, sync_status varchar(32), created_at timestamptz not null default now(), completed_at timestamptz,
 primary key(tenant_id,receipt_id), unique(tenant_id,request_method,request_path,idempotency_key)
);
create table if not exists permission_point_catalog (
 permission_point varchar(160) primary key, resource_type varchar(96) not null, action_code varchar(96) not null,
 description text not null, risk_level varchar(24) not null default 'MEDIUM', active boolean not null default true, version integer not null default 1
);
create table if not exists reason_code_catalog (
 reason_code varchar(128) primary key, http_status integer, category varchar(64) not null, retryable boolean not null default false,
 message_template text not null, active boolean not null default true, version integer not null default 1
);
create table if not exists domain_event_catalog (
 event_type varchar(160) not null, payload_version varchar(32) not null, aggregate_type varchar(96) not null,
 description text not null, exportable boolean not null default false, active boolean not null default true,
 primary key(event_type,payload_version)
);
insert into permission_point_catalog(permission_point,resource_type,action_code,description,risk_level) values
 ('task.create','TASK','CREATE','Create an OpenDispatch Task.','HIGH'),('task.update','TASK','UPDATE','Update Task metadata or state.','HIGH'),
 ('task.resolve','TASK','RESOLVE','Complete or resolve a Task.','HIGH'),('a2a.request','A2A_REQUEST','CREATE','Request cross-domain work.','HIGH'),
 ('a2a.approve','A2A_REQUEST','APPROVE','Approve an A2A Request.','HIGH'),('integration.issue.create','EXTERNAL_ISSUE','CREATE','Create an external Issue projection.','HIGH'),
 ('integration.issue.retry','INTEGRATION_SYNC','RETRY','Retry an external synchronization operation.','HIGH'),
 ('integration.issue.resolve_conflict','INTEGRATION_CONFLICT','RESOLVE','Resolve an integration conflict.','CRITICAL'),
 ('integration.secret.rotate','INTEGRATION_CREDENTIAL','ROTATE','Rotate an Integration Credential.','CRITICAL'),('security.override','SECURITY_OVERRIDE','CREATE','Create a time-limited security override.','CRITICAL'),
 ('audit.read','AUDIT_EVIDENCE','READ','Read immutable audit evidence.','MEDIUM')
on conflict(permission_point) do nothing;
insert into reason_code_catalog(reason_code,http_status,category,retryable,message_template) values
 ('API_IDEMPOTENCY_KEY_REQUIRED',400,'API_CONTRACT',false,'Idempotency-Key is required for this mutation.'),
 ('API_EXPECTED_VERSION_REQUIRED',428,'API_CONTRACT',false,'If-Match or expectedVersion is required.'),
 ('API_ACTOR_IDENTITY_REQUIRED',401,'API_CONTRACT',false,'Actor identity is required.'),
 ('API_AUDIT_REASON_REQUIRED',400,'AUDIT',false,'X-Audit-Reason is required for this operation.'),
 ('API_IDEMPOTENCY_CONFLICT',409,'API_CONTRACT',false,'The Idempotency-Key was used with a different request.'),
 ('RESOURCE_VERSION_CONFLICT',409,'CONCURRENCY',true,'The resource version changed.'),
 ('AUTHORIZATION_DENIED',403,'AUTHORIZATION',false,'The actor is not authorized.'),
 ('HANDOFF_CONTEXT_REQUIRED_BEFORE_COMPLETION',409,'TASK',true,'An approved Handoff Context Snapshot is required before completion.')
on conflict(reason_code) do nothing;
insert into domain_event_catalog(event_type,payload_version,aggregate_type,description,exportable) values
 ('TASK_CREATED','1','TASK','A Task was created.',true),('TASK_COMPLETED','1','TASK','A Task completed.',true),
 ('TASK_RELATION_ADDED','1','TASK','A Task relationship was added.',false),('A2A_REQUESTED','1','A2A_REQUEST','An A2A Request was created.',true),
 ('A2A_CHILD_TASK_CREATED','1','A2A_REQUEST','An A2A Child Task was created.',true),('A2A_COMPLETED','1','A2A_REQUEST','An A2A Request completed.',true),
 ('ISSUE_CREATE_REQUESTED','1','TASK_ISSUE_LINK','External Issue creation was requested.',true),('ISSUE_CREATED','1','TASK_ISSUE_LINK','An external Issue was created.',true),
 ('ISSUE_SYNC_FAILED','1','INTEGRATION_OUTBOX','External synchronization failed.',true),('CROSS_PROJECT_RELAY_COMPLETED','1','ISSUE_RELAY','A cross-project relay completed.',true),
 ('AUTHORIZATION_DECIDED','1','AUTHORIZATION_DECISION','An authorization decision was recorded.',false),('AUDIT_EVIDENCE_RECORDED','1','AUDIT_EVIDENCE','Audit evidence was recorded.',false)
on conflict(event_type,payload_version) do nothing;
