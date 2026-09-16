-- Phase 5 Fix6: Tenant is provisioning data, not an interactive authentication choice.
--
-- Normal human sessions are bound automatically to the active Tenant Membership marked
-- default_tenant=true. A single active membership is an unambiguous fallback. This migration
-- backfills a deterministic home workspace for historical identities that have active memberships
-- but no Default Tenant, so login never needs to ask a Person to choose authorization context.

create temporary table phase5_fix6_default_tenant_candidates on commit drop as
with active as (
  select d.user_id,
         d.tenant_id,
         d.default_tenant,
         d.updated_at,
         bool_or(d.default_tenant) over(partition by d.user_id) as has_default,
         row_number() over(
           partition by d.user_id
           order by d.updated_at asc nulls last, d.tenant_id asc
         ) as candidate_rank
    from iam_user_tenant_directory d
   where d.membership_status='ACTIVE'
     and d.tenant_status='ACTIVE'
     and (d.expires_at is null or d.expires_at>now())
)
select user_id,tenant_id
  from active
 where not has_default
   and candidate_rank=1;

do $$
declare
  tenant_value varchar(64);
begin
  for tenant_value in
    select distinct tenant_id
      from phase5_fix6_default_tenant_candidates
     order by tenant_id
  loop
    perform set_config('app.current_tenant_id',tenant_value,true);
    perform set_config('app.current_actor_id','phase5-fix6',true);

    update org_tenant_memberships m
       set default_tenant=true,
           updated_at=now(),
           updated_by='phase5-fix6',
           status_reason=case
             when coalesce(m.status_reason,'')='' then 'Assigned as sign-in home workspace by Phase 5 Fix6'
             else m.status_reason
           end,
           version=m.version+1
     where m.tenant_id=tenant_value
       and m.status='ACTIVE'
       and exists (
         select 1
           from phase5_fix6_default_tenant_candidates c
          where c.user_id=m.user_id
            and c.tenant_id=m.tenant_id
       );
  end loop;

  perform set_config('app.current_tenant_id','INSTANCE',true);
  perform set_config('app.current_actor_id','phase5-fix6',true);
end $$;

comment on column org_tenant_memberships.default_tenant is
  'Sign-in home workspace for a human identity. Authentication resolves this Tenant automatically; users do not choose Tenant context during login.';

-- Admission safety net: the first active Tenant Membership for an identity becomes its home
-- workspace even when a legacy/API caller omits defaultTenant. Existing active memberships are not
-- silently rewritten here, so administrators can still perform explicit lifecycle changes.
create or replace function phase5_fix6_assign_default_tenant_on_admission()
returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
begin
  if new.status='ACTIVE' and not coalesce(new.default_tenant,false) then
    if tg_op='INSERT' or (tg_op='UPDATE' and old.status<>'ACTIVE') then
      if not exists (
        select 1
          from iam_user_tenant_directory d
         where d.user_id=new.user_id
           and d.membership_status='ACTIVE'
           and d.tenant_status='ACTIVE'
           and d.default_tenant=true
           and (d.expires_at is null or d.expires_at>now())
           and d.tenant_id<>new.tenant_id
      ) then
        new.default_tenant=true;
      end if;
    end if;
  end if;
  return new;
end $$;

revoke all on function phase5_fix6_assign_default_tenant_on_admission() from public;

drop trigger if exists trg_phase5_fix6_default_tenant_on_admission on org_tenant_memberships;
create trigger trg_phase5_fix6_default_tenant_on_admission
before insert or update of status on org_tenant_memberships
for each row execute function phase5_fix6_assign_default_tenant_on_admission();
