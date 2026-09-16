-- Issue Tracking current-authority repair.
--
-- Phase 0E originally required an enabled Project Mapping to point at a principal
-- whose aggregate status was exactly ACTIVE. That rule predates the thin Redmine
-- connector model. In the current model:
--   * one SERVICE_ACCOUNT is the technical execution identity;
--   * provider permission probes are diagnostic evidence only;
--   * Redmine remains the final authority for READ/CREATE/COMMENT/UPDATE rights;
--   * IntegrationPrincipalScope mutation is retired.
--
-- Therefore an authenticated service account may legitimately be DEGRADED (for
-- example CREATE is granted while UPDATE is denied) and must still be eligible to
-- back an enabled mapping. Database enforcement must match the runtime eligibility
-- predicate instead of reviving the retired scoped-principal model.

create or replace function phase0e_validate_project_mapping_principals()
returns trigger
language plpgsql
as $$
declare
  p varchar(128);
  resolved_principal_id varchar(128);
begin
  -- Compatibility operation-principal columns may remain populated on historical
  -- rows. Every referenced principal must still belong to the same Tenant and
  -- Connection; that referential invariant remains authoritative.
  foreach p in array array[
    new.read_principal_id,
    new.create_principal_id,
    new.comment_principal_id,
    new.update_principal_id,
    new.relation_principal_id,
    new.webhook_principal_id
  ]
  loop
    if p is not null and btrim(p) <> '' then
      if not exists(
        select 1
          from integration_principals ip
         where ip.tenant_id = new.tenant_id
           and ip.principal_id = p
           and ip.connection_id = new.connection_id
      ) then
        raise exception 'Project Mapping principal % must belong to the same Tenant and Connection', p;
      end if;

      if resolved_principal_id is null then
        resolved_principal_id := p;
      elsif new.enabled and resolved_principal_id is distinct from p then
        raise exception 'Enabled Project Mapping must use one Technical Service Account';
      end if;
    end if;
  end loop;

  if new.enabled then
    if resolved_principal_id is null then
      raise exception 'Enabled Project Mapping requires a Technical Service Account';
    end if;

    if not exists(
      select 1
        from integration_principals ip
       where ip.tenant_id = new.tenant_id
         and ip.principal_id = resolved_principal_id
         and ip.connection_id = new.connection_id
         and ip.principal_type = 'SERVICE_ACCOUNT'
         and ip.status not in ('REVOKED', 'EXPIRED', 'DISABLED')
    ) then
      raise exception 'Enabled Project Mapping requires an available Technical Service Account';
    end if;
  end if;

  return new;
end $$;

comment on function phase0e_validate_project_mapping_principals() is
'Current Issue Tracking invariant: operation-principal columns are compatibility metadata; enabled mappings use one available SERVICE_ACCOUNT on the same Tenant/Connection. Provider permissions remain Redmine authority; IntegrationPrincipalScope is not an activation prerequisite.';
