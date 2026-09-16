-- R7 RBAC Hardening and Runtime Acceptance.
-- Adds grant-above-actor boundaries, effective SoD, two-person approvals,
-- immutable before/after evidence and immediate expiration/cache invalidation controls.

select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','r7-rbac-hardening',true);

create table if not exists rbac_critical_change_approvals (
  approval_id varchar(128) primary key,
  tenant_id varchar(64) references tenants(tenant_id),
  operation varchar(64) not null check(operation in('ROLE_BINDING_CREATE','ROLE_PERMISSION_REPLACE')),
  request_hash varchar(64) not null,
  requester_id varchar(128) not null,
  target_type varchar(64) not null,
  target_id varchar(256) not null,
  status varchar(24) not null check(status in('PENDING','APPROVED','REJECTED','CONSUMED','EXPIRED')),
  approver_id varchar(128),
  decision_reason varchar(500),
  requested_at timestamptz not null,
  expires_at timestamptz not null,
  decided_at timestamptz,
  consumed_at timestamptz,
  version bigint not null default 1,
  check(expires_at>requested_at),
  check(approver_id is null or approver_id<>requester_id)
);
create index if not exists idx_rbac_critical_approval_tenant_status
  on rbac_critical_change_approvals(coalesce(tenant_id,'INSTANCE'),status,requested_at desc);
create unique index if not exists uq_rbac_critical_approval_active_request
  on rbac_critical_change_approvals(coalesce(tenant_id,'INSTANCE'),operation,request_hash,requester_id)
  where status in('PENDING','APPROVED');

create table if not exists rbac_change_evidence (
  evidence_id varchar(128) primary key,
  tenant_id varchar(64) references tenants(tenant_id),
  operation varchar(64) not null,
  actor_id varchar(128) not null,
  target_type varchar(64) not null,
  target_id varchar(256) not null,
  approval_id varchar(128) references rbac_critical_change_approvals(approval_id),
  before_json jsonb not null default '{}'::jsonb,
  after_json jsonb not null default '{}'::jsonb,
  added_permissions jsonb not null default '[]'::jsonb,
  removed_permissions jsonb not null default '[]'::jsonb,
  warnings jsonb not null default '[]'::jsonb,
  correlation_id varchar(128),
  audit_reason varchar(500) not null,
  occurred_at timestamptz not null
);
create index if not exists idx_rbac_change_evidence_target
  on rbac_change_evidence(coalesce(tenant_id,'INSTANCE'),target_type,target_id,occurred_at desc);

create or replace function r7_deny_rbac_evidence_mutation() returns trigger language plpgsql as $$
begin
  raise exception using errcode='42501',message='RBAC change evidence is append-only';
end $$;
drop trigger if exists trg_rbac_change_evidence_append_only on rbac_change_evidence;
create trigger trg_rbac_change_evidence_append_only before update or delete on rbac_change_evidence
for each row execute function r7_deny_rbac_evidence_mutation();

create or replace function r7_rbac_scopes_overlap(
  p_tenant_id varchar,p_left_type varchar,p_left_id varchar,p_right_type varchar,p_right_id varchar)
returns boolean language sql stable as $$
  select case
    when p_left_type='INSTANCE' or p_right_type='INSTANCE' then p_left_type=p_right_type
    when p_left_type='TENANT' or p_right_type='TENANT' then true
    when p_left_type='GROUP' or p_right_type='GROUP' then
      p_left_type='GROUP' and p_right_type='GROUP' and p_left_id=p_right_id
    when p_left_type='DEPARTMENT' and p_right_type='DEPARTMENT' then exists(
      select 1 from org_department_closure c
      where c.tenant_id=p_tenant_id and
        ((c.ancestor_department_id=p_left_id and c.descendant_department_id=p_right_id)
          or (c.ancestor_department_id=p_right_id and c.descendant_department_id=p_left_id)))
    else false end
$$;

create or replace function r7_rbac_scope_contains(
  p_tenant_id varchar,p_container_type varchar,p_container_id varchar,p_candidate_type varchar,p_candidate_id varchar)
returns boolean language sql stable as $$
  select case
    when p_container_type='INSTANCE' then true
    when p_container_type='TENANT' then p_candidate_type in('TENANT','DEPARTMENT','GROUP')
    when p_container_type='GROUP' then p_candidate_type='GROUP' and p_container_id=p_candidate_id
    when p_container_type='DEPARTMENT' then p_candidate_type='DEPARTMENT' and exists(
      select 1 from org_department_closure c where c.tenant_id=p_tenant_id
        and c.ancestor_department_id=p_container_id and c.descendant_department_id=p_candidate_id)
    else false end
$$;

-- Effective SoD includes direct User bindings and roles inherited through active Group membership.
create or replace function r7_validate_effective_separation_of_duties() returns trigger language plpgsql as $$
declare affected_user varchar(128); existing record; sod record;
begin
  if new.status<>'ACTIVE' or new.effective_at>now() or (new.expires_at is not null and new.expires_at<=now()) then return new; end if;
  for affected_user in
    select new.principal_id where new.principal_type='USER'
    union
    select gm.user_id from org_group_memberships gm
      where new.principal_type='GROUP' and gm.tenant_id=new.tenant_id and gm.group_id=new.principal_id
        and gm.status='ACTIVE' and gm.effective_at<=now() and (gm.expires_at is null or gm.expires_at>now())
  loop
    for existing in
      select b.* from rbac_principal_role_bindings b
      where b.tenant_id=new.tenant_id and b.binding_id<>new.binding_id and b.status='ACTIVE'
        and b.effective_at<=now() and (b.expires_at is null or b.expires_at>now())
        and (b.principal_type='USER' and b.principal_id=affected_user
          or b.principal_type='GROUP' and exists(select 1 from org_group_memberships gm
              where gm.tenant_id=b.tenant_id and gm.group_id=b.principal_id and gm.user_id=affected_user
                and gm.status='ACTIVE' and gm.effective_at<=now() and (gm.expires_at is null or gm.expires_at>now())))
    loop
      for sod in select * from rbac_separation_of_duties_rules r
        where r.status='ACTIVE' and (r.tenant_id is null or r.tenant_id=new.tenant_id)
          and ((r.left_role_id=new.role_id and r.right_role_id=existing.role_id)
            or (r.right_role_id=new.role_id and r.left_role_id=existing.role_id))
      loop
        if not sod.scope_overlap_required or r7_rbac_scopes_overlap(new.tenant_id,new.scope_type,new.scope_id,existing.scope_type,existing.scope_id) then
          raise exception using errcode='23514',message='ROLE_SEPARATION_OF_DUTIES_CONFLICT';
        end if;
      end loop;
    end loop;
  end loop;
  return new;
end $$;
drop trigger if exists trg_r7_effective_sod on rbac_principal_role_bindings;
create trigger trg_r7_effective_sod before insert or update of principal_type,principal_id,role_id,scope_type,scope_id,status,effective_at,expires_at
on rbac_principal_role_bindings for each row execute function r7_validate_effective_separation_of_duties();

-- A Group membership can create effective access after the Group Role Binding was already approved.
-- Validate that transition as an authorization mutation as well, otherwise inherited SoD and self-binding
-- could be bypassed by adding the user to an already privileged Group.
create or replace function r7_validate_group_membership_effective_access() returns trigger language plpgsql as $$
declare
  candidate record;
  existing record;
  sod record;
  actor varchar(128):=nullif(current_setting('app.current_actor_id',true),'');
  previous_group_id varchar(128):=case when tg_op='UPDATE' then old.group_id else null end;
begin
  if new.status<>'ACTIVE' or new.effective_at>now() or (new.expires_at is not null and new.expires_at<=now()) then
    return new;
  end if;

  if actor is not null and actor=new.user_id and exists(
    select 1 from rbac_principal_role_bindings b
    where b.tenant_id=new.tenant_id and b.principal_type='GROUP' and b.principal_id=new.group_id
      and b.status='ACTIVE' and b.effective_at<=now() and (b.expires_at is null or b.expires_at>now())) then
    raise exception using errcode='23514',message='RBAC_SELF_BINDING_FORBIDDEN';
  end if;

  for candidate in
    select b.* from rbac_principal_role_bindings b
    where b.tenant_id=new.tenant_id and b.principal_type='GROUP' and b.principal_id=new.group_id
      and b.status='ACTIVE' and b.effective_at<=now() and (b.expires_at is null or b.expires_at>now())
  loop
    for existing in
      select b.* from rbac_principal_role_bindings b
      where b.tenant_id=new.tenant_id and b.binding_id<>candidate.binding_id and b.status='ACTIVE'
        and b.effective_at<=now() and (b.expires_at is null or b.expires_at>now())
        and not(previous_group_id is not null and previous_group_id<>new.group_id
          and b.principal_type='GROUP' and b.principal_id=previous_group_id)
        and (b.principal_type='USER' and b.principal_id=new.user_id
          or b.principal_type='GROUP' and exists(
            select 1 from org_group_memberships gm
            where gm.tenant_id=b.tenant_id and gm.group_id=b.principal_id and gm.user_id=new.user_id
              and gm.status='ACTIVE' and gm.effective_at<=now() and (gm.expires_at is null or gm.expires_at>now())))
    loop
      for sod in select * from rbac_separation_of_duties_rules r
        where r.status='ACTIVE' and (r.tenant_id is null or r.tenant_id=new.tenant_id)
          and ((r.left_role_id=candidate.role_id and r.right_role_id=existing.role_id)
            or (r.right_role_id=candidate.role_id and r.left_role_id=existing.role_id))
      loop
        if not sod.scope_overlap_required or r7_rbac_scopes_overlap(
            new.tenant_id,candidate.scope_type,candidate.scope_id,existing.scope_type,existing.scope_id) then
          raise exception using errcode='23514',message='ROLE_SEPARATION_OF_DUTIES_CONFLICT';
        end if;
      end loop;
    end loop;
  end loop;
  return new;
end $$;
drop trigger if exists trg_r7_group_membership_effective_access on org_group_memberships;
create trigger trg_r7_group_membership_effective_access
before insert or update of user_id,group_id,status,effective_at,expires_at on org_group_memberships
for each row execute function r7_validate_group_membership_effective_access();

-- Group expansion is part of the effective Principal. Every membership mutation must atomically advance
-- the Tenant policy/security epochs so no cached inherited grant can survive membership add/remove/change.
create or replace function r7_invalidate_group_membership_authorization() returns trigger language plpgsql as $$
declare
  affected_tenant varchar(64):=case when tg_op='DELETE' then old.tenant_id else new.tenant_id end;
  actor varchar(128):=coalesce(nullif(current_setting('app.current_actor_id',true),''),'r7-group-membership');
begin
  perform phase1a4_increment_policy_version(affected_tenant,actor);
  if tg_op='DELETE' then return old; end if;
  return new;
end $$;
drop trigger if exists trg_r7_group_membership_authorization_version on org_group_memberships;
create trigger trg_r7_group_membership_authorization_version
after insert or update or delete on org_group_memberships
for each row execute function r7_invalidate_group_membership_authorization();

-- Database final defense against direct self-binding. Group self-binding is also denied by the application
-- after Principal Expansion; this trigger protects direct SQL and alternate adapters.
create or replace function r7_reject_direct_self_binding() returns trigger language plpgsql as $$
declare actor varchar(128):=nullif(current_setting('app.current_actor_id',true),'');
begin
  if actor is not null and new.principal_type='USER' and new.principal_id=actor then
    raise exception using errcode='23514',message='RBAC_SELF_BINDING_FORBIDDEN';
  end if;
  return new;
end $$;
drop trigger if exists trg_r7_direct_self_binding on rbac_principal_role_bindings;
create trigger trg_r7_direct_self_binding before insert on rbac_principal_role_bindings
for each row execute function r7_reject_direct_self_binding();

-- R7 permissions are first-class Atomic Permissions. Publish a new immutable Catalog revision
-- rather than mutating the active projection directly.
insert into permission_catalog_revisions(
  revision_id,revision_code,revision_number,status,content_hash,description,supersedes_revision_id,
  created_at,created_by,published_at,published_by,version)
select '00000000-0000-0000-0000-000000000015'::uuid,'R7-0.8.2',coalesce(max(revision_number),0)+1,
  'DRAFT','DRAFT:UNPUBLISHED','R7 critical RBAC approval and hardening permissions.',
  (select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE'),
  now(),'r7-rbac-hardening',null,null,1
from permission_catalog_revisions
on conflict(revision_id) do nothing;

insert into permission_catalog_revision_entries(
  revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,
  lifecycle,allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,
  retired_at,updated_at,updated_by,version)
select '00000000-0000-0000-0000-000000000015'::uuid,e.permission_code,e.owner_module,e.resource_type,
  e.action_code,e.description,e.risk_level,e.risk_lane,e.lifecycle,e.allowed_scope_types,e.system_managed,
  e.replacement_permission_code,e.introduced_at,e.deprecated_at,e.retired_at,now(),'r7-rbac-hardening',1
from permission_catalog_revision_entries e
join permission_catalog_active_revision a on a.singleton_id='ACTIVE' and a.revision_id=e.revision_id
on conflict(revision_id,permission_code) do nothing;

insert into permission_catalog_revision_aliases(
  revision_id,alias_code,canonical_permission_code,alias_type,valid_from,valid_until,reason,created_by,created_at,version)
select '00000000-0000-0000-0000-000000000015'::uuid,a.alias_code,a.canonical_permission_code,
  a.alias_type,a.valid_from,a.valid_until,a.reason,'r7-rbac-hardening',now(),1
from permission_catalog_revision_aliases a
join permission_catalog_active_revision active on active.singleton_id='ACTIVE' and active.revision_id=a.revision_id
on conflict(revision_id,alias_code) do nothing;

insert into permission_catalog_revision_entries(
  revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,
  lifecycle,allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,
  retired_at,updated_at,updated_by,version)
values
 ('00000000-0000-0000-0000-000000000015','identity.role_approval.read','rbac','RBAC_APPROVAL','READ',
  'Read critical RBAC approval requests and immutable evidence.','HIGH','READ','ACTIVE',array['INSTANCE','TENANT'],true,null,now(),null,null,now(),'r7-rbac-hardening',1),
 ('00000000-0000-0000-0000-000000000015','identity.role_approval.request','rbac','RBAC_APPROVAL','REQUEST',
  'Request two-person approval for a critical RBAC change.','CRITICAL','CRITICAL','ACTIVE',array['INSTANCE','TENANT'],true,null,now(),null,null,now(),'r7-rbac-hardening',1),
 ('00000000-0000-0000-0000-000000000015','identity.role_approval.approve','rbac','RBAC_APPROVAL','APPROVE',
  'Approve or reject a critical RBAC change requested by another principal.','CRITICAL','CRITICAL','ACTIVE',array['INSTANCE','TENANT'],true,null,now(),null,null,now(),'r7-rbac-hardening',1)
on conflict(revision_id,permission_code) do update set description=excluded.description,risk_level=excluded.risk_level,
  risk_lane=excluded.risk_lane,lifecycle='ACTIVE',allowed_scope_types=excluded.allowed_scope_types,
  updated_at=now(),updated_by='r7-rbac-hardening',version=permission_catalog_revision_entries.version+1;

select set_config('app.permission_catalog_publish_revision_id','00000000-0000-0000-0000-000000000015',true);

insert into permission_definitions(
  permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,
  active,version,owner_module,risk_lane,lifecycle,catalog_revision_id,replacement_permission_code,
  introduced_at,deprecated_at,retired_at,updated_at,updated_by)
select permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,
  lifecycle<>'RETIRED',1,owner_module,risk_lane,lifecycle,revision_id,replacement_permission_code,
  introduced_at,deprecated_at,retired_at,updated_at,updated_by
from permission_catalog_revision_entries where revision_id='00000000-0000-0000-0000-000000000015'::uuid
on conflict(permission_code) do update set resource_type=excluded.resource_type,action_code=excluded.action_code,
  description=excluded.description,risk_level=excluded.risk_level,allowed_scope_types=excluded.allowed_scope_types,
  system_managed=excluded.system_managed,active=excluded.active,owner_module=excluded.owner_module,
  risk_lane=excluded.risk_lane,lifecycle=excluded.lifecycle,catalog_revision_id=excluded.catalog_revision_id,
  replacement_permission_code=excluded.replacement_permission_code,deprecated_at=excluded.deprecated_at,
  retired_at=excluded.retired_at,updated_at=excluded.updated_at,updated_by=excluded.updated_by,
  version=permission_definitions.version+1;

update permission_catalog_revisions set status='SUPERSEDED',version=version+1
where revision_id=(select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE')
  and revision_id<>'00000000-0000-0000-0000-000000000015'::uuid and status='PUBLISHED';

update permission_catalog_revisions set status='PUBLISHED',content_hash=(
  with catalog_lines as (
    select 'P|'||permission_code||'|'||owner_module||'|'||resource_type||'|'||action_code||'|'||description||'|'||
      risk_level||'|'||risk_lane||'|'||lifecycle||'|'||coalesce(array_to_string(allowed_scope_types,','),'')||'|'||
      system_managed::text||'|'||coalesce(replacement_permission_code,'') as line
    from permission_catalog_revision_entries where revision_id='00000000-0000-0000-0000-000000000015'::uuid
    union all
    select 'A|'||alias_code||'|'||canonical_permission_code||'|'||alias_type||'|'||
      coalesce(to_char(valid_from at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||
      coalesce(to_char(valid_until at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||reason
    from permission_catalog_revision_aliases where revision_id='00000000-0000-0000-0000-000000000015'::uuid)
  select 'sha256:'||encode(sha256(convert_to(coalesce(string_agg(line,E'\n' order by line),''),'UTF8')),'hex') from catalog_lines),
  published_at=now(),published_by='r7-rbac-hardening',version=version+1
where revision_id='00000000-0000-0000-0000-000000000015'::uuid and status='DRAFT';

update permission_catalog_active_revision set revision_id='00000000-0000-0000-0000-000000000015'::uuid,
  activated_at=now(),activated_by='r7-rbac-hardening',version=version+1 where singleton_id='ACTIVE';

insert into permission_catalog_publication_events(
  publication_id,revision_id,previous_revision_id,content_hash,entry_count,alias_count,actor_id,audit_reason,correlation_id,published_at)
select '00000000-0000-0000-0000-000000005015'::uuid,r.revision_id,r.supersedes_revision_id,r.content_hash,
  (select count(*)::integer from permission_catalog_revision_entries e where e.revision_id=r.revision_id),
  (select count(*)::integer from permission_catalog_revision_aliases a where a.revision_id=r.revision_id),
  'r7-rbac-hardening','R7 hardening Catalog publication','r7-rbac-hardening',coalesce(r.published_at,now())
from permission_catalog_revisions r where r.revision_id='00000000-0000-0000-0000-000000000015'::uuid
on conflict(publication_id) do nothing;

insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version)
values
 ('grant-system-admin-role-approval-read',null,'role-system-admin','identity.role_approval.read',now(),'r7-rbac-hardening',1),
 ('grant-system-admin-role-approval-request',null,'role-system-admin','identity.role_approval.request',now(),'r7-rbac-hardening',1),
 ('grant-system-admin-role-approval-approve',null,'role-system-admin','identity.role_approval.approve',now(),'r7-rbac-hardening',1),
 ('grant-tenant-admin-role-approval-read',null,'role-tenant-admin','identity.role_approval.read',now(),'r7-rbac-hardening',1),
 ('grant-tenant-admin-role-approval-request',null,'role-tenant-admin','identity.role_approval.request',now(),'r7-rbac-hardening',1),
 ('grant-security-admin-role-approval-read',null,'role-security-admin','identity.role_approval.read',now(),'r7-rbac-hardening',1),
 ('grant-security-admin-role-approval-approve',null,'role-security-admin','identity.role_approval.approve',now(),'r7-rbac-hardening',1),
 ('grant-auditor-role-approval-read',null,'role-auditor','identity.role_approval.read',now(),'r7-rbac-hardening',1)
on conflict(role_id,permission_point) do nothing;

insert into reason_code_catalog(reason_code,http_status,category,retryable,message_template)
values
 ('RBAC_GRANT_ABOVE_ACTOR_FORBIDDEN',403,'AUTHORIZATION',false,'The actor cannot grant permissions they do not effectively hold.'),
 ('RBAC_ASSIGNABLE_ROLE_BOUNDARY_VIOLATION',403,'AUTHORIZATION',false,'The selected Role is outside the actor assignable Role boundary.'),
 ('RBAC_ASSIGNABLE_PERMISSION_BOUNDARY_VIOLATION',403,'AUTHORIZATION',false,'The permission set is outside the actor assignable Permission boundary.'),
 ('RBAC_SELF_BINDING_FORBIDDEN',409,'AUTHORIZATION',false,'A principal cannot assign a Role to itself or an inherited Group.'),
 ('RBAC_SELF_ESCALATION_FORBIDDEN',409,'AUTHORIZATION',false,'The requested change would elevate the requester.'),
 ('RBAC_CRITICAL_APPROVAL_REQUIRED',409,'AUTHORIZATION',false,'Critical RBAC changes require approval by a different principal.'),
 ('RBAC_CRITICAL_APPROVAL_SELF_APPROVAL_FORBIDDEN',409,'AUTHORIZATION',false,'The requester cannot approve their own critical RBAC change.'),
 ('RBAC_CRITICAL_APPROVAL_EXPIRED',409,'AUTHORIZATION',false,'The critical RBAC approval has expired.'),
 ('RBAC_ROOT_MFA_REQUIRED',403,'SECURITY',false,'Root sensitive writes require MFA.'),
 ('RBAC_ROOT_STEP_UP_REQUIRED',403,'SECURITY',false,'Root sensitive writes require recent step-up authentication.'),
 ('RBAC_ROOT_SESSION_TOO_LONG',403,'SECURITY',false,'Root break-glass session lifetime is too long.')
on conflict(reason_code) do update set http_status=excluded.http_status,category=excluded.category,
  retryable=excluded.retryable,message_template=excluded.message_template,active=true;

-- Increment policy versions so all existing authorization caches are invalidated immediately.
update rbac_policy_versions set policy_version=policy_version+1,updated_at=now(),updated_by='r7-rbac-hardening';

insert into permission_entry_point_inventory(
 entry_point_id,entry_point_type,application_id,owner_module,display_name,route_pattern,http_method,authority_state,
 target_permission_code,legacy_authority_type,legacy_authorities,resource_type,resource_resolver_id,exemption_reason,
 migration_deadline,manifest_revision,source_ref,source_hash,last_verified_at,created_by,updated_by)
values
 ('REST:GET:/api/admin/access/platform/rbac-approvals','REST','control-plane-app','iam-api','IamPlatformRoleController.rbacApprovals','/api/admin/access/platform/rbac-approvals','GET','TARGET_ONLY','identity.role_approval.read',null,'[]'::jsonb,'IAM_PLATFORM_ROLE','R3_ROUTE_RESOURCE_RESOLVER',null,null,'r7-rbac-hardening-2026-08-04','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/IamPlatformRoleController.java#rbacApprovals','14d62f33ecc997e0a012f942af1859277d4be8e6870abbbf131fb8b46a9f11c9',now(),'r7-rbac-hardening','r7-rbac-hardening'),
 ('REST:GET:/api/admin/access/platform/rbac-approvals/{approvalId}','REST','control-plane-app','iam-api','IamPlatformRoleController.rbacApproval','/api/admin/access/platform/rbac-approvals/{approvalId}','GET','TARGET_ONLY','identity.role_approval.read',null,'[]'::jsonb,'IAM_PLATFORM_ROLE','R3_PATH_RESOURCE_RESOLVER',null,null,'r7-rbac-hardening-2026-08-04','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/IamPlatformRoleController.java#rbacApproval','14d62f33ecc997e0a012f942af1859277d4be8e6870abbbf131fb8b46a9f11c9',now(),'r7-rbac-hardening','r7-rbac-hardening'),
 ('REST:POST:/api/admin/access/platform/rbac-approvals/{approvalId}/approve','REST','control-plane-app','iam-api','IamPlatformRoleController.approveRbacChange','/api/admin/access/platform/rbac-approvals/{approvalId}/approve','POST','TARGET_ONLY','identity.role_approval.approve',null,'[]'::jsonb,'IAM_PLATFORM_ROLE','R3_PATH_RESOURCE_RESOLVER',null,null,'r7-rbac-hardening-2026-08-04','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/IamPlatformRoleController.java#approveRbacChange','14d62f33ecc997e0a012f942af1859277d4be8e6870abbbf131fb8b46a9f11c9',now(),'r7-rbac-hardening','r7-rbac-hardening'),
 ('REST:POST:/api/admin/access/platform/rbac-approvals/{approvalId}/reject','REST','control-plane-app','iam-api','IamPlatformRoleController.rejectRbacChange','/api/admin/access/platform/rbac-approvals/{approvalId}/reject','POST','TARGET_ONLY','identity.role_approval.approve',null,'[]'::jsonb,'IAM_PLATFORM_ROLE','R3_PATH_RESOURCE_RESOLVER',null,null,'r7-rbac-hardening-2026-08-04','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/IamPlatformRoleController.java#rejectRbacChange','14d62f33ecc997e0a012f942af1859277d4be8e6870abbbf131fb8b46a9f11c9',now(),'r7-rbac-hardening','r7-rbac-hardening'),
 ('REST:POST:/api/admin/access/platform/role-bindings/approval-requests','REST','control-plane-app','iam-api','IamPlatformRoleController.requestRoleBindingApproval','/api/admin/access/platform/role-bindings/approval-requests','POST','TARGET_ONLY','identity.role_approval.request',null,'[]'::jsonb,'IAM_PLATFORM_ROLE','R3_ROUTE_RESOURCE_RESOLVER',null,null,'r7-rbac-hardening-2026-08-04','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/IamPlatformRoleController.java#requestRoleBindingApproval','14d62f33ecc997e0a012f942af1859277d4be8e6870abbbf131fb8b46a9f11c9',now(),'r7-rbac-hardening','r7-rbac-hardening'),
 ('REST:POST:/api/admin/access/platform/role-bindings/hardening-preview','REST','control-plane-app','iam-api','IamPlatformRoleController.previewRoleBindingHardening','/api/admin/access/platform/role-bindings/hardening-preview','POST','TARGET_ONLY','identity.role_binding.read',null,'[]'::jsonb,'IAM_PLATFORM_ROLE','R3_ROUTE_RESOURCE_RESOLVER',null,null,'r7-rbac-hardening-2026-08-04','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/IamPlatformRoleController.java#previewRoleBindingHardening','14d62f33ecc997e0a012f942af1859277d4be8e6870abbbf131fb8b46a9f11c9',now(),'r7-rbac-hardening','r7-rbac-hardening'),
 ('REST:POST:/api/admin/access/platform/roles/{roleId}/permissions/approval-requests','REST','control-plane-app','iam-api','IamPlatformRoleController.requestRolePermissionApproval','/api/admin/access/platform/roles/{roleId}/permissions/approval-requests','POST','TARGET_ONLY','identity.role_approval.request',null,'[]'::jsonb,'IAM_PLATFORM_ROLE','R3_PATH_RESOURCE_RESOLVER',null,null,'r7-rbac-hardening-2026-08-04','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/IamPlatformRoleController.java#requestRolePermissionApproval','14d62f33ecc997e0a012f942af1859277d4be8e6870abbbf131fb8b46a9f11c9',now(),'r7-rbac-hardening','r7-rbac-hardening'),
 ('REST:POST:/api/admin/access/platform/roles/{roleId}/permissions/hardening-preview','REST','control-plane-app','iam-api','IamPlatformRoleController.previewRolePermissionHardening','/api/admin/access/platform/roles/{roleId}/permissions/hardening-preview','POST','TARGET_ONLY','identity.platform_role.read',null,'[]'::jsonb,'IAM_PLATFORM_ROLE','R3_PATH_RESOURCE_RESOLVER',null,null,'r7-rbac-hardening-2026-08-04','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/IamPlatformRoleController.java#previewRolePermissionHardening','14d62f33ecc997e0a012f942af1859277d4be8e6870abbbf131fb8b46a9f11c9',now(),'r7-rbac-hardening','r7-rbac-hardening'),
 ('REST:GET:/api/admin/access/tenants/{tenantId}/rbac-approvals','REST','control-plane-app','iam-api','UnifiedAccessManagementController.rbacApprovals','/api/admin/access/tenants/{tenantId}/rbac-approvals','GET','TARGET_ONLY','identity.role_approval.read',null,'[]'::jsonb,'UNIFIED_ACCESS_MANAGEMENT','R3_PATH_RESOURCE_RESOLVER',null,null,'r7-rbac-hardening-2026-08-04','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/UnifiedAccessManagementController.java#rbacApprovals','6427fc40fdf4fceff751e2409ea7762944570677634f60b91346e8b5941cda48',now(),'r7-rbac-hardening','r7-rbac-hardening'),
 ('REST:GET:/api/admin/access/tenants/{tenantId}/rbac-approvals/{approvalId}','REST','control-plane-app','iam-api','UnifiedAccessManagementController.rbacApproval','/api/admin/access/tenants/{tenantId}/rbac-approvals/{approvalId}','GET','TARGET_ONLY','identity.role_approval.read',null,'[]'::jsonb,'UNIFIED_ACCESS_MANAGEMENT','R3_PATH_RESOURCE_RESOLVER',null,null,'r7-rbac-hardening-2026-08-04','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/UnifiedAccessManagementController.java#rbacApproval','6427fc40fdf4fceff751e2409ea7762944570677634f60b91346e8b5941cda48',now(),'r7-rbac-hardening','r7-rbac-hardening'),
 ('REST:POST:/api/admin/access/tenants/{tenantId}/rbac-approvals/{approvalId}/approve','REST','control-plane-app','iam-api','UnifiedAccessManagementController.approveRbacChange','/api/admin/access/tenants/{tenantId}/rbac-approvals/{approvalId}/approve','POST','TARGET_ONLY','identity.role_approval.approve',null,'[]'::jsonb,'UNIFIED_ACCESS_MANAGEMENT','R3_PATH_RESOURCE_RESOLVER',null,null,'r7-rbac-hardening-2026-08-04','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/UnifiedAccessManagementController.java#approveRbacChange','6427fc40fdf4fceff751e2409ea7762944570677634f60b91346e8b5941cda48',now(),'r7-rbac-hardening','r7-rbac-hardening'),
 ('REST:POST:/api/admin/access/tenants/{tenantId}/rbac-approvals/{approvalId}/reject','REST','control-plane-app','iam-api','UnifiedAccessManagementController.rejectRbacChange','/api/admin/access/tenants/{tenantId}/rbac-approvals/{approvalId}/reject','POST','TARGET_ONLY','identity.role_approval.approve',null,'[]'::jsonb,'UNIFIED_ACCESS_MANAGEMENT','R3_PATH_RESOURCE_RESOLVER',null,null,'r7-rbac-hardening-2026-08-04','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/UnifiedAccessManagementController.java#rejectRbacChange','6427fc40fdf4fceff751e2409ea7762944570677634f60b91346e8b5941cda48',now(),'r7-rbac-hardening','r7-rbac-hardening'),
 ('REST:POST:/api/admin/access/tenants/{tenantId}/role-bindings/approval-requests','REST','control-plane-app','iam-api','UnifiedAccessManagementController.requestRoleBindingApproval','/api/admin/access/tenants/{tenantId}/role-bindings/approval-requests','POST','TARGET_ONLY','identity.role_approval.request',null,'[]'::jsonb,'UNIFIED_ACCESS_MANAGEMENT','R3_PATH_RESOURCE_RESOLVER',null,null,'r7-rbac-hardening-2026-08-04','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/UnifiedAccessManagementController.java#requestRoleBindingApproval','6427fc40fdf4fceff751e2409ea7762944570677634f60b91346e8b5941cda48',now(),'r7-rbac-hardening','r7-rbac-hardening'),
 ('REST:POST:/api/admin/access/tenants/{tenantId}/role-bindings/hardening-preview','REST','control-plane-app','iam-api','UnifiedAccessManagementController.previewRoleBindingHardening','/api/admin/access/tenants/{tenantId}/role-bindings/hardening-preview','POST','TARGET_ONLY','identity.role_binding.read',null,'[]'::jsonb,'UNIFIED_ACCESS_MANAGEMENT','R3_PATH_RESOURCE_RESOLVER',null,null,'r7-rbac-hardening-2026-08-04','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/UnifiedAccessManagementController.java#previewRoleBindingHardening','6427fc40fdf4fceff751e2409ea7762944570677634f60b91346e8b5941cda48',now(),'r7-rbac-hardening','r7-rbac-hardening'),
 ('REST:POST:/api/admin/access/tenants/{tenantId}/roles/{roleId}/permissions/approval-requests','REST','control-plane-app','iam-api','UnifiedAccessManagementController.requestRolePermissionApproval','/api/admin/access/tenants/{tenantId}/roles/{roleId}/permissions/approval-requests','POST','TARGET_ONLY','identity.role_approval.request',null,'[]'::jsonb,'UNIFIED_ACCESS_MANAGEMENT','R3_PATH_RESOURCE_RESOLVER',null,null,'r7-rbac-hardening-2026-08-04','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/UnifiedAccessManagementController.java#requestRolePermissionApproval','6427fc40fdf4fceff751e2409ea7762944570677634f60b91346e8b5941cda48',now(),'r7-rbac-hardening','r7-rbac-hardening'),
 ('REST:POST:/api/admin/access/tenants/{tenantId}/roles/{roleId}/permissions/hardening-preview','REST','control-plane-app','iam-api','UnifiedAccessManagementController.previewRolePermissionHardening','/api/admin/access/tenants/{tenantId}/roles/{roleId}/permissions/hardening-preview','POST','TARGET_ONLY','identity.role_binding.read',null,'[]'::jsonb,'UNIFIED_ACCESS_MANAGEMENT','R3_PATH_RESOURCE_RESOLVER',null,null,'r7-rbac-hardening-2026-08-04','ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/UnifiedAccessManagementController.java#previewRolePermissionHardening','6427fc40fdf4fceff751e2409ea7762944570677634f60b91346e8b5941cda48',now(),'r7-rbac-hardening','r7-rbac-hardening')
on conflict(entry_point_id) do update set
 entry_point_type=excluded.entry_point_type,application_id=excluded.application_id,owner_module=excluded.owner_module,
 display_name=excluded.display_name,route_pattern=excluded.route_pattern,http_method=excluded.http_method,
 authority_state='TARGET_ONLY',target_permission_code=excluded.target_permission_code,legacy_authority_type=null,
 legacy_authorities='[]'::jsonb,resource_type=excluded.resource_type,resource_resolver_id=excluded.resource_resolver_id,
 exemption_reason=null,migration_deadline=null,manifest_revision=excluded.manifest_revision,source_ref=excluded.source_ref,
 source_hash=excluded.source_hash,last_verified_at=now(),updated_at=now(),updated_by='r7-rbac-hardening',
 version=permission_entry_point_inventory.version+1;

-- Release gates validate the current R6 + R7 runtime inventory only.
-- Historical inventory rows intentionally remain as migration evidence and may be EXEMPT or retired.
do $$
declare
  v_current_target_count integer;
  v_current_access_count integer;
  v_r7_route_count integer;
begin
  select count(*)
    into v_current_target_count
    from permission_entry_point_inventory
   where manifest_revision in (
       'r6-legacy-compatibility-removal-2026-08-04',
       'r7-rbac-hardening-2026-08-04'
     )
     and authority_state='TARGET_ONLY';

  if v_current_target_count <> 700 then
    raise exception
      'R7_AUTHORIZATION_CURRENT_INVENTORY_COUNT_MISMATCH expected=700 actual=%',
      v_current_target_count;
  end if;

  select count(*)
    into v_current_access_count
    from permission_entry_point_inventory
   where manifest_revision in (
       'r6-legacy-compatibility-removal-2026-08-04',
       'r7-rbac-hardening-2026-08-04'
     )
     and route_pattern like '/api/admin/access/%'
     and authority_state='TARGET_ONLY';

  if v_current_access_count <> 149 then
    raise exception
      'R7_AUTHORIZATION_CURRENT_ACCESS_INVENTORY_COUNT_MISMATCH expected=149 actual=%',
      v_current_access_count;
  end if;

  select count(*)
    into v_r7_route_count
    from permission_entry_point_inventory
   where manifest_revision='r7-rbac-hardening-2026-08-04'
     and authority_state='TARGET_ONLY';

  if v_r7_route_count <> 16 then
    raise exception
      'R7_AUTHORIZATION_R7_ROUTE_COUNT_MISMATCH expected=16 actual=%',
      v_r7_route_count;
  end if;

  if exists(
    select 1
      from permission_entry_point_inventory
     where manifest_revision in (
         'r6-legacy-compatibility-removal-2026-08-04',
         'r7-rbac-hardening-2026-08-04'
       )
       and authority_state <> 'TARGET_ONLY'
  ) then
    raise exception 'R7_AUTHORIZATION_NON_TARGET_CURRENT_ENTRY_POINT';
  end if;

  if exists(
    select 1
      from permission_entry_point_inventory i
      left join permission_definitions p
        on p.permission_code=i.target_permission_code
     where i.manifest_revision in (
         'r6-legacy-compatibility-removal-2026-08-04',
         'r7-rbac-hardening-2026-08-04'
       )
       and i.authority_state='TARGET_ONLY'
       and (
         i.target_permission_code is null
         or p.permission_code is null
         or not p.active
       )
  ) then
    raise exception 'R7_AUTHORIZATION_UNMAPPED_CURRENT_PERMISSION';
  end if;

  if exists(
    select 1
      from permission_entry_point_inventory
     where manifest_revision in (
         'r6-legacy-compatibility-removal-2026-08-04',
         'r7-rbac-hardening-2026-08-04'
       )
       and authority_state='TARGET_ONLY'
       and (
         resource_resolver_id is null
         or resource_resolver_id in ('','NONE')
       )
  ) then
    raise exception 'R7_AUTHORIZATION_MISSING_CURRENT_RESOURCE_RESOLVER';
  end if;
end $$;
