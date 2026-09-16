-- P4RA-F: Issue/Integration query-scope evidence and external-write human authorization audit.
-- Provider credentials and provider secrets remain owned by Issue Tracking / Secret Bridge.

create table if not exists resource_integration_scope_query_audits (
 tenant_id varchar(64) not null, scope_query_audit_id varchar(128) not null,
 principal_type varchar(32) not null, principal_id varchar(128) not null,
 permission_code varchar(160) not null, resource_type varchar(64) not null,
 purpose varchar(160) not null, strategy varchar(48) not null, plan_hash varchar(128) not null,
 maximum_visibility varchar(32) not null, exact_department_count integer not null,
 subtree_root_count integer not null, group_count integer not null,
 explicit_resource_count integer not null, excluded_resource_count integer not null,
 policy_catalog_version bigint not null, policy_revision bigint not null,
 global_security_epoch bigint not null, tenant_security_epoch bigint not null,
 principal_security_epoch bigint not null, resource_security_epoch bigint not null,
 department_tree_revision bigint not null, created_at timestamptz not null,
 primary key(tenant_id,scope_query_audit_id), unique(tenant_id,plan_hash,purpose),
 foreign key(tenant_id) references tenants(tenant_id), foreign key(resource_type) references resource_catalog(resource_type),
 check(maximum_visibility in ('NONE','METADATA','SUMMARY','STANDARD','SENSITIVE','FULL','SECRET_METADATA')),
 check(exact_department_count>=0 and subtree_root_count>=0 and group_count>=0 and explicit_resource_count>=0 and excluded_resource_count>=0)
);
create table if not exists resource_integration_scope_shadow_mismatches (
 tenant_id varchar(64) not null, mismatch_id varchar(128) not null,
 principal_type varchar(32) not null, principal_id varchar(128) not null,
 permission_code varchar(160) not null, resource_type varchar(64) not null,
 purpose varchar(160) not null, plan_hash varchar(128) not null,
 legacy_only_count integer not null, scoped_only_count integer not null,
 legacy_only_sample text not null, scoped_only_sample text not null, created_at timestamptz not null,
 primary key(tenant_id,mismatch_id), foreign key(tenant_id) references tenants(tenant_id),
 foreign key(resource_type) references resource_catalog(resource_type),
 check(legacy_only_count>=0 and scoped_only_count>=0)
);
create table if not exists resource_external_write_authorization_audits (
 tenant_id varchar(64) not null, audit_id varchar(128) not null,
 authorization_decision_id varchar(128) not null, human_principal_id varchar(128) not null,
 resource_type varchar(64) not null, resource_id varchar(128) not null,
 permission_code varchar(160) not null, purpose varchar(160) not null,
 decision_effect varchar(32) not null, decision_mode varchar(32) not null,
 policy_catalog_version bigint not null, policy_revision bigint not null,
 security_epoch_fingerprint varchar(256) not null, executable boolean not null,
 correlation_id varchar(128) not null, idempotency_key varchar(256) not null,
 created_at timestamptz not null,
 primary key(tenant_id,audit_id), unique(tenant_id,idempotency_key),
 foreign key(tenant_id) references tenants(tenant_id), foreign key(resource_type) references resource_catalog(resource_type),
 check(decision_effect in ('ALLOW','DENY','CONDITIONAL','NOT_APPLICABLE','ERROR')),
 check(decision_mode in ('FORMAL','EXPLAIN','SIMULATION','SHADOW')),
 check(not executable or (decision_effect='ALLOW' and decision_mode='FORMAL'))
);

create index if not exists idx_resource_integration_scope_query_actor on resource_integration_scope_query_audits(tenant_id,principal_id,resource_type,created_at desc);
create index if not exists idx_resource_integration_scope_shadow_actor on resource_integration_scope_shadow_mismatches(tenant_id,principal_id,resource_type,created_at desc);
create index if not exists idx_resource_external_write_decision on resource_external_write_authorization_audits(tenant_id,authorization_decision_id);

alter table resource_integration_scope_query_audits enable row level security;
alter table resource_integration_scope_query_audits force row level security;
drop policy if exists p4ra_integration_scope_audit_tenant on resource_integration_scope_query_audits;
create policy p4ra_integration_scope_audit_tenant on resource_integration_scope_query_audits using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table resource_integration_scope_shadow_mismatches enable row level security;
alter table resource_integration_scope_shadow_mismatches force row level security;
drop policy if exists p4ra_integration_scope_shadow_tenant on resource_integration_scope_shadow_mismatches;
create policy p4ra_integration_scope_shadow_tenant on resource_integration_scope_shadow_mismatches using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table resource_external_write_authorization_audits enable row level security;
alter table resource_external_write_authorization_audits force row level security;
drop policy if exists p4ra_external_write_auth_tenant on resource_external_write_authorization_audits;
create policy p4ra_external_write_auth_tenant on resource_external_write_authorization_audits using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());

drop trigger if exists trg_resource_integration_scope_query_immutable on resource_integration_scope_query_audits;
create trigger trg_resource_integration_scope_query_immutable before update or delete on resource_integration_scope_query_audits for each row execute function p4ra_reject_append_only_mutation();
drop trigger if exists trg_resource_integration_scope_shadow_immutable on resource_integration_scope_shadow_mismatches;
create trigger trg_resource_integration_scope_shadow_immutable before update or delete on resource_integration_scope_shadow_mismatches for each row execute function p4ra_reject_append_only_mutation();
drop trigger if exists trg_resource_external_write_auth_immutable on resource_external_write_authorization_audits;
create trigger trg_resource_external_write_auth_immutable before update or delete on resource_external_write_authorization_audits for each row execute function p4ra_reject_append_only_mutation();

-- allowed_scope_types is the IAM RBAC binding-scope domain only. Resource-level scopes
-- (RESOURCE, TASK_CHAIN, PARTICIPANT, OWNER, etc.) are evaluated by Resource Access
-- after permission entitlement and must never be stored in the IAM scope array.
insert into permission_point_catalog(permission_point,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed)
values
 ('integration.issue.connection.read','ISSUE_CONNECTION','READ','Read an Issue provider connection within Resource Scope.','MEDIUM',array['TENANT','DEPARTMENT','GROUP'],true),
 ('integration.issue.connection.update','ISSUE_CONNECTION','UPDATE','Update an Issue provider connection within owner scope.','HIGH',array['TENANT','DEPARTMENT','GROUP'],true),
 ('integration.issue.principal.read','ISSUE_PRINCIPAL','READ','Read Integration Principal metadata.','HIGH',array['TENANT','DEPARTMENT','GROUP'],true),
 ('integration.issue.principal.update','ISSUE_PRINCIPAL','UPDATE','Update Integration Principal metadata.','CRITICAL',array['TENANT','DEPARTMENT','GROUP'],true),
 ('integration.issue.credential-metadata.read','ISSUE_CREDENTIAL_METADATA','READ','Read credential metadata without Secret material.','CRITICAL',array['TENANT','DEPARTMENT','GROUP'],true),
 ('integration.issue.credential.rotate','ISSUE_CREDENTIAL_METADATA','ROTATE','Rotate an Integration credential through Secret Bridge.','CRITICAL',array['TENANT','DEPARTMENT','GROUP'],true),
 ('integration.issue.mapping.read','ISSUE_PROJECT_MAPPING','READ','Read an Issue project mapping.','MEDIUM',array['TENANT','DEPARTMENT','GROUP'],true),
 ('integration.issue.mapping.update','ISSUE_PROJECT_MAPPING','UPDATE','Update or publish an Issue project mapping.','HIGH',array['TENANT','DEPARTMENT','GROUP'],true),
 ('integration.issue.link.read','TASK_ISSUE_LINK','READ','Read a Task-Issue link.','MEDIUM',array['TENANT','DEPARTMENT','GROUP'],true),
 ('integration.issue.link.update','TASK_ISSUE_LINK','UPDATE','Mutate a Task-Issue relation.','HIGH',array['TENANT','DEPARTMENT','GROUP'],true),
 ('integration.issue.snapshot.read','ISSUE_CONTEXT_SNAPSHOT','READ','Read a Task-bound Issue projection snapshot.','MEDIUM',array['TENANT','DEPARTMENT','GROUP'],true),
 ('integration.issue.conflict.read','ISSUE_CONFLICT','READ','Read an Issue projection conflict.','HIGH',array['TENANT','DEPARTMENT','GROUP'],true),
 ('integration.issue.conflict.resolve','ISSUE_CONFLICT','RESOLVE','Resolve a provider conflict with Human authorization.','CRITICAL',array['TENANT','DEPARTMENT','GROUP'],true),
 ('integration.issue.dead-letter.read','ISSUE_DEAD_LETTER','READ','Read redacted Issue dead-letter metadata.','HIGH',array['TENANT','DEPARTMENT','GROUP'],true),
 ('integration.issue.dead-letter.retry','ISSUE_DEAD_LETTER','RETRY','Retry an Issue dead-letter operation.','CRITICAL',array['TENANT','DEPARTMENT','GROUP'],true),
 ('integration.issue.topology.read','ISSUE_TOPOLOGY','READ','Read redacted Issue topology.','MEDIUM',array['TENANT','DEPARTMENT','GROUP'],true),
 ('integration.issue.topology.update','ISSUE_TOPOLOGY','UPDATE','Update an Issue topology edge.','HIGH',array['TENANT','DEPARTMENT','GROUP'],true)
on conflict(permission_point) do update set resource_type=excluded.resource_type,action_code=excluded.action_code,description=excluded.description,risk_level=excluded.risk_level,allowed_scope_types=excluded.allowed_scope_types,system_managed=true,active=true,version=permission_point_catalog.version+1;

insert into reason_code_catalog(reason_code,http_status,category,retryable,message_template)
values
 ('INTEGRATION_RESOURCE_SCOPE_NOT_MATCHED',403,'SCOPE',false,'The principal has no matching Issue/Integration Resource Scope.'),
 ('INTEGRATION_RESOURCE_EXPLICIT_DENY_MATCHED',403,'SCOPE',false,'An active Explicit Deny blocks the Issue/Integration resource.'),
 ('INTEGRATION_CREDENTIAL_SECRET_CONCEALED',200,'VISIBILITY',false,'Credential Secret material is never returned by Resource Access APIs.'),
 ('EXTERNAL_WRITE_HUMAN_AUTHORIZATION_REQUIRED',403,'PERMISSION',false,'Human Resource Authorization is required before provider write identity selection.'),
 ('EXTERNAL_WRITE_IDENTITY_POLICY_DENIED',403,'SCOPE',false,'The Mapping write identity policy prohibits this provider operation.'),
 ('EXTERNAL_ACTOR_BINDING_REQUIRED',403,'SCOPE',false,'A verified Human-to-Provider actor binding is required.'),
 ('PROVIDER_WRITE_ATTRIBUTION_MISSING',500,'INTERNAL',true,'Provider execution attribution evidence is incomplete.'),
 ('PROVIDER_EXECUTION_AUTHORIZATION_CONTEXT_INCOMPLETE',409,'INTERNAL',false,'The secret-free Provider execution authorization reference is incomplete.'),
 ('PROVIDER_EXECUTION_AUTHORIZATION_CONTEXT_MISMATCH',409,'SCOPE',false,'The current Provider principal or credential no longer matches the authorized execution identity.'),
 ('PROVIDER_EXECUTION_ACTOR_MISMATCH',409,'SCOPE',false,'The current Provider actor no longer matches the verified delegated actor binding.')
on conflict(reason_code) do update set http_status=excluded.http_status,category=excluded.category,retryable=excluded.retryable,message_template=excluded.message_template,active=true,version=reason_code_catalog.version+1;
