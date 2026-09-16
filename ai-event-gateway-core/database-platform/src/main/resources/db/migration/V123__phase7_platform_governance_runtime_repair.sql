-- Phase 7 Build Fix 5: platform-governance Runtime repair.
-- Platform enforcement-activation routes are INSTANCE scoped and must never resolve a Tenant resource.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','phase7-build-fix5',true);
select set_config('app.current_audit_reason','Repair platform governance Runtime scope, RLS evidence, and route metadata',true);

update permission_entry_point_inventory
set resource_resolver_id='NONE',
    last_verified_at=now(),
    updated_at=now(),
    updated_by='phase7-build-fix5',
    version=version+1
where route_pattern like '/api/platform/enforcement-activation%'
  and resource_resolver_id<>'NONE';

-- Preserve fail-closed INSTANCE evidence. INSTANCE rows are represented by tenant_id IS NULL.
drop policy if exists rbac_shadow_decision_scope on rbac_shadow_decisions;
create policy rbac_shadow_decision_scope on rbac_shadow_decisions
 for all to opendispatch_runtime
 using (
   (tenant_id is null and iam_current_tenant_id()='INSTANCE')
   or tenant_id=iam_current_tenant_id()
 )
 with check (
   (tenant_id is null and iam_current_tenant_id()='INSTANCE')
   or tenant_id=iam_current_tenant_id()
 );

update rbac_policy_versions
set policy_version=policy_version+1,updated_at=now(),updated_by='phase7-build-fix5';
