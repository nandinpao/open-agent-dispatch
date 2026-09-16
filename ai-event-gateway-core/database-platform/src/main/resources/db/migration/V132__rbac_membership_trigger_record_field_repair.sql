-- OpenDispatch RBAC convergence runtime repair: make the shared organization-membership
-- authorization trigger safe for both Department and Group membership row types.
--
-- V130 attached one trigger function to org_department_memberships and org_group_memberships.
-- Its DECLARE initializer referenced NEW.group_id and NEW.department_id in one CASE expression.
-- PostgreSQL trigger RECORD field resolution can fail on the field that does not exist for the
-- current relation even when that CASE branch is not selected.  Use a JSONB row projection for
-- table-specific fields and keep the authorization checks themselves unchanged.

select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','rbac-membership-trigger-record-field-repair',true);

create or replace function r3_validate_membership_effective_access() returns trigger language plpgsql as $$
declare
  actor varchar(128):=nullif(current_setting('app.current_actor_id',true),'');
  row_json jsonb:=to_jsonb(new);
  membership_type varchar(32);
  resource_id varchar(128);
begin
  case tg_table_name
    when 'org_group_memberships' then
      membership_type:='GROUP';
      resource_id:=nullif(row_json->>'group_id','');
    when 'org_department_memberships' then
      membership_type:='DEPARTMENT';
      resource_id:=nullif(row_json->>'department_id','');
    else
      raise exception using errcode='55000',
        message='RBAC_MEMBERSHIP_TRIGGER_TABLE_UNSUPPORTED:'||coalesce(tg_table_name,'<null>');
  end case;

  if resource_id is null then
    raise exception using errcode='23502',
      message='RBAC_MEMBERSHIP_TRIGGER_RESOURCE_REQUIRED:'||tg_table_name;
  end if;

  if new.status<>'ACTIVE' or new.effective_at>now()
     or (new.expires_at is not null and new.expires_at<=now()) then
    return new;
  end if;

  if actor is not null and actor=new.user_id and exists(
    select 1 from rbac_principal_role_bindings b
    join r3_principals_for_membership(new.tenant_id,membership_type,resource_id) p
      on p.principal_type=b.principal_type and p.principal_id=b.principal_id
    where b.tenant_id=new.tenant_id and b.status='ACTIVE' and b.effective_at<=now()
      and (b.expires_at is null or b.expires_at>now())) then
    raise exception using errcode='23514',message='RBAC_SELF_BINDING_FORBIDDEN';
  end if;

  perform r3_assert_user_effective_sod(new.tenant_id,new.user_id,now());
  return new;
end $$;

-- Rebind explicitly so upgraded databases and clean installs converge on the repaired function.
drop trigger if exists trg_r7_group_membership_effective_access on org_group_memberships;
create trigger trg_r7_group_membership_effective_access
after insert or update of user_id,group_id,status,effective_at,expires_at on org_group_memberships
for each row execute function r3_validate_membership_effective_access();

drop trigger if exists trg_r3_department_membership_effective_access on org_department_memberships;
create trigger trg_r3_department_membership_effective_access
after insert or update of user_id,department_id,status,effective_at,expires_at on org_department_memberships
for each row execute function r3_validate_membership_effective_access();
