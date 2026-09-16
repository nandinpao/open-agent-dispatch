-- OpenDispatch RBAC convergence: ensure canonical Admin UI CRUD authority.
--
-- The canonical AuthorizationService gives INSTANCE_ROOT full catalogued authority for an
-- explicitly addressed Tenant. TENANT_ADMIN must likewise carry the standard Tenant CRUD
-- permissions so R4 backend-driven UI entitlements do not hide the administration controls.

select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','rbac-admin-ui-crud-authority-repair',true);

insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version)
select 'crud-admin-'||substr(md5(r.role_id||':'||p.permission_code),1,24),
       null,r.role_id,p.permission_code,now(),'rbac-admin-ui-crud-authority-repair',1
  from rbac_roles r
  join permission_definitions p on p.permission_code in (
       'identity.user.create','identity.user.read','identity.user.update',
       'identity.tenant_membership.manage','identity.tenant_membership.read',
       'identity.department.manage','identity.department.read',
       'identity.group.manage','identity.group.read','identity.membership.manage',
       'identity.tenant_role.manage','identity.tenant_role.read',
       'identity.role_permission.manage','identity.role_binding.manage','identity.role_binding.read',
       'identity.role_approval.read','identity.role_approval.request',
       'security.mfa.reset','security.session.read','security.session.revoke',
       'security.policy.read','security.policy.manage','security.token.read','security.token.manage',
       'audit.identity.read'
  )
 where r.tenant_id is null and r.role_code='TENANT_ADMIN' and r.status='ACTIVE'
   and p.active=true and p.lifecycle='ACTIVE'
on conflict(role_id,permission_point) do nothing;

-- Fail the deployment rather than shipping an Admin UI where CRUD controls silently disappear.
do $$
declare missing integer;
begin
  select count(*) into missing
    from (values
      ('identity.user.create'),('identity.user.update'),
      ('identity.department.manage'),('identity.group.manage'),('identity.membership.manage'),
      ('identity.tenant_role.manage'),('identity.role_permission.manage'),('identity.role_binding.manage')
    ) required(permission_code)
   where not exists (
     select 1
       from rbac_roles r
       join rbac_role_permissions rp on rp.role_id=r.role_id and rp.tenant_id is null
      where r.tenant_id is null and r.role_code='TENANT_ADMIN' and r.status='ACTIVE'
        and rp.permission_point=required.permission_code
   );
  if missing<>0 then
    raise exception 'TENANT_ADMIN_CRUD_PERMISSION_REPAIR_FAILED:%',missing using errcode='23514';
  end if;
end $$;
