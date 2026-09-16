-- Phase 7 Security, Audit and governed credential catalogs.
-- source_hash values below are SHA-256 digests of the owning Java source file in the phase source artifact.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','phase7-security-governance',true);

create table if not exists iam_security_policy_revisions (
  revision_id varchar(160) primary key,
  tenant_id varchar(64) not null references tenants(tenant_id),
  policy_kind varchar(24) not null,
  policy_version bigint not null,
  policy_json jsonb not null,
  actor_id varchar(128) not null,
  audit_reason varchar(500) not null,
  correlation_id varchar(128) not null,
  created_at timestamptz not null,
  constraint ck_iam_security_policy_revision_kind check(policy_kind in('PASSWORD','MFA','SESSION','TOKEN')),
  constraint ck_iam_security_policy_revision_version check(policy_version>=0),
  constraint ck_iam_security_policy_revision_json check(jsonb_typeof(policy_json)='object')
);
create index if not exists idx_iam_security_policy_revision_tenant_kind on iam_security_policy_revisions(tenant_id,policy_kind,created_at desc);
alter table iam_security_policy_revisions enable row level security;
alter table iam_security_policy_revisions force row level security;
drop policy if exists tenant_isolation on iam_security_policy_revisions;
create policy tenant_isolation on iam_security_policy_revisions using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
grant select,insert on iam_security_policy_revisions to opendispatch_runtime;
revoke update,delete on iam_security_policy_revisions from opendispatch_runtime;

create table if not exists iam_credential_audiences (
  audience_code varchar(96) primary key,
  display_name varchar(160) not null,
  description varchar(1000) not null,
  status varchar(24) not null default 'ACTIVE',
  version bigint not null default 1,
  constraint ck_iam_credential_audience_status check(status in('ACTIVE','RETIRED')),
  constraint ck_iam_credential_audience_version check(version>0)
);
create table if not exists iam_credential_api_products (
  product_code varchar(96) primary key,
  display_name varchar(160) not null,
  description varchar(1000) not null,
  allowed_audiences varchar(96)[] not null default '{}',
  api_prefixes varchar(512)[] not null default '{}',
  status varchar(24) not null default 'ACTIVE',
  version bigint not null default 1,
  constraint ck_iam_credential_product_status check(status in('ACTIVE','RETIRED')),
  constraint ck_iam_credential_product_version check(version>0),
  constraint ck_iam_credential_product_prefixes check(cardinality(api_prefixes)>0)
);
grant select on iam_credential_audiences,iam_credential_api_products to opendispatch_runtime;
revoke insert,update,delete on iam_credential_audiences,iam_credential_api_products from opendispatch_runtime;

insert into iam_credential_audiences(audience_code,display_name,description,status,version) values
('OPEN_DISPATCH_ADMIN','OpenDispatch Administration','Administrative APIs for governed Tenant, identity, organization, access and security operations.','ACTIVE',1),
('OPEN_DISPATCH_RUNTIME','OpenDispatch Runtime','Runtime APIs for dispatch, task, Agent and A2A execution.','ACTIVE',1),
('OPEN_DISPATCH_INTEGRATION','OpenDispatch Integration','Integration APIs for ERP, MES, issue tracking and external providers.','ACTIVE',1),
('OPEN_DISPATCH_AUDIT','OpenDispatch Audit','Read-only audit and security evidence APIs.','ACTIVE',1)
on conflict(audience_code) do update set display_name=excluded.display_name,description=excluded.description,status=excluded.status,version=iam_credential_audiences.version+1;

insert into iam_credential_api_products(product_code,display_name,description,allowed_audiences,api_prefixes,status,version) values
('TENANT_ADMINISTRATION','Tenant Administration','Manage Tenant people, organization, responsibilities and security settings.',array['OPEN_DISPATCH_ADMIN'],array['/api/admin/access/tenants/','/api/admin/access/security/'],'ACTIVE',1),
('DISPATCH_RUNTIME','Dispatch Runtime','Submit, inspect and operate dispatch and task runtime requests.',array['OPEN_DISPATCH_RUNTIME'],array['/api/tasks','/api/dispatch','/api/a2a'],'ACTIVE',1),
('INTEGRATION_RUNTIME','Integration Runtime','Operate governed integrations and provider connections.',array['OPEN_DISPATCH_INTEGRATION'],array['/api/integrations'],'ACTIVE',1),
('AUDIT_EVIDENCE','Audit Evidence','Read security, authorization and immutable audit evidence.',array['OPEN_DISPATCH_AUDIT'],array['/api/admin/access/tenants/','/api/security-events'],'ACTIVE',1)
on conflict(product_code) do update set display_name=excluded.display_name,description=excluded.description,allowed_audiences=excluded.allowed_audiences,api_prefixes=excluded.api_prefixes,status=excluded.status,version=iam_credential_api_products.version+1;

insert into permission_entry_point_inventory(
 entry_point_id,entry_point_type,application_id,owner_module,display_name,route_pattern,http_method,authority_state,
 target_permission_code,legacy_authority_type,legacy_authorities,resource_type,resource_resolver_id,exemption_reason,
 migration_deadline,manifest_revision,source_ref,source_hash,last_verified_at,created_by,updated_by)
values
('REST:GET:/api/admin/access/tenants/{tenantId}/security-summary','REST','control-plane-app','iam-api','UnifiedAccessManagementController.securityWorkspaceSummary','/api/admin/access/tenants/{tenantId}/security-summary','GET','TARGET_ONLY','audit.identity.read',null,'[]'::jsonb,'UNIFIED_ACCESS_MANAGEMENT','R3_PATH_RESOURCE_RESOLVER',null,null,'phase7-security-2026-08-06','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/UnifiedAccessManagementController.java#securityWorkspaceSummary','c7c8abbe5e60ce0685adffb3aeee893ea90c4863c222c0fdf9fafbb3396d2fa2',now(),'phase7-security-governance','phase7-security-governance'),
('REST:GET:/api/admin/access/tenants/{tenantId}/credential-governance-catalog','REST','control-plane-app','iam-api','UnifiedAccessManagementController.credentialGovernanceCatalog','/api/admin/access/tenants/{tenantId}/credential-governance-catalog','GET','TARGET_ONLY','security.token.read',null,'[]'::jsonb,'UNIFIED_ACCESS_MANAGEMENT','R3_PATH_RESOURCE_RESOLVER',null,null,'phase7-security-2026-08-06','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/UnifiedAccessManagementController.java#credentialGovernanceCatalog','c7c8abbe5e60ce0685adffb3aeee893ea90c4863c222c0fdf9fafbb3396d2fa2',now(),'phase7-security-governance','phase7-security-governance'),
('REST:GET:/api/admin/access/tenants/{tenantId}/audit-feed','REST','control-plane-app','iam-api','UnifiedAccessManagementController.humanReadableAudit','/api/admin/access/tenants/{tenantId}/audit-feed','GET','TARGET_ONLY','audit.identity.read',null,'[]'::jsonb,'UNIFIED_ACCESS_MANAGEMENT','R3_PATH_RESOURCE_RESOLVER',null,null,'phase7-security-2026-08-06','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/UnifiedAccessManagementController.java#humanReadableAudit','c7c8abbe5e60ce0685adffb3aeee893ea90c4863c222c0fdf9fafbb3396d2fa2',now(),'phase7-security-governance','phase7-security-governance'),
('REST:GET:/api/admin/access/tenants/{tenantId}/security-policy-revisions','REST','control-plane-app','iam-api','UnifiedAccessManagementController.securityPolicyRevisions','/api/admin/access/tenants/{tenantId}/security-policy-revisions','GET','TARGET_ONLY','security.policy.read',null,'[]'::jsonb,'UNIFIED_ACCESS_MANAGEMENT','R3_PATH_RESOURCE_RESOLVER',null,null,'phase7-security-2026-08-06','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/UnifiedAccessManagementController.java#securityPolicyRevisions','c7c8abbe5e60ce0685adffb3aeee893ea90c4863c222c0fdf9fafbb3396d2fa2',now(),'phase7-security-governance','phase7-security-governance'),
('REST:POST:/api/admin/access/security/policies/{policyKind}/restore/{revisionId}','REST','control-plane-app','iam-api','IamSecurityPolicyController.restore','/api/admin/access/security/policies/{policyKind}/restore/{revisionId}','POST','TARGET_ONLY','security.policy.manage',null,'[]'::jsonb,'IAM_SECURITY_POLICY','R3_PATH_RESOURCE_RESOLVER',null,null,'phase7-security-2026-08-06','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/IamSecurityPolicyController.java#restore','d0c9c41acd369d96fd1d8bd20dedd4f6aecb5172072158e88b1ee9c0642d5015',now(),'phase7-security-governance','phase7-security-governance')
on conflict(entry_point_id) do update set display_name=excluded.display_name,route_pattern=excluded.route_pattern,http_method=excluded.http_method,authority_state=excluded.authority_state,target_permission_code=excluded.target_permission_code,legacy_authority_type=null,legacy_authorities='[]'::jsonb,resource_type=excluded.resource_type,resource_resolver_id=excluded.resource_resolver_id,exemption_reason=null,migration_deadline=null,manifest_revision=excluded.manifest_revision,source_ref=excluded.source_ref,source_hash=excluded.source_hash,last_verified_at=now(),updated_by='phase7-security-governance',version=permission_entry_point_inventory.version+1;

update rbac_policy_versions set policy_version=policy_version+1,updated_at=now(),updated_by='phase7-security-governance';
