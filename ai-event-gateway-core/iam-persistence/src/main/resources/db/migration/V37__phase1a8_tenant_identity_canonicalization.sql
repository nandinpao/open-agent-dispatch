-- Phase 1A-8 tenant identity canonicalization repair.
--
-- Tenant identifiers are opaque, case-preserving values. Legacy Dispatch resources may
-- still submit a Tenant code (or a case variant of the canonical Tenant ID). The Phase 0B
-- compatibility bridge must resolve that alias to the existing canonical tenants.tenant_id
-- before the resource row and its foreign keys are evaluated.

-- Tenant ID and Tenant code share one case-insensitive alias namespace. Ambiguous existing
-- data must be repaired explicitly instead of being guessed during a resource mutation.
do $$
declare
  v_ambiguous_alias text;
begin
  select alias_key
    into v_ambiguous_alias
    from (
      select lower(btrim(tenant_id)) as alias_key, tenant_id from tenants
      union all
      select lower(btrim(tenant_code)) as alias_key, tenant_id from tenants
    ) aliases
   where alias_key is not null and alias_key <> ''
   group by alias_key
  having count(distinct tenant_id) > 1
   order by alias_key
   limit 1;

  if v_ambiguous_alias is not null then
    raise exception using
      errcode = '23505',
      message = 'TENANT_IDENTITY_ALIAS_AMBIGUOUS',
      detail = format('Tenant ID/code alias %L resolves to more than one tenant.', v_ambiguous_alias),
      hint = 'Repair the tenants table so each case-insensitive ID/code alias maps to exactly one tenant before retrying.';
  end if;
end $$;

create unique index if not exists uq_tenants_tenant_id_ci
  on tenants(lower(btrim(tenant_id)));

create or replace function phase1a8_validate_tenant_identity_namespace()
returns trigger
language plpgsql
as $$
declare
  v_current_tenant_id varchar(64);
begin
  if tg_op = 'UPDATE' then
    v_current_tenant_id := old.tenant_id;
  end if;
  if new.tenant_id is null or btrim(new.tenant_id) = '' then
    raise exception using errcode = '23514', message = 'TENANT_ID_REQUIRED';
  end if;
  if new.tenant_code is null or btrim(new.tenant_code) = '' then
    raise exception using errcode = '23514', message = 'TENANT_CODE_REQUIRED';
  end if;

  -- ID and code share one alias namespace. Lock both alias keys in deterministic
  -- order so concurrent cross-column inserts cannot both pass the existence check.
  perform pg_advisory_xact_lock(
    hashtextextended(least(lower(btrim(new.tenant_id)), lower(btrim(new.tenant_code))), 1)
  );
  if lower(btrim(new.tenant_id)) <> lower(btrim(new.tenant_code)) then
    perform pg_advisory_xact_lock(
      hashtextextended(greatest(lower(btrim(new.tenant_id)), lower(btrim(new.tenant_code))), 1)
    );
  end if;

  if exists (
    select 1
      from tenants existing
     where (v_current_tenant_id is null or existing.tenant_id <> v_current_tenant_id)
       and (
         lower(btrim(existing.tenant_id)) = lower(btrim(new.tenant_id))
         or lower(btrim(existing.tenant_code)) = lower(btrim(new.tenant_code))
         or lower(btrim(existing.tenant_id)) = lower(btrim(new.tenant_code))
         or lower(btrim(existing.tenant_code)) = lower(btrim(new.tenant_id))
       )
  ) then
    raise exception using
      errcode = '23505',
      message = 'TENANT_IDENTITY_ALIAS_CONFLICT',
      detail = format('tenant_id=%L and tenant_code=%L overlap an existing case-insensitive Tenant ID/code alias.', new.tenant_id, new.tenant_code),
      hint = 'Tenant ID and Tenant code must belong to one tenant identity namespace.';
  end if;

  return new;
end $$;

drop trigger if exists trg_tenants_phase1a8_identity_namespace on tenants;
create trigger trg_tenants_phase1a8_identity_namespace
before insert or update of tenant_id, tenant_code on tenants
for each row execute function phase1a8_validate_tenant_identity_namespace();

create or replace function phase0b_register_tenant_from_resource()
returns trigger
language plpgsql
as $$
declare
  v_requested_tenant_id varchar(64);
  v_canonical_tenant_ids varchar(64)[];
  v_canonical_tenant_id varchar(64);
begin
  if new.tenant_id is null or btrim(new.tenant_id) = '' then
    raise exception 'TENANT_CONTEXT_REQUIRED: % requires tenant_id.', tg_table_name;
  end if;

  v_requested_tenant_id := btrim(new.tenant_id);

  -- Serialize registration/resolution for the same case-insensitive alias. This prevents
  -- concurrent first-use inserts from racing the Tenant code and Tenant ID unique indexes.
  perform pg_advisory_xact_lock(hashtextextended(lower(v_requested_tenant_id), 0));

  select array_agg(distinct tenant_id order by tenant_id)
    into v_canonical_tenant_ids
    from tenants
   where lower(btrim(tenant_id)) = lower(v_requested_tenant_id)
      or lower(btrim(tenant_code)) = lower(v_requested_tenant_id);

  if coalesce(cardinality(v_canonical_tenant_ids), 0) > 1 then
    raise exception using
      errcode = '23505',
      message = 'TENANT_IDENTITY_ALIAS_AMBIGUOUS',
      detail = format('Resource Tenant alias %L resolves to tenant IDs %s.', v_requested_tenant_id, v_canonical_tenant_ids::text);
  end if;

  if coalesce(cardinality(v_canonical_tenant_ids), 0) = 0 then
    insert into tenants (
      tenant_id, tenant_code, display_name, legal_name, status,
      default_timezone, default_locale, data_region, metadata_json,
      created_at, updated_at, updated_by, version
    ) values (
      v_requested_tenant_id, v_requested_tenant_id, v_requested_tenant_id, null, 'ACTIVE',
      'UTC', 'en', 'GLOBAL', jsonb_build_object('registrationSource', 'PHASE_0B_COMPATIBILITY_BRIDGE'),
      now(), now(), 'phase0b-compatibility-bridge', 1
    ) on conflict do nothing;

    select array_agg(distinct tenant_id order by tenant_id)
      into v_canonical_tenant_ids
      from tenants
     where lower(btrim(tenant_id)) = lower(v_requested_tenant_id)
        or lower(btrim(tenant_code)) = lower(v_requested_tenant_id);
  end if;

  if coalesce(cardinality(v_canonical_tenant_ids), 0) <> 1 then
    raise exception using
      errcode = '23503',
      message = 'TENANT_REGISTRY_CANONICALIZATION_FAILED',
      detail = format('Resource Tenant alias %L could not be resolved to one canonical tenant.', v_requested_tenant_id);
  end if;

  v_canonical_tenant_id := v_canonical_tenant_ids[1];
  new.tenant_id := v_canonical_tenant_id;

  perform set_config('app.current_tenant_id', v_canonical_tenant_id, true);
  perform set_config('app.current_actor_id', 'phase0b-compatibility-bridge', true);
  return new;
end $$;
