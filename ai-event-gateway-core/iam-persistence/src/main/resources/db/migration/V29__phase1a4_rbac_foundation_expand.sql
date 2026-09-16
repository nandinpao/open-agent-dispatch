-- Phase 1A-4 Expand: evolve the existing permission catalog into the single
-- RBAC permission authority and add role, binding, policy-version and shadow stores.

alter table permission_point_catalog
  add column if not exists allowed_scope_types varchar(32)[] not null default array['TENANT']::varchar[],
  add column if not exists system_managed boolean not null default true;

update permission_point_catalog
   set allowed_scope_types = case
     when permission_point in ('system.manage','security.override') then array['INSTANCE']::varchar[]
     when permission_point like 'task.%' or permission_point like 'a2a.%'
       or permission_point like 'integration.%' or permission_point like 'dispatch.%'
       or permission_point like 'handoff.%' or permission_point like 'source_system.%'
       then array['TENANT']::varchar[]
     else allowed_scope_types
   end;

insert into permission_point_catalog(permission_point,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed)
values
 ('instance.tenant.read','TENANT','READ','Read Instance Tenant metadata.','HIGH',array['INSTANCE'],true),
 ('instance.tenant.manage','TENANT','MANAGE','Create, suspend, activate, or update Tenants.','CRITICAL',array['INSTANCE'],true),
 ('identity.user.read','USER','READ','Read users in the active Tenant.','MEDIUM',array['TENANT','DEPARTMENT','GROUP'],true),
 ('identity.user.create','USER','CREATE','Create or invite a user.','HIGH',array['TENANT'],true),
 ('identity.user.update','USER','UPDATE','Update user profile or lifecycle state.','HIGH',array['TENANT'],true),
 ('identity.user.disable','USER','DISABLE','Disable a user in the active Tenant.','CRITICAL',array['TENANT'],true),
 ('identity.user.unlock','USER','UNLOCK','Unlock a user account.','HIGH',array['TENANT'],true),
 ('identity.department.read','DEPARTMENT','READ','Read Department hierarchy.','MEDIUM',array['TENANT','DEPARTMENT'],true),
 ('identity.department.manage','DEPARTMENT','MANAGE','Create, move, rename, or disable Departments.','HIGH',array['TENANT'],true),
 ('identity.group.read','GROUP','READ','Read collaboration Groups.','MEDIUM',array['TENANT','GROUP'],true),
 ('identity.group.manage','GROUP','MANAGE','Create, update, or disable Groups.','HIGH',array['TENANT'],true),
 ('identity.role.read','ROLE','READ','Read roles, permissions, and effective access.','MEDIUM',array['TENANT'],true),
 ('identity.role.manage','ROLE','MANAGE','Create custom roles and manage role permissions.','CRITICAL',array['TENANT'],true),
 ('identity.membership.manage','MEMBERSHIP','MANAGE','Manage Tenant, Department, and Group memberships.','CRITICAL',array['TENANT'],true),
 ('security.policy.read','SECURITY_POLICY','READ','Read password, MFA, session, and token policies.','HIGH',array['TENANT'],true),
 ('security.policy.manage','SECURITY_POLICY','MANAGE','Update password, MFA, session, and token policies.','CRITICAL',array['TENANT'],true),
 ('security.session.read','SESSION','READ','Read Tenant browser sessions.','HIGH',array['TENANT'],true),
 ('security.session.revoke','SESSION','REVOKE','Revoke browser sessions.','CRITICAL',array['TENANT'],true),
 ('security.token.read','TOKEN','READ','Read token metadata.','HIGH',array['TENANT'],true),
 ('security.token.manage','TOKEN','MANAGE','Issue, rotate, or revoke tokens.','CRITICAL',array['TENANT'],true),
 ('security.mfa.reset','MFA_METHOD','RESET','Reset a user MFA method.','CRITICAL',array['TENANT'],true),
 ('audit.identity.read','IAM_AUDIT','READ','Read immutable IAM audit evidence.','HIGH',array['TENANT'],true),
 ('audit.identity.export','IAM_AUDIT','EXPORT','Export IAM audit evidence.','CRITICAL',array['TENANT'],true)
on conflict(permission_point) do update
 set resource_type=excluded.resource_type, action_code=excluded.action_code,
     description=excluded.description, risk_level=excluded.risk_level,
     allowed_scope_types=excluded.allowed_scope_types, system_managed=true,
     active=true, version=permission_point_catalog.version+1;

create table if not exists rbac_roles (
  role_id varchar(128) primary key,
  tenant_id varchar(64),
  role_code varchar(64) not null,
  role_name varchar(200) not null,
  description text not null default '',
  role_type varchar(40) not null,
  status varchar(32) not null,
  system_managed boolean not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  created_by varchar(128) not null,
  updated_by varchar(128) not null,
  version bigint not null default 1,
  foreign key(tenant_id) references tenants(tenant_id)
);
create unique index if not exists uq_rbac_global_role_code on rbac_roles(role_code) where tenant_id is null;
create unique index if not exists uq_rbac_tenant_role_code on rbac_roles(tenant_id,role_code) where tenant_id is not null;
create unique index if not exists uq_rbac_role_tenant_identity on rbac_roles(role_id,tenant_id) nulls not distinct;
create index if not exists idx_rbac_roles_tenant_status on rbac_roles(tenant_id,status,role_code);

create table if not exists rbac_role_permissions (
  grant_id varchar(128) primary key,
  tenant_id varchar(64),
  role_id varchar(128) not null references rbac_roles(role_id),
  permission_point varchar(160) not null references permission_point_catalog(permission_point),
  created_at timestamptz not null,
  created_by varchar(128) not null,
  version bigint not null default 1,
  unique(role_id,permission_point),
  foreign key(tenant_id) references tenants(tenant_id)
);
create index if not exists idx_rbac_role_permissions_role on rbac_role_permissions(role_id,permission_point);

create table if not exists rbac_principal_role_bindings (
  binding_id varchar(128) primary key,
  tenant_id varchar(64),
  principal_type varchar(40) not null,
  principal_id varchar(128) not null,
  role_id varchar(128) not null references rbac_roles(role_id),
  scope_type varchar(32) not null,
  scope_id varchar(128) not null,
  effective_at timestamptz not null,
  expires_at timestamptz,
  status varchar(32) not null,
  created_at timestamptz not null,
  created_by varchar(128) not null,
  revoked_at timestamptz,
  revoked_by varchar(128),
  revoke_reason varchar(500),
  version bigint not null default 1,
  foreign key(tenant_id) references tenants(tenant_id)
);
create index if not exists idx_rbac_bindings_effective
  on rbac_principal_role_bindings(tenant_id,principal_type,principal_id,status,effective_at,expires_at);
create index if not exists idx_rbac_bindings_role on rbac_principal_role_bindings(role_id,status);

create table if not exists rbac_policy_versions (
  scope_type varchar(32) not null,
  scope_id varchar(128) not null,
  tenant_id varchar(64),
  policy_version bigint not null default 0,
  updated_at timestamptz not null,
  updated_by varchar(128) not null,
  primary key(scope_type,scope_id),
  foreign key(tenant_id) references tenants(tenant_id)
);
insert into rbac_policy_versions(scope_type,scope_id,tenant_id,policy_version,updated_at,updated_by)
values('INSTANCE','INSTANCE',null,0,now(),'phase1a4-migration') on conflict do nothing;
insert into rbac_policy_versions(scope_type,scope_id,tenant_id,policy_version,updated_at,updated_by)
select 'TENANT',tenant_id,tenant_id,0,now(),'phase1a4-migration' from tenants on conflict do nothing;

create table if not exists rbac_decision_audits (
  decision_id varchar(128) primary key,
  tenant_id varchar(64),
  principal_type varchar(40) not null,
  principal_id varchar(128) not null,
  permission_point varchar(160) not null,
  resource_type varchar(96),
  resource_id varchar(256),
  requested_scope_type varchar(32),
  requested_scope_id varchar(128),
  decision varchar(32) not null,
  reason_code varchar(128) not null,
  matched_binding_ids varchar(128)[] not null default '{}',
  matched_role_ids varchar(128)[] not null default '{}',
  effective_scope_type varchar(32),
  effective_scope_id varchar(128),
  policy_version bigint not null,
  global_security_epoch bigint not null,
  tenant_security_epoch bigint not null,
  principal_security_epoch bigint not null,
  correlation_id varchar(128),
  decided_at timestamptz not null
);
create index if not exists idx_rbac_decisions_tenant_principal on rbac_decision_audits(tenant_id,principal_id,decided_at desc);
create index if not exists idx_rbac_decisions_reason on rbac_decision_audits(tenant_id,reason_code,decided_at desc);

create table if not exists rbac_shadow_decisions (
  shadow_id varchar(128) primary key,
  tenant_id varchar(64),
  principal_id varchar(128) not null,
  permission_point varchar(160) not null,
  route varchar(512),
  legacy_decision varchar(32) not null,
  new_decision varchar(32) not null,
  reason_code varchar(128) not null,
  lane varchar(32) not null,
  high_risk boolean not null default false,
  metadata_json jsonb not null default '{}'::jsonb,
  occurred_at timestamptz not null
);
create index if not exists idx_rbac_shadow_critical on rbac_shadow_decisions(tenant_id,lane,occurred_at desc);

create table if not exists rbac_shadow_aggregates (
  tenant_id varchar(64) not null,
  route varchar(512) not null,
  permission_point varchar(160) not null,
  legacy_decision varchar(32) not null,
  new_decision varchar(32) not null,
  reason_code varchar(128) not null,
  time_bucket timestamptz not null,
  decision_count bigint not null default 0,
  primary key(tenant_id,route,permission_point,legacy_decision,new_decision,reason_code,time_bucket),
  foreign key(tenant_id) references tenants(tenant_id)
);

-- Stable system and tenant role templates. They are migration-owned and read-only at runtime.
insert into rbac_roles(role_id,tenant_id,role_code,role_name,description,role_type,status,system_managed,created_at,updated_at,created_by,updated_by,version)
values
 ('role-system-admin',null,'SYSTEM_ADMIN','System Administrator','Instance administration without Root recovery authority.','SYSTEM_ROLE','ACTIVE',true,now(),now(),'phase1a4-migration','phase1a4-migration',1),
 ('role-tenant-admin',null,'TENANT_ADMIN','Tenant Administrator','Tenant identity, organization, role binding, and basic settings administration.','TENANT_ROLE','ACTIVE',true,now(),now(),'phase1a4-migration','phase1a4-migration',1),
 ('role-user-admin',null,'USER_ADMIN','User Administrator','User, invitation, and membership administration.','TENANT_ROLE','ACTIVE',true,now(),now(),'phase1a4-migration','phase1a4-migration',1),
 ('role-security-admin',null,'SECURITY_ADMIN','Security Administrator','Password, MFA, session, token, and security policy administration.','TENANT_ROLE','ACTIVE',true,now(),now(),'phase1a4-migration','phase1a4-migration',1),
 ('role-auditor',null,'AUDITOR','Auditor','Read-only IAM security and identity audit access.','TENANT_ROLE','ACTIVE',true,now(),now(),'phase1a4-migration','phase1a4-migration',1),
 ('role-identity-viewer',null,'IDENTITY_VIEWER','Identity Viewer','Read-only User, Department, Group, and Role access.','TENANT_ROLE','ACTIVE',true,now(),now(),'phase1a4-migration','phase1a4-migration',1),
 ('role-legacy-admin',null,'LEGACY_ADMIN','Legacy Compatibility Admin','Temporary ADMIN compatibility mapping.','LEGACY_COMPATIBILITY_ROLE','ACTIVE',true,now(),now(),'phase1a4-migration','phase1a4-migration',1),
 ('role-legacy-operator',null,'LEGACY_OPERATOR','Legacy Compatibility Operator','Temporary OPERATOR compatibility mapping.','LEGACY_COMPATIBILITY_ROLE','ACTIVE',true,now(),now(),'phase1a4-migration','phase1a4-migration',1),
 ('role-legacy-viewer',null,'LEGACY_VIEWER','Legacy Compatibility Viewer','Temporary VIEWER compatibility mapping.','LEGACY_COMPATIBILITY_ROLE','ACTIVE',true,now(),now(),'phase1a4-migration','phase1a4-migration',1)
on conflict(role_id) do nothing;

insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version)
select 'grant-'||replace(role_code,'_','-')||'-'||replace(permission_point,'.','-'),null,role_id,permission_point,now(),'phase1a4-migration',1
from (
 values
 ('SYSTEM_ADMIN','instance.tenant.read'),('SYSTEM_ADMIN','instance.tenant.manage'),
 ('TENANT_ADMIN','identity.user.read'),('TENANT_ADMIN','identity.user.create'),('TENANT_ADMIN','identity.user.update'),('TENANT_ADMIN','identity.user.disable'),('TENANT_ADMIN','identity.user.unlock'),
 ('TENANT_ADMIN','identity.department.read'),('TENANT_ADMIN','identity.department.manage'),('TENANT_ADMIN','identity.group.read'),('TENANT_ADMIN','identity.group.manage'),
 ('TENANT_ADMIN','identity.role.read'),('TENANT_ADMIN','identity.role.manage'),('TENANT_ADMIN','identity.membership.manage'),
 ('USER_ADMIN','identity.user.read'),('USER_ADMIN','identity.user.create'),('USER_ADMIN','identity.user.update'),('USER_ADMIN','identity.user.disable'),('USER_ADMIN','identity.user.unlock'),('USER_ADMIN','identity.membership.manage'),('USER_ADMIN','identity.department.read'),('USER_ADMIN','identity.group.read'),
 ('SECURITY_ADMIN','security.policy.read'),('SECURITY_ADMIN','security.policy.manage'),('SECURITY_ADMIN','security.session.read'),('SECURITY_ADMIN','security.session.revoke'),('SECURITY_ADMIN','security.token.read'),('SECURITY_ADMIN','security.token.manage'),('SECURITY_ADMIN','security.mfa.reset'),
 ('AUDITOR','identity.user.read'),('AUDITOR','identity.department.read'),('AUDITOR','identity.group.read'),('AUDITOR','identity.role.read'),('AUDITOR','audit.identity.read'),('AUDITOR','audit.identity.export'),
 ('IDENTITY_VIEWER','identity.user.read'),('IDENTITY_VIEWER','identity.department.read'),('IDENTITY_VIEWER','identity.group.read'),('IDENTITY_VIEWER','identity.role.read'),
 ('LEGACY_ADMIN','system.manage'),('LEGACY_ADMIN','dispatch.manage'),('LEGACY_ADMIN','source_system.manage'),('LEGACY_ADMIN','audit.read'),
 ('LEGACY_OPERATOR','task.create'),('LEGACY_OPERATOR','task.update'),('LEGACY_OPERATOR','task.resolve'),('LEGACY_OPERATOR','a2a.request'),('LEGACY_OPERATOR','audit.read'),
 ('LEGACY_VIEWER','audit.read')
) as mapping(role_code,permission_point)
join rbac_roles r using(role_code)
on conflict(role_id,permission_point) do nothing;

insert into reason_code_catalog(reason_code,http_status,category,retryable,message_template)
values
 ('AUTH_PERMISSION_GRANTED',200,'AUTHORIZATION',false,'The permission was granted.'),
 ('AUTH_PERMISSION_UNKNOWN',403,'AUTHORIZATION',false,'The requested permission is not in the active Permission Catalog.'),
 ('AUTH_PERMISSION_DISABLED',403,'AUTHORIZATION',false,'The requested permission is disabled.'),
 ('AUTH_SCOPE_UNSUPPORTED',403,'AUTHORIZATION',false,'The permission does not support the requested scope.'),
 ('AUTH_SCOPE_MISMATCH',403,'AUTHORIZATION',false,'No effective role binding matches the requested scope.'),
 ('AUTH_POLICY_VERSION_STALE',401,'AUTHORIZATION',true,'The presented authorization policy version is stale.'),
 ('TENANT_SECURITY_EPOCH_MISMATCH',401,'AUTHORIZATION',true,'The presented Tenant security epoch is stale.'),
 ('ROLE_INSTANCE_PERMISSION_FORBIDDEN',400,'AUTHORIZATION',false,'A custom Tenant role cannot contain Instance permissions.'),
 ('ROLE_BINDING_PRINCIPAL_FORBIDDEN',400,'AUTHORIZATION',false,'The principal type cannot receive a role binding.'),
 ('ROLE_BINDING_SCOPE_INVALID',400,'AUTHORIZATION',false,'The role binding scope is invalid.'),
 ('RBAC_VERSION_CONFLICT',409,'CONCURRENCY',true,'The RBAC resource version changed.'),
 ('SHADOW_CRITICAL_PERSISTENCE_FAILED',503,'AUTHORIZATION',true,'A critical Shadow Decision could not be persisted.'),
 ('AUTH_PERMISSION_DENIED',403,'AUTHORIZATION',false,'No effective Role Binding grants the requested Permission and Scope.'),
 ('AUTH_TENANT_MISMATCH',403,'AUTHORIZATION',false,'The requested scope does not belong to the active Tenant.'),
 ('AUTH_PRINCIPAL_UNSUPPORTED',403,'AUTHORIZATION',false,'The principal type is not supported by this authorization path.'),
 ('ROLE_NOT_FOUND',404,'AUTHORIZATION',false,'The Role does not exist in the active scope.'),
 ('ROLE_DISABLED',409,'AUTHORIZATION',false,'The Role is disabled.'),
 ('ROLE_CODE_CONFLICT',409,'AUTHORIZATION',false,'The Role code already exists.'),
 ('ROLE_SYSTEM_MANAGED',409,'AUTHORIZATION',false,'The system-managed Role cannot be modified at runtime.'),
 ('ROLE_PERMISSION_SCOPE_UNSUPPORTED',400,'AUTHORIZATION',false,'The Permission cannot be granted at the requested Role scope.'),
 ('ROLE_BINDING_NOT_FOUND',404,'AUTHORIZATION',false,'The Role Binding does not exist.'),
 ('ROLE_BINDING_EXPIRED',409,'AUTHORIZATION',false,'The Role Binding is expired.'),
 ('IDENTITY_LAST_TENANT_ADMIN_PROTECTED',409,'AUTHORIZATION',false,'The final effective Tenant Administrator cannot be removed.'),
 ('SHADOW_RECORD_TOO_LARGE',413,'AUTHORIZATION',false,'The Shadow Decision record exceeds the permitted metadata size.')
on conflict(reason_code) do update set http_status=excluded.http_status,category=excluded.category,retryable=excluded.retryable,message_template=excluded.message_template,active=true;

insert into domain_event_catalog(event_type,payload_version,aggregate_type,description,exportable)
values
 ('ROLE_CREATED','1','RBAC_ROLE','A custom Tenant role was created.',false),
 ('ROLE_PERMISSION_CHANGED','1','RBAC_ROLE','A role permission mapping changed.',false),
 ('ROLE_BINDING_ADDED','1','RBAC_BINDING','A principal role binding was added.',false),
 ('ROLE_BINDING_REMOVED','1','RBAC_BINDING','A principal role binding was revoked.',false),
 ('IAM_CACHE_INVALIDATION_REQUESTED','1','RBAC_POLICY','An IAM cache invalidation was requested.',false),
 ('AUTHORIZATION_SHADOW_MISMATCH','1','AUTHORIZATION_DECISION','Legacy and IAM authorization decisions differed.',false)
on conflict(event_type,payload_version) do nothing;
