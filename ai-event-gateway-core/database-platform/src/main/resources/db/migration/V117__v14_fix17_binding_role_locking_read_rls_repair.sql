-- OpenDispatch v14 Fix17: Principal Role Binding validator locking-read/RLS repair.
--
-- V116 made global Role templates visible to Tenant SELECTs, but the binding validator still used
-- SELECT ... FOR KEY SHARE. A PostgreSQL locking SELECT requires UPDATE authority in addition to
-- SELECT authority. The canonical UPDATE RLS policy intentionally prevents a Tenant request from
-- updating a global Role template, so the locking lookup filtered the otherwise-visible global
-- TENANT_ADMIN row and incorrectly raised ROLE_NOT_FOUND.
--
-- Referential integrity already protects the binding/Role relationship through the foreign key
-- rbac_principal_role_bindings(role_id) -> rbac_roles(role_id). The trigger therefore performs a
-- non-locking validation read while the foreign key remains the authoritative concurrent-delete
-- guard.

select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','v14-fix17-binding-role-read-repair',true);

-- Fail closed if the database no longer has the foreign key that makes the non-locking lookup safe.
do $$
begin
  if not exists(
    select 1
      from pg_constraint c
     where c.contype='f'
       and c.conrelid='rbac_principal_role_bindings'::regclass
       and c.confrelid='rbac_roles'::regclass
       and exists(
         select 1
           from unnest(c.conkey) with ordinality source(attnum,position)
           join unnest(c.confkey) with ordinality target(attnum,position)
             using(position)
           join pg_attribute source_attribute
             on source_attribute.attrelid=c.conrelid
            and source_attribute.attnum=source.attnum
           join pg_attribute target_attribute
             on target_attribute.attrelid=c.confrelid
            and target_attribute.attnum=target.attnum
          where source_attribute.attname='role_id'
            and target_attribute.attname='role_id'
       )
  ) then
    raise exception 'FIX17_BINDING_ROLE_FOREIGN_KEY_REQUIRED' using errcode='23514';
  end if;
end $$;

create or replace function phase1a4_validate_principal_binding()
returns trigger language plpgsql as $$
declare
  role_tenant varchar(64);
  role_kind varchar(40);
  role_status varchar(32);
  role_code varchar(128);
  role_risk varchar(16);
begin
  select tenant_id,role_type,status,rbac_roles.role_code,risk_level
    into role_tenant,role_kind,role_status,role_code,role_risk
    from rbac_roles
   where role_id=new.role_id
     and (tenant_id is null or tenant_id is not distinct from new.tenant_id);

  if not found then raise exception 'ROLE_NOT_FOUND' using errcode='23503'; end if;
  if role_status<>'ACTIVE' then raise exception 'ROLE_DISABLED' using errcode='23514'; end if;
  if role_tenant is not null and role_tenant is distinct from new.tenant_id then
    raise exception 'ROLE_TENANT_MISMATCH' using errcode='23514';
  end if;
  if role_kind in('SYSTEM_ROLE','CUSTOM_PLATFORM_ROLE')
     and (new.scope_type<>'INSTANCE' or new.scope_id<>'INSTANCE' or new.tenant_id is not null) then
    raise exception 'ROLE_BINDING_SCOPE_INVALID' using errcode='23514';
  end if;
  if role_kind not in('SYSTEM_ROLE','CUSTOM_PLATFORM_ROLE') and new.scope_type='INSTANCE' then
    raise exception 'ROLE_BINDING_SCOPE_INVALID' using errcode='23514';
  end if;
  if role_code in('SYSTEM_ADMIN','TENANT_ADMIN') and new.principal_type<>'USER' then
    raise exception 'ROLE_BINDING_PRINCIPAL_FORBIDDEN' using errcode='23514';
  end if;
  if new.principal_type='SERVICE_ACCOUNT' or role_risk='CRITICAL' then
    new.review_required=true;
    new.next_review_at=coalesce(
      new.next_review_at,
      now()+case when role_risk='CRITICAL' then interval '30 days' else interval '90 days' end);
  end if;
  return new;
end $$;

-- Assert the deployed trigger body uses the RLS-compatible non-locking lookup and retains the
-- explicit global-or-same-Tenant Role boundary.
do $$
declare validator_definition text;
begin
  select lower(pg_get_functiondef('phase1a4_validate_principal_binding()'::regprocedure))
    into validator_definition;

  if position('for key share' in validator_definition)>0
     or position('for update' in validator_definition)>0
     or position('tenant_id is null' in validator_definition)=0
     or position('tenant_id is not distinct from new.tenant_id' in validator_definition)=0 then
    raise exception 'FIX17_BINDING_ROLE_VALIDATOR_REPAIR_FAILED' using errcode='23514';
  end if;
end $$;
