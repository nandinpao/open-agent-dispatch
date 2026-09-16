-- P4RA-G: attachment, export, background and runtime lease enforcement evidence.
create table if not exists resource_attachment_security_metadata(
 tenant_id varchar(64) not null,resource_type varchar(64) not null,resource_id varchar(128) not null,
 storage_object_ref varchar(512),malware_status varchar(32) not null,content_available boolean not null default false,
 legal_hold boolean not null default false,metadata_version bigint not null,scanned_at timestamptz not null,
 created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 primary key(tenant_id,resource_type,resource_id),
 foreign key(tenant_id,resource_type,resource_id) references resource_descriptors(tenant_id,resource_type,resource_id) on delete cascade,
 check(resource_type in ('TASK_ATTACHMENT','ISSUE_ATTACHMENT')),check(malware_status in ('NOT_SCANNED','PENDING','CLEAN','INFECTED','SCAN_FAILED')),
 check(metadata_version>0),check(not content_available or storage_object_ref is not null),
 check(storage_object_ref is null or storage_object_ref !~* '^(https?|ftp)://')
);
create table if not exists resource_attachment_security_audits(
 tenant_id varchar(64) not null,audit_id varchar(128) not null,resource_type varchar(64) not null,resource_id varchar(128) not null,
 malware_status varchar(32) not null,content_available boolean not null,legal_hold boolean not null,metadata_version bigint not null,
 actor_id varchar(128) not null,reason text not null,correlation_id varchar(128) not null,created_at timestamptz not null,
 primary key(tenant_id,audit_id)
);
create table if not exists resource_runtime_authorization_leases(
 tenant_id varchar(64) not null,lease_id varchar(128) not null,authorization_decision_id varchar(128) not null,descriptor_hash varchar(128) not null,
 resource_type varchar(64) not null,resource_id varchar(128) not null,principal_type varchar(32) not null,principal_id varchar(128) not null,permission_code varchar(160) not null,assignment_id varchar(128),attempt_no integer,
 policy_catalog_version bigint not null,policy_revision bigint not null,policy_content_hash varchar(128) not null,
 global_epoch bigint not null,tenant_epoch bigint not null,principal_epoch bigint not null,resource_epoch bigint not null,epoch_policy_catalog_version bigint not null,department_tree_revision bigint not null,
 fencing_version bigint not null,issued_at timestamptz not null,expires_at timestamptz not null,maximum_stale_until timestamptz not null,recheck_after timestamptz not null,
 lease_status varchar(32) not null,version bigint not null,created_at timestamptz not null,updated_at timestamptz not null,
 primary key(tenant_id,lease_id),
 foreign key(tenant_id,authorization_decision_id) references resource_authorization_decisions(tenant_id,decision_id),
 foreign key(tenant_id,resource_type,resource_id) references resource_descriptors(tenant_id,resource_type,resource_id),
 check(principal_type in ('USER','SERVICE_ACCOUNT','GROUP','INSTANCE_ROOT','SYSTEM_SERVICE')),
 check(lease_status in ('ACTIVE','RECHECK_REQUIRED','REVOKED','EXPIRED','FENCED','REVOCATION_PENDING','COMPLETED')),
 check(fencing_version>0 and version>0),check(attempt_no is null or attempt_no>=0),check(expires_at>issued_at),
 check(maximum_stale_until>issued_at and maximum_stale_until<=expires_at),check(recheck_after>=issued_at and recheck_after<=maximum_stale_until)
);
create table if not exists resource_runtime_authorization_lease_events(
 tenant_id varchar(64) not null,event_id varchar(128) not null,lease_id varchar(128) not null,authorization_decision_id varchar(128) not null,event_type varchar(48) not null,
 lease_status varchar(32) not null,fencing_version bigint not null,security_epoch_fingerprint varchar(256) not null,reason_code varchar(128) not null,
 correlation_id varchar(128) not null,occurred_at timestamptz not null,created_at timestamptz not null,
 primary key(tenant_id,event_id),foreign key(tenant_id,lease_id) references resource_runtime_authorization_leases(tenant_id,lease_id),
 foreign key(tenant_id,authorization_decision_id) references resource_authorization_decisions(tenant_id,decision_id)
);
create table if not exists resource_attachment_content_handles(
 tenant_id varchar(64) not null,handle_id varchar(128) not null,resource_type varchar(64) not null,resource_id varchar(128) not null,
 storage_object_ref varchar(512) not null,authorization_decision_id varchar(128) not null,runtime_lease_id varchar(128) not null,expires_at timestamptz not null,created_at timestamptz not null,
 primary key(tenant_id,handle_id),foreign key(tenant_id,runtime_lease_id) references resource_runtime_authorization_leases(tenant_id,lease_id),
 foreign key(tenant_id,authorization_decision_id) references resource_authorization_decisions(tenant_id,decision_id),
 check(storage_object_ref !~* '^(https?|ftp)://'),check(expires_at>created_at)
);
create table if not exists resource_attachment_download_audits(
 tenant_id varchar(64) not null,audit_id varchar(128) not null,resource_type varchar(64) not null,resource_id varchar(128) not null,principal_id varchar(128) not null,
 read_decision_id varchar(128) not null,download_decision_id varchar(128) not null,runtime_lease_id varchar(128) not null,fencing_version bigint not null,
 content_handle_id varchar(128) not null,content_type varchar(256) not null,size_bytes bigint not null,sha256 varchar(64) not null,purpose varchar(160) not null,correlation_id varchar(128) not null,created_at timestamptz not null,
 primary key(tenant_id,audit_id),foreign key(tenant_id,runtime_lease_id) references resource_runtime_authorization_leases(tenant_id,lease_id),
 foreign key(tenant_id,read_decision_id) references resource_authorization_decisions(tenant_id,decision_id),
 foreign key(tenant_id,download_decision_id) references resource_authorization_decisions(tenant_id,decision_id),
 check(size_bytes>=0),check(sha256 ~ '^[0-9a-f]{64}$')
);
create table if not exists resource_export_authorizations(
 tenant_id varchar(64) not null,export_authorization_id varchar(128) not null,resource_type varchar(64) not null,resource_id varchar(128) not null,
 export_format varchar(32) not null,selected_fields_text text not null,field_set_hash varchar(64) not null,maximum_rows bigint not null,
 read_decision_id varchar(128) not null,export_decision_id varchar(128) not null,policy_catalog_version bigint not null,policy_revision bigint not null,policy_content_hash varchar(128) not null,
 security_epoch_fingerprint varchar(256) not null,descriptor_hash varchar(128) not null,runtime_lease_id varchar(128) not null,fencing_version bigint not null,
 principal_id varchar(128) not null,purpose varchar(160) not null,idempotency_key varchar(256) not null,correlation_id varchar(128) not null,
 issued_at timestamptz not null,expires_at timestamptz not null,executable boolean not null,created_at timestamptz not null,
 primary key(tenant_id,export_authorization_id),unique(tenant_id,idempotency_key),foreign key(tenant_id,runtime_lease_id) references resource_runtime_authorization_leases(tenant_id,lease_id),
 foreign key(tenant_id,read_decision_id) references resource_authorization_decisions(tenant_id,decision_id),
 foreign key(tenant_id,export_decision_id) references resource_authorization_decisions(tenant_id,decision_id),
 check(export_format in ('CSV','JSON','NDJSON','XLSX')),check(field_set_hash ~ '^[0-9a-f]{64}$'),check(maximum_rows>0),check(fencing_version>0),check(expires_at>issued_at)
);
create table if not exists resource_export_artifact_commits(
 tenant_id varchar(64) not null,commit_id varchar(128) not null,export_authorization_id varchar(128) not null,runtime_lease_id varchar(128) not null,principal_id varchar(128) not null,
 row_count bigint not null,field_set_hash varchar(64) not null,artifact_sha256 varchar(64) not null,artifact_size_bytes bigint not null,accepted boolean not null,reason_code varchar(128) not null,
 correlation_id varchar(128) not null,committed_at timestamptz not null,created_at timestamptz not null,
 primary key(tenant_id,commit_id),foreign key(tenant_id,export_authorization_id) references resource_export_authorizations(tenant_id,export_authorization_id),
 check(row_count>=0 and artifact_size_bytes>=0),check(field_set_hash ~ '^[0-9a-f]{64}$'),check(artifact_sha256 ~ '^[0-9a-f]{64}$')
);
create table if not exists resource_background_authorization_audits(
 tenant_id varchar(64) not null,audit_id varchar(128) not null,authorization_id varchar(128) not null,job_code varchar(128) not null,principal_id varchar(128) not null,
 decision_ids_text text not null,resource_refs_text text not null,runtime_lease_ids_text text not null,executable boolean not null,purpose varchar(160) not null,correlation_id varchar(128) not null,evaluated_at timestamptz not null,created_at timestamptz not null,
 primary key(tenant_id,audit_id)
);
create unique index if not exists uq_runtime_lease_resource_fencing on resource_runtime_authorization_leases(tenant_id,resource_type,resource_id,fencing_version);
create index if not exists idx_runtime_lease_resource on resource_runtime_authorization_leases(tenant_id,resource_type,resource_id,lease_status,expires_at);
create index if not exists idx_runtime_lease_principal on resource_runtime_authorization_leases(tenant_id,principal_id,lease_status,expires_at);
create index if not exists idx_export_auth_resource on resource_export_authorizations(tenant_id,resource_type,resource_id,issued_at desc);
create index if not exists idx_attachment_download_resource on resource_attachment_download_audits(tenant_id,resource_type,resource_id,created_at desc);
create index if not exists idx_attachment_content_handle_expiry on resource_attachment_content_handles(tenant_id,expires_at);
create unique index if not exists uq_export_accepted_commit on resource_export_artifact_commits(tenant_id,export_authorization_id) where accepted;

do $$ declare t text; begin foreach t in array array['resource_attachment_security_metadata','resource_attachment_security_audits','resource_runtime_authorization_leases','resource_runtime_authorization_lease_events','resource_attachment_content_handles','resource_attachment_download_audits','resource_export_authorizations','resource_export_artifact_commits','resource_background_authorization_audits'] loop execute format('alter table %I enable row level security',t);execute format('alter table %I force row level security',t);execute format('drop policy if exists tenant_isolation on %I',t);execute format('create policy tenant_isolation on %I using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id())',t);end loop;end $$;

do $$ declare t text; begin foreach t in array array['resource_attachment_security_audits','resource_runtime_authorization_lease_events','resource_attachment_content_handles','resource_attachment_download_audits','resource_export_authorizations','resource_export_artifact_commits','resource_background_authorization_audits'] loop execute format('drop trigger if exists trg_%s_immutable on %I',t,t);execute format('create trigger trg_%s_immutable before update or delete on %I for each row execute function p4ra_reject_append_only_mutation()',t,t);end loop;end $$;

-- allowed_scope_types remains the IAM RBAC binding-scope domain. Attachment, export,
-- ownership, participant, task-chain, and explicit-resource checks are enforced by
-- Resource Access decisions and Runtime Leases, not by IAM role-binding scope values.
insert into permission_point_catalog(permission_point,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed)
values
 ('task.attachment.read','TASK_ATTACHMENT','READ','Read governed task attachment metadata.','MEDIUM',array['TENANT','DEPARTMENT','GROUP'],true),
 ('task.attachment.download','TASK_ATTACHMENT','DOWNLOAD','Release governed task attachment content.','CRITICAL',array['TENANT','DEPARTMENT','GROUP'],true),
 ('integration.issue.attachment.read','ISSUE_ATTACHMENT','READ','Read governed issue attachment metadata.','MEDIUM',array['TENANT','DEPARTMENT','GROUP'],true),
 ('integration.issue.attachment.download','ISSUE_ATTACHMENT','DOWNLOAD','Release governed issue attachment content.','CRITICAL',array['TENANT','DEPARTMENT','GROUP'],true),
 ('task.export','TASK','EXPORT','Export field-filtered task data.','CRITICAL',array['TENANT','DEPARTMENT','GROUP'],true),
 ('integration.issue.export','ISSUE_CONTEXT_SNAPSHOT','EXPORT','Export field-filtered issue/integration data.','CRITICAL',array['TENANT','DEPARTMENT','GROUP'],true),
 ('integration.issue.worker.execute','ISSUE_PROJECT_MAPPING','EXECUTE','Execute least-privilege Issue Relay or Recovery background work.','CRITICAL',array['TENANT','DEPARTMENT','GROUP'],true)
on conflict(permission_point) do update set resource_type=excluded.resource_type,action_code=excluded.action_code,description=excluded.description,risk_level=excluded.risk_level,allowed_scope_types=excluded.allowed_scope_types,system_managed=true,active=true,version=permission_point_catalog.version+1;

insert into reason_code_catalog(reason_code,http_status,category,retryable,message_template)
values
 ('ATTACHMENT_MALWARE_STATUS_BLOCKED',409,'SECURITY',false,'Attachment content is blocked until malware status is CLEAN.'),
 ('ATTACHMENT_CONTENT_UNAVAILABLE',409,'SECURITY',true,'Attachment content is not available in governed internal storage.'),
 ('ATTACHMENT_DOWNLOAD_POLICY_DENIED',403,'VISIBILITY',false,'The active field policy prohibits attachment download.'),
 ('ATTACHMENT_DIRECT_URL_FORBIDDEN',500,'SECURITY',false,'Provider direct URLs are forbidden for attachment delivery.'),
 ('EXPORT_FIELD_POLICY_REQUIRED',409,'VISIBILITY',false,'An active field visibility/export policy is required.'),
 ('EXPORT_ARTIFACT_COMMIT_DENIED',409,'AUTHORIZATION',false,'The export artifact failed its final authorization checkpoint.'),
 ('BACKGROUND_RESOURCE_AUTHORIZATION_REQUIRED',403,'AUTHORIZATION',false,'The background worker has no executable Resource Authorization.'),
 ('RUNTIME_AUTHORIZATION_LEASE_EXPIRED',409,'AUTHORIZATION',false,'The Runtime Authorization Lease expired.'),
 ('RUNTIME_MAXIMUM_STALE_WINDOW_EXCEEDED',409,'AUTHORIZATION',false,'The Runtime Authorization Lease missed its maximum stale deadline.'),
 ('RUNTIME_LEASE_DECISION_BINDING_MISMATCH',409,'AUTHORIZATION',false,'The Runtime Authorization Lease does not match its formal authorization decision.'),
 ('RUNTIME_EPOCH_MISMATCH',409,'AUTHORIZATION',false,'The Runtime Authorization Lease was fenced because its security epoch no longer matches the current authority epoch.')
on conflict(reason_code) do update set http_status=excluded.http_status,category=excluded.category,retryable=excluded.retryable,message_template=excluded.message_template,active=true,version=reason_code_catalog.version+1;

comment on table resource_attachment_security_metadata is 'Internal scan/storage projection. Provider URLs, credentials and secret references are forbidden.';
comment on table resource_runtime_authorization_leases is 'Expiring, epoch-bound authorization lease for attachment, export, streaming and long-running background execution.';
