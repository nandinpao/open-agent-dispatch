-- P4RA-G: provider-neutral Issue Attachment metadata authority. No Provider URL, credential or secret material is stored.
create table if not exists integration_issue_attachment_metadata(
 tenant_id varchar(64) not null,attachment_id varchar(128) not null,connection_id varchar(128) not null,
 task_issue_link_id varchar(128),project_mapping_id varchar(128),provider_attachment_id varchar(256) not null,
 filename varchar(512) not null,content_type varchar(256) not null,size_bytes bigint not null,sha256 varchar(64) not null,
 storage_object_ref varchar(512),malware_status varchar(32) not null,content_available boolean not null default false,
 legal_hold boolean not null default false,metadata_version bigint not null,observed_at timestamptz not null,
 created_at timestamptz not null default now(),updated_at timestamptz not null default now(),
 primary key(tenant_id,attachment_id),unique(tenant_id,connection_id,provider_attachment_id),
 foreign key(tenant_id,connection_id) references integration_connections(tenant_id,connection_id),
 foreign key(tenant_id,task_issue_link_id) references task_issue_links(tenant_id,link_id),
 foreign key(tenant_id,project_mapping_id) references integration_project_mappings(tenant_id,mapping_id),
 check((task_issue_link_id is not null) <> (project_mapping_id is not null)),
 check(size_bytes>=0),check(sha256 ~ '^[0-9a-f]{64}$'),check(metadata_version>0),
 check(malware_status in ('NOT_SCANNED','PENDING','CLEAN','INFECTED','SCAN_FAILED')),
 check(not content_available or storage_object_ref is not null),
 check(storage_object_ref is null or storage_object_ref !~* '^(https?|ftp)://')
);
create index if not exists idx_issue_attachment_parent_link on integration_issue_attachment_metadata(tenant_id,task_issue_link_id,attachment_id);
create index if not exists idx_issue_attachment_parent_mapping on integration_issue_attachment_metadata(tenant_id,project_mapping_id,attachment_id);
alter table integration_issue_attachment_metadata enable row level security;
alter table integration_issue_attachment_metadata force row level security;
drop policy if exists issue_attachment_metadata_tenant on integration_issue_attachment_metadata;
create policy issue_attachment_metadata_tenant on integration_issue_attachment_metadata using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
comment on table integration_issue_attachment_metadata is 'Provider-neutral Issue Attachment metadata. Provider direct URLs, secret_ref and credentials are forbidden.';
