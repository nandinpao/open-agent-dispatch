-- V164 — Tenant resource authority boundary repair
-- Resource writes must never create Tenant authority at runtime. Repair legacy orphan references once,
-- then make the Phase 0B compatibility trigger a fail-closed canonical Tenant resolver only.

select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','v164-tenant-resource-boundary-repair',true);

-- Existing pre-IAM/legacy rows may reference tenant_id values that were accepted before canonical
-- Tenant foreign keys were validated. Reconcile those historical references once during migration.
create temporary table v164_legacy_resource_tenants on commit drop as
select min(btrim(tenant_id))::varchar(64) as tenant_id
from (
  select tenant_id from source_systems
  union all select tenant_id from agent_enrollment_requests
  union all select tenant_id from agent_profiles
  union all select tenant_id from agents
  union all select tenant_id from agent_pools
  union all select tenant_id from runtime_resources
  union all select tenant_id from dispatch_flows
  union all select tenant_id from dispatch_policies
  union all select tenant_id from tasks
) resource_tenants
where tenant_id is not null and btrim(tenant_id)<>''
group by lower(btrim(tenant_id));

insert into tenants (
  tenant_id, tenant_code, display_name, legal_name, status,
  default_timezone, default_locale, data_region, metadata_json,
  created_at, updated_at, updated_by, version
)
select
  legacy.tenant_id, legacy.tenant_id, legacy.tenant_id, null, 'ACTIVE',
  'UTC', 'en', 'GLOBAL',
  jsonb_build_object(
    'registrationSource','V164_LEGACY_RESOURCE_REPAIR',
    'migrationOnly',true,
    'runtimeImplicitCreationDisabled',true
  ),
  now(), now(), 'v164-tenant-resource-boundary-repair', 1
from v164_legacy_resource_tenants legacy
where not exists (
  select 1 from tenants t
   where lower(btrim(t.tenant_id))=lower(legacy.tenant_id)
      or lower(btrim(t.tenant_code))=lower(legacy.tenant_id)
)
on conflict do nothing;

-- Runtime resources may resolve a case-insensitive Tenant ID/code alias, but they may no longer
-- create Tenant records. Tenant lifecycle authority belongs to IAM Tenant administration only.
create or replace function phase0b_register_tenant_from_resource()
returns trigger
language plpgsql
as $$
declare
  v_requested_tenant_id varchar(64);
  v_canonical_tenant_ids varchar(64)[];
  v_canonical_tenant_id varchar(64);
begin
  if new.tenant_id is null or btrim(new.tenant_id)='' then
    raise exception using
      errcode='23502',
      message='TENANT_CONTEXT_REQUIRED',
      detail=format('%s requires tenant_id.',tg_table_name),
      hint='Use a canonical IAM Tenant before creating Tenant-owned resources.';
  end if;

  v_requested_tenant_id:=btrim(new.tenant_id);

  select array_agg(distinct tenant_id order by tenant_id)
    into v_canonical_tenant_ids
    from tenants
   where lower(btrim(tenant_id))=lower(v_requested_tenant_id)
      or lower(btrim(tenant_code))=lower(v_requested_tenant_id);

  if coalesce(cardinality(v_canonical_tenant_ids),0)>1 then
    raise exception using
      errcode='23505',
      message='TENANT_IDENTITY_ALIAS_AMBIGUOUS',
      detail=format('Resource Tenant alias %L resolves to tenant IDs %s.',v_requested_tenant_id,v_canonical_tenant_ids::text),
      hint='Resolve the canonical Tenant identity collision before writing Tenant-owned resources.';
  end if;

  if coalesce(cardinality(v_canonical_tenant_ids),0)=0 then
    raise exception using
      errcode='23503',
      message='TENANT_NOT_REGISTERED',
      detail=format('%s references Tenant %L, but no canonical IAM Tenant exists.',tg_table_name,v_requested_tenant_id),
      hint='Create or restore the Tenant through IAM administration; resource writes do not create Tenants.';
  end if;

  v_canonical_tenant_id:=v_canonical_tenant_ids[1];
  new.tenant_id:=v_canonical_tenant_id;
  return new;
end $$;

comment on function phase0b_register_tenant_from_resource() is
'V164 fail-closed Tenant reference resolver. Runtime resource writes never create canonical Tenant authority.';
