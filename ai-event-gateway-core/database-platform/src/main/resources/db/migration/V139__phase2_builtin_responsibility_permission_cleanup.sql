-- Phase 2: Built-in Responsibility / Permission Matrix Cleanup.
-- Corrective migration: historical migrations remain immutable; this migration defines the
-- canonical final grants for business-facing built-in Responsibilities.

select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','phase2-responsibility-cleanup',true);

-- Canonical business-facing templates. INSTANCE_ROOT remains a principal type, not a Role Binding.
insert into rbac_roles(
  role_id,tenant_id,role_code,role_name,description,role_type,status,system_managed,
  created_at,updated_at,created_by,updated_by,version)
values
 ('role-platform-admin',null,'PLATFORM_ADMIN','Platform Administrator','Delegated Instance and Tenant lifecycle administration without Root bootstrap, break-glass, or permission-engineering authority.','SYSTEM_ROLE','ACTIVE',true,now(),now(),'phase2-responsibility-cleanup','phase2-responsibility-cleanup',1),
 ('role-user-admin',null,'USER_ADMIN','User Administrator','Person account and Tenant membership administration without organization, access-policy, or security-policy administration.','TENANT_ROLE','ACTIVE',true,now(),now(),'phase2-responsibility-cleanup','phase2-responsibility-cleanup',1),
 ('role-organization-admin',null,'ORGANIZATION_ADMIN','Organization Administrator','Department, Group, Manager, and organization membership administration without Role or security administration.','TENANT_ROLE','ACTIVE',true,now(),now(),'phase2-responsibility-cleanup','phase2-responsibility-cleanup',1),
 ('role-access-admin',null,'ACCESS_ADMIN','Access Administrator','Tenant Responsibility, permission assignment, Role Binding, approval request, and Effective Access administration.','TENANT_ROLE','ACTIVE',true,now(),now(),'phase2-responsibility-cleanup','phase2-responsibility-cleanup',1),
 ('role-security-admin',null,'SECURITY_ADMIN','Security Administrator','Tenant sign-in security, MFA, session, token, and security-policy administration without Person or organization administration.','TENANT_ROLE','ACTIVE',true,now(),now(),'phase2-responsibility-cleanup','phase2-responsibility-cleanup',1),
 ('role-dispatch-admin',null,'DISPATCH_ADMIN','Dispatch Administrator','Source System, Dispatch, A2A policy, Agent configuration, capability, and governed routing administration.','TENANT_ROLE','ACTIVE',true,now(),now(),'phase2-responsibility-cleanup','phase2-responsibility-cleanup',1),
 ('role-operator',null,'OPERATOR','Operator','Day-to-day Task, Dispatch Request, Adapter Action, Incident, and Issue operational execution without IAM, security-policy, or dispatch-configuration administration.','TENANT_ROLE','ACTIVE',true,now(),now(),'phase2-responsibility-cleanup','phase2-responsibility-cleanup',1),
 ('role-auditor',null,'AUDITOR','Auditor','Independent read-only operational, identity, access, security, audit, and authorization-evidence review.','TENANT_ROLE','ACTIVE',true,now(),now(),'phase2-responsibility-cleanup','phase2-responsibility-cleanup',1),
 ('role-viewer',null,'VIEWER','Viewer','Tenant operational read-only access without People & Access or security administration.','TENANT_ROLE','ACTIVE',true,now(),now(),'phase2-responsibility-cleanup','phase2-responsibility-cleanup',1),
 ('role-identity-viewer',null,'IDENTITY_VIEWER','Identity Viewer','Read-only Person, Tenant membership, organization, Responsibility, Role Binding, and identity-audit visibility.','TENANT_ROLE','ACTIVE',true,now(),now(),'phase2-responsibility-cleanup','phase2-responsibility-cleanup',1)
on conflict(role_id) do update set
 role_code=excluded.role_code,
 role_name=excluded.role_name,
 description=excluded.description,
 role_type=excluded.role_type,
 status='ACTIVE',
 system_managed=true,
 updated_at=now(),
 updated_by='phase2-responsibility-cleanup',
 version=rbac_roles.version+1;

-- V116 preserves the existing canonical TENANT_ADMIN role_id, so update it by business code rather
-- than assuming a physical identifier.
update rbac_roles
set role_name='Tenant Administrator',
    description='Full business administration for one Tenant, excluding Instance Root and permission-engineering authority.',
    role_type='TENANT_ROLE',status='ACTIVE',system_managed=true,
    updated_at=now(),updated_by='phase2-responsibility-cleanup',version=version+1
where tenant_id is null and role_code='TENANT_ADMIN';

update rbac_roles
set risk_level=case role_code
      when 'PLATFORM_ADMIN' then 'CRITICAL'
      when 'TENANT_ADMIN' then 'CRITICAL'
      when 'ACCESS_ADMIN' then 'CRITICAL'
      when 'SECURITY_ADMIN' then 'CRITICAL'
      when 'USER_ADMIN' then 'HIGH'
      when 'ORGANIZATION_ADMIN' then 'HIGH'
      when 'DISPATCH_ADMIN' then 'HIGH'
      when 'OPERATOR' then 'HIGH'
      when 'AUDITOR' then 'MEDIUM'
      when 'IDENTITY_VIEWER' then 'MEDIUM'
      when 'VIEWER' then 'LOW'
      else risk_level end,
    review_required=role_code in('PLATFORM_ADMIN','TENANT_ADMIN','ACCESS_ADMIN','SECURITY_ADMIN','USER_ADMIN','ORGANIZATION_ADMIN','DISPATCH_ADMIN','OPERATOR'),
    next_review_at=case when role_code in('PLATFORM_ADMIN','TENANT_ADMIN','ACCESS_ADMIN','SECURITY_ADMIN','USER_ADMIN','ORGANIZATION_ADMIN','DISPATCH_ADMIN','OPERATOR')
      then coalesce(next_review_at,now()+interval '90 days') else next_review_at end,
    updated_at=now(),updated_by='phase2-responsibility-cleanup',version=version+1
where tenant_id is null and role_code in(
 'PLATFORM_ADMIN','TENANT_ADMIN','USER_ADMIN','ORGANIZATION_ADMIN','ACCESS_ADMIN','SECURITY_ADMIN',
 'DISPATCH_ADMIN','OPERATOR','AUDITOR','VIEWER','IDENTITY_VIEWER');

-- Rebuild specialized templates from an allowlist. This intentionally removes stale grants from
-- older migrations (for example USER_ADMIN Role-Binding management and VIEWER membership mutation).
delete from rbac_role_permissions rp
using rbac_roles r
where rp.role_id=r.role_id and rp.tenant_id is null and r.tenant_id is null
  and r.role_code in('PLATFORM_ADMIN','USER_ADMIN','ORGANIZATION_ADMIN','ACCESS_ADMIN','SECURITY_ADMIN',
                     'DISPATCH_ADMIN','OPERATOR','AUDITOR','VIEWER','IDENTITY_VIEWER');

-- Tenant Administrator remains broad business authority but must not inherit permission-engineering
-- / authorization-cutover controls. Those belong to INSTANCE_ROOT / engineering administration.
delete from rbac_role_permissions rp
using rbac_roles r
where rp.role_id=r.role_id and rp.tenant_id is null and r.tenant_id is null and r.role_code='TENANT_ADMIN'
  and (rp.permission_point like 'permission.%' or rp.permission_point like 'api.wave0.%');

-- Explicit IAM / platform / security responsibility grants.
with canonical(role_code,permission_point) as (values
 ('PLATFORM_ADMIN','instance.tenant.read'),
 ('PLATFORM_ADMIN','instance.tenant.manage'),
 ('PLATFORM_ADMIN','identity.platform_user.read'),
 ('PLATFORM_ADMIN','identity.platform_user.create'),
 ('PLATFORM_ADMIN','identity.platform_user.update'),
 ('PLATFORM_ADMIN','identity.platform_user.security'),
 ('PLATFORM_ADMIN','identity.platform_admin.manage'),
 ('PLATFORM_ADMIN','identity.platform_role.read'),
 ('PLATFORM_ADMIN','identity.platform_role.manage'),
 ('PLATFORM_ADMIN','identity.role_binding.read'),
 ('PLATFORM_ADMIN','identity.role_binding.manage'),
 ('PLATFORM_ADMIN','identity.role_permission.manage'),
 ('PLATFORM_ADMIN','identity.role_approval.read'),
 ('PLATFORM_ADMIN','identity.role_approval.request'),

 ('USER_ADMIN','identity.user.read'),
 ('USER_ADMIN','identity.user.create'),
 ('USER_ADMIN','identity.user.update'),
 ('USER_ADMIN','identity.tenant_membership.read'),
 ('USER_ADMIN','identity.tenant_membership.manage'),
 ('USER_ADMIN','identity.department.read'),
 ('USER_ADMIN','identity.group.read'),

 ('ORGANIZATION_ADMIN','identity.user.read'),
 ('ORGANIZATION_ADMIN','identity.department.read'),
 ('ORGANIZATION_ADMIN','identity.department.manage'),
 ('ORGANIZATION_ADMIN','identity.group.read'),
 ('ORGANIZATION_ADMIN','identity.group.manage'),
 ('ORGANIZATION_ADMIN','identity.membership.manage'),

 ('ACCESS_ADMIN','identity.user.read'),
 ('ACCESS_ADMIN','identity.department.read'),
 ('ACCESS_ADMIN','identity.group.read'),
 ('ACCESS_ADMIN','identity.tenant_role.read'),
 ('ACCESS_ADMIN','identity.tenant_role.manage'),
 ('ACCESS_ADMIN','identity.role_permission.manage'),
 ('ACCESS_ADMIN','identity.role_binding.read'),
 ('ACCESS_ADMIN','identity.role_binding.manage'),
 ('ACCESS_ADMIN','identity.role_approval.read'),
 ('ACCESS_ADMIN','identity.role_approval.request'),

 ('SECURITY_ADMIN','identity.user.read'),
 ('SECURITY_ADMIN','security.mfa.reset'),
 ('SECURITY_ADMIN','security.policy.read'),
 ('SECURITY_ADMIN','security.policy.manage'),
 ('SECURITY_ADMIN','security.session.read'),
 ('SECURITY_ADMIN','security.session.revoke'),
 ('SECURITY_ADMIN','security.token.read'),
 ('SECURITY_ADMIN','security.token.manage'),
 ('SECURITY_ADMIN','identity.role_approval.read'),
 ('SECURITY_ADMIN','identity.role_approval.approve'),

 ('IDENTITY_VIEWER','identity.user.read'),
 ('IDENTITY_VIEWER','identity.tenant_membership.read'),
 ('IDENTITY_VIEWER','identity.department.read'),
 ('IDENTITY_VIEWER','identity.group.read'),
 ('IDENTITY_VIEWER','identity.tenant_role.read'),
 ('IDENTITY_VIEWER','identity.role_binding.read'),
 ('IDENTITY_VIEWER','identity.role_approval.read'),
 ('IDENTITY_VIEWER','audit.identity.read'),

 ('AUDITOR','identity.user.read'),
 ('AUDITOR','identity.tenant_membership.read'),
 ('AUDITOR','identity.department.read'),
 ('AUDITOR','identity.group.read'),
 ('AUDITOR','identity.tenant_role.read'),
 ('AUDITOR','identity.role_binding.read'),
 ('AUDITOR','identity.role_approval.read'),
 ('AUDITOR','audit.identity.read'),
 ('AUDITOR','security.policy.read'),
 ('AUDITOR','security.session.read'),
 ('AUDITOR','security.token.read')
)
insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version)
select 'phase2-'||substr(md5(r.role_id||':'||c.permission_point),1,32),null,r.role_id,c.permission_point,now(),'phase2-responsibility-cleanup',1
from canonical c
join rbac_roles r on r.tenant_id is null and r.role_code=c.role_code and r.status='ACTIVE'
join permission_definitions p on p.permission_code=c.permission_point and p.active=true and p.lifecycle='ACTIVE'
on conflict(role_id,permission_point) do nothing;

-- Viewer is a deliberately operational read-only responsibility. Security/IAM administration,
-- authorization-engine internals and integrations configuration are not part of this baseline.
with operational_read as (
 select p.permission_code
 from permission_definitions p
 where p.active=true and p.lifecycle='ACTIVE' and p.risk_lane='READ'
   and (
      p.permission_code like 'admin.core.dashboard.%'
      or p.permission_code like 'admin.core.admin.task.facade.%'
      or p.permission_code like 'admin.agent.%'
      or p.permission_code like 'admin.dispatch.%'
      or p.permission_code like 'admin.source.system.%'
      or p.permission_code like 'admin.task.%'
      or p.permission_code like 'admin.enforce.%'
      or p.permission_code like 'api.task.%'
      or p.permission_code like 'api.agent.%'
      or p.permission_code like 'api.assignment.%'
      or p.permission_code like 'api.dispatch.request.%'
      or p.permission_code like 'api.routing.%'
      or p.permission_code like 'api.a2.aoperations.%'
      or p.permission_code like 'api.issue.%'
      or p.permission_code like 'api.incident.%'
      or p.permission_code like 'api.gateway.%'
      or p.permission_code like 'api.operational.%'
      or p.permission_code like 'api.core.%'
      or p.permission_code like 'api.adapter.action.%'
      or p.permission_code like 'api.projection.%'
      or p.permission_code like 'api.provider.%'
      or p.permission_code like 'api.relay.%'
      or p.permission_code like 'api.external.%'
      or p.permission_code like 'api.handoff.%'
   )
)
insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version)
select 'phase2-'||substr(md5(r.role_id||':'||o.permission_code),1,32),null,r.role_id,o.permission_code,now(),'phase2-responsibility-cleanup',1
from rbac_roles r cross join operational_read o
where r.tenant_id is null and r.role_code='VIEWER' and r.status='ACTIVE'
on conflict(role_id,permission_point) do nothing;

-- Operator inherits the Viewer read baseline plus narrowly selected operational mutations.
insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version)
select 'phase2-'||substr(md5(operator_role.role_id||':'||viewer_grant.permission_point),1,32),null,operator_role.role_id,viewer_grant.permission_point,now(),'phase2-responsibility-cleanup',1
from rbac_roles operator_role
join rbac_roles viewer_role on viewer_role.tenant_id is null and viewer_role.role_code='VIEWER'
join rbac_role_permissions viewer_grant on viewer_grant.role_id=viewer_role.role_id and viewer_grant.tenant_id is null
where operator_role.tenant_id is null and operator_role.role_code='OPERATOR' and operator_role.status='ACTIVE'
on conflict(role_id,permission_point) do nothing;

with operator_mutation(permission_point) as (values
 ('admin.core.admin.task.facade.cancel.dispatch.request'),
 ('admin.core.admin.task.facade.cancel.task'),
 ('admin.core.admin.task.facade.dead.letter.task'),
 ('admin.core.admin.task.facade.escalate.task'),
 ('admin.core.admin.task.facade.manual.retry.task'),
 ('admin.core.admin.task.facade.reassign.task'),
 ('admin.core.admin.task.facade.retry.dispatch.request'),
 ('admin.core.admin.task.facade.retry.task.latest.dispatch'),
 ('admin.core.admin.task.facade.run.task.remediation.command'),
 ('admin.core.admin.task.facade.timeout.task'),
 ('api.task.cancel'),
 ('api.task.reassign'),
 ('api.task.timeout'),
 ('api.task.process.timeouts'),
 ('api.task.domain.transition'),
 ('api.assignment.decide'),
 ('api.assignment.scan.delayed.dispatch.recovery'),
 ('api.dispatch.request.approve'),
 ('api.dispatch.request.cancel'),
 ('api.dispatch.request.dead.letter'),
 ('api.dispatch.request.execute'),
 ('api.dispatch.request.execute.approved'),
 ('api.dispatch.request.reject'),
 ('api.dispatch.request.retry'),
 ('api.adapter.action.cancel'),
 ('api.adapter.action.complete'),
 ('api.adapter.action.execute'),
 ('api.adapter.action.execute.pending'),
 ('api.adapter.action.execute.retry'),
 ('api.adapter.action.fail'),
 ('api.adapter.action.recover.expired.leases'),
 ('api.adapter.action.retry'),
 ('api.incident.auto.resolve'),
 ('api.incident.reopen'),
 ('api.incident.resolve'),
 ('api.incident.suppress'),
 ('api.issue.projection.resolve'),
 ('api.issue.projection.retry'),
 ('api.issue.projection.state.materialize'),
 ('api.issue.projection.state.request.intent'),
 ('api.issue.projection.state.resolve'),
 ('api.issue.projection.state.retry'),
 ('api.issue.projection.state.supersede'),
 ('api.issue.projection.state.suspend')
)
insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version)
select 'phase2-'||substr(md5(r.role_id||':'||m.permission_point),1,32),null,r.role_id,m.permission_point,now(),'phase2-responsibility-cleanup',1
from operator_mutation m
join permission_definitions p on p.permission_code=m.permission_point and p.active=true and p.lifecycle='ACTIVE'
join rbac_roles r on r.tenant_id is null and r.role_code='OPERATOR' and r.status='ACTIVE'
on conflict(role_id,permission_point) do nothing;

-- Dispatch Administrator inherits read-only operations and owns configuration/governance mutations
-- for Source Systems, Dispatch, A2A policy and Agent configuration. IAM/security permissions are
-- intentionally outside this responsibility.
insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version)
select 'phase2-'||substr(md5(dispatch_role.role_id||':'||viewer_grant.permission_point),1,32),null,dispatch_role.role_id,viewer_grant.permission_point,now(),'phase2-responsibility-cleanup',1
from rbac_roles dispatch_role
join rbac_roles viewer_role on viewer_role.tenant_id is null and viewer_role.role_code='VIEWER'
join rbac_role_permissions viewer_grant on viewer_grant.role_id=viewer_role.role_id and viewer_grant.tenant_id is null
where dispatch_role.tenant_id is null and dispatch_role.role_code='DISPATCH_ADMIN' and dispatch_role.status='ACTIVE'
on conflict(role_id,permission_point) do nothing;

insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version)
select 'phase2-'||substr(md5(r.role_id||':'||p.permission_code),1,32),null,r.role_id,p.permission_code,now(),'phase2-responsibility-cleanup',1
from rbac_roles r
join permission_definitions p on p.active=true and p.lifecycle='ACTIVE' and (
     p.permission_code like 'admin.source.system.%'
     or p.permission_code like 'admin.dispatch.%'
     or p.permission_code like 'api.a2.agovernance.%'
     or p.permission_code like 'admin.agent.assignment.%'
     or p.permission_code like 'admin.agent.governance.%'
     or p.permission_code like 'admin.agent.setup.%'
     or p.permission_code like 'admin.agent.skill.registry.%'
   )
where r.tenant_id is null and r.role_code='DISPATCH_ADMIN' and r.status='ACTIVE'
on conflict(role_id,permission_point) do nothing;

-- Auditor inherits the operational Viewer baseline, then receives explicit read-only identity/security
-- evidence and permission-engine read evidence. No mutation permission is added.
insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version)
select 'phase2-'||substr(md5(auditor_role.role_id||':'||viewer_grant.permission_point),1,32),null,auditor_role.role_id,viewer_grant.permission_point,now(),'phase2-responsibility-cleanup',1
from rbac_roles auditor_role
join rbac_roles viewer_role on viewer_role.tenant_id is null and viewer_role.role_code='VIEWER'
join rbac_role_permissions viewer_grant on viewer_grant.role_id=viewer_role.role_id and viewer_grant.tenant_id is null
where auditor_role.tenant_id is null and auditor_role.role_code='AUDITOR' and auditor_role.status='ACTIVE'
on conflict(role_id,permission_point) do nothing;

insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version)
select 'phase2-'||substr(md5(r.role_id||':'||p.permission_code),1,32),null,r.role_id,p.permission_code,now(),'phase2-responsibility-cleanup',1
from rbac_roles r
join permission_definitions p on p.active=true and p.lifecycle='ACTIVE'
  and p.permission_code like 'permission.%.read'
where r.tenant_id is null and r.role_code='AUDITOR' and r.status='ACTIVE'
on conflict(role_id,permission_point) do nothing;

-- Final invariants: read-only templates never mutate Tenant membership or resource scope;
-- Operator never receives IAM/security administration; Platform Admin never receives permission-engineering authority.
do $$
begin
  if exists(
    select 1 from rbac_role_permissions rp join rbac_roles r on r.role_id=rp.role_id
    where r.tenant_id is null and r.role_code in('VIEWER','AUDITOR','IDENTITY_VIEWER')
      and rp.permission_point in('identity.tenant_membership.manage','resource.scope.approve')) then
    raise exception 'PHASE2_READ_ONLY_ROLE_MUTATION_GRANT_DETECTED' using errcode='23514';
  end if;
  if exists(
    select 1 from rbac_role_permissions rp join rbac_roles r on r.role_id=rp.role_id
    where r.tenant_id is null and r.role_code='OPERATOR'
      and (rp.permission_point like 'identity.%' or rp.permission_point like 'security.%' or rp.permission_point like 'api.iam.%')) then
    raise exception 'PHASE2_OPERATOR_IAM_SECURITY_GRANT_DETECTED' using errcode='23514';
  end if;
  if exists(
    select 1 from rbac_role_permissions rp join rbac_roles r on r.role_id=rp.role_id
    where r.tenant_id is null and r.role_code='PLATFORM_ADMIN' and rp.permission_point like 'permission.%') then
    raise exception 'PHASE2_PLATFORM_ADMIN_PERMISSION_ENGINEERING_GRANT_DETECTED' using errcode='23514';
  end if;
  if not exists(
    select 1 from rbac_role_permissions rp join rbac_roles r on r.role_id=rp.role_id
    where r.tenant_id is null and r.role_code='DISPATCH_ADMIN' and rp.permission_point='admin.dispatch.flow.update') then
    raise exception 'PHASE2_DISPATCH_ADMIN_REQUIRED_GRANT_MISSING' using errcode='23514';
  end if;
end $$;
