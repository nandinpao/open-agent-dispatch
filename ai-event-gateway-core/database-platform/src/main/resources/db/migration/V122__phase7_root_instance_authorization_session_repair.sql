-- Phase 7 Root instance authorization and session continuity repair.
-- INSTANCE_ROOT must be able to authorize and audit Instance-scoped administration routes,
-- while Tenant rows remain isolated by the transaction-local IAM Tenant context.


alter table rbac_policy_versions enable row level security;
alter table rbac_policy_versions force row level security;
drop policy if exists rbac_tenant_only on rbac_policy_versions;
drop policy if exists rbac_policy_version_scope on rbac_policy_versions;
create policy rbac_policy_version_scope on rbac_policy_versions
  using (
    (tenant_id is null and scope_type='INSTANCE' and scope_id='INSTANCE'
      and iam_current_tenant_id()='INSTANCE')
    or tenant_id=iam_current_tenant_id()
  )
  with check (
    (tenant_id is null and scope_type='INSTANCE' and scope_id='INSTANCE'
      and iam_current_tenant_id()='INSTANCE')
    or tenant_id=iam_current_tenant_id()
  );

alter table rbac_decision_audits enable row level security;
alter table rbac_decision_audits force row level security;
drop policy if exists rbac_tenant_only on rbac_decision_audits;
drop policy if exists rbac_decision_scope on rbac_decision_audits;
create policy rbac_decision_scope on rbac_decision_audits
  using (
    (tenant_id is null and iam_current_tenant_id()='INSTANCE')
    or tenant_id=iam_current_tenant_id()
  )
  with check (
    (tenant_id is null and iam_current_tenant_id()='INSTANCE')
    or tenant_id=iam_current_tenant_id()
  );

alter table rbac_shadow_decisions enable row level security;
alter table rbac_shadow_decisions force row level security;
drop policy if exists rbac_tenant_only on rbac_shadow_decisions;
drop policy if exists rbac_shadow_decision_scope on rbac_shadow_decisions;
create policy rbac_shadow_decision_scope on rbac_shadow_decisions
  using (
    (tenant_id is null and iam_current_tenant_id()='INSTANCE')
    or tenant_id=iam_current_tenant_id()
  )
  with check (
    (tenant_id is null and iam_current_tenant_id()='INSTANCE')
    or tenant_id=iam_current_tenant_id()
  );

-- Force all existing active Root Sessions to re-evaluate against the current global epoch only
-- through normal Session validation. No Session is revoked by this migration.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','phase7-root-instance-repair',true);

insert into rbac_policy_versions(scope_type,scope_id,tenant_id,policy_version,updated_at,updated_by)
values('INSTANCE','INSTANCE',null,0,now(),'phase7-root-instance-repair')
on conflict do nothing;
