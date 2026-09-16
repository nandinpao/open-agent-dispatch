-- Phase 12.2 — Unified Principal & Ownership Governance
-- Canonical accountable ownership for Agents, principal applicability for Responsibilities,
-- AGENT Role Bindings, lifecycle impact/transfer primitives, and orphan prevention.

select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','phase12-2-ownership-governance',true);

-- ---------------------------------------------------------------------------
-- Responsibility applicability: a Role may explicitly state which principal classes can hold it.
-- Existing behavior remains compatible; AGENT is opt-in rather than silently inheriting Human roles.
-- ---------------------------------------------------------------------------
alter table rbac_roles
  add column if not exists allowed_principal_types varchar(40)[] not null
  default array['USER','DEPARTMENT','GROUP','SERVICE_ACCOUNT']::varchar[];

alter table rbac_roles drop constraint if exists ck_rbac_role_allowed_principal_types;
alter table rbac_roles add constraint ck_rbac_role_allowed_principal_types check(
  cardinality(allowed_principal_types)>0
  and allowed_principal_types <@ array['USER','DEPARTMENT','GROUP','SERVICE_ACCOUNT','AGENT']::varchar[]
) not valid;
alter table rbac_roles validate constraint ck_rbac_role_allowed_principal_types;

update rbac_roles
   set allowed_principal_types=array['USER']::varchar[],version=version+1,updated_at=now(),updated_by='phase12-2-ownership-governance'
 where system_managed=true
   and role_code<>'AGENT_RUNTIME'
   and allowed_principal_types is distinct from array['USER']::varchar[];

-- A deliberately narrow built-in Agent responsibility. Additional Agent Responsibilities remain
-- explicit policy decisions; existing Human/Machine roles are not auto-exposed to Agents.
insert into rbac_roles(role_id,tenant_id,role_code,role_name,description,role_type,status,system_managed,
                       created_at,updated_at,created_by,updated_by,version,risk_level,review_required,next_review_at,allowed_principal_types)
values('role-agent-runtime',null,'AGENT_RUNTIME','Agent Runtime',
       'Minimal runtime identity Responsibility for governed Agent principals. Business capabilities remain separately governed.',
       'TENANT_ROLE','ACTIVE',true,now(),now(),'phase12-2-ownership-governance','phase12-2-ownership-governance',1,
       'MEDIUM',true,now()+interval '90 days',array['AGENT']::varchar[])
on conflict(role_id) do update set
  allowed_principal_types=excluded.allowed_principal_types,
  description=excluded.description,
  review_required=true,
  next_review_at=coalesce(rbac_roles.next_review_at,excluded.next_review_at),
  updated_at=now(),updated_by='phase12-2-ownership-governance',version=rbac_roles.version+1;

insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version)
select 'phase12-2-agent-runtime-'||substr(md5(p.permission_code),1,30),null,'role-agent-runtime',p.permission_code,
       now(),'phase12-2-ownership-governance',1
  from permission_definitions p
 where p.permission_code in('api.agent.metadata','api.agent.runtime.descriptor','api.agent.runtime.capability.profile','api.agent.runtime.capability.items','api.agent.runtime.load')
   and p.active=true and p.lifecycle='ACTIVE'
on conflict(role_id,permission_point) do nothing;

-- AGENT is now a first-class canonical RBAC binding principal.
alter table rbac_principal_role_bindings drop constraint if exists ck_rbac_binding_principal;
alter table rbac_principal_role_bindings
  add constraint ck_rbac_binding_principal
  check(principal_type in('USER','DEPARTMENT','GROUP','SERVICE_ACCOUNT','AGENT')) not valid;
alter table rbac_principal_role_bindings validate constraint ck_rbac_binding_principal;

create or replace function r3_validate_role_binding_targets() returns trigger language plpgsql as $$
begin
  if new.status<>'ACTIVE' then return new; end if;
  if new.scope_type='INSTANCE' then return new; end if;
  if new.tenant_id is null then raise exception using errcode='23514',message='ROLE_BINDING_TENANT_REQUIRED'; end if;

  if new.principal_type='USER' and not exists(
    select 1 from iam_users u join org_tenant_memberships tm on tm.user_id=u.user_id and tm.tenant_id=new.tenant_id
     where u.user_id=new.principal_id and u.status<>'DELETED' and tm.status<>'REMOVED') then
    raise exception using errcode='23514',message='ROLE_BINDING_PRINCIPAL_NOT_FOUND';
  elsif new.principal_type='DEPARTMENT' and not exists(
    select 1 from departments d where d.tenant_id=new.tenant_id and d.department_id=new.principal_id) then
    raise exception using errcode='23514',message='ROLE_BINDING_PRINCIPAL_NOT_FOUND';
  elsif new.principal_type='GROUP' and not exists(
    select 1 from organization_groups g where g.tenant_id=new.tenant_id and g.group_id=new.principal_id) then
    raise exception using errcode='23514',message='ROLE_BINDING_PRINCIPAL_NOT_FOUND';
  elsif new.principal_type='SERVICE_ACCOUNT' and not exists(
    select 1 from token_service_accounts sa where sa.tenant_id=new.tenant_id and sa.service_account_id=new.principal_id) then
    raise exception using errcode='23514',message='ROLE_BINDING_PRINCIPAL_NOT_FOUND';
  elsif new.principal_type='AGENT' and not exists(
    select 1 from agent_profiles a where a.tenant_id=new.tenant_id and a.agent_id=new.principal_id) then
    raise exception using errcode='23514',message='ROLE_BINDING_PRINCIPAL_NOT_FOUND';
  end if;

  if new.scope_type='TENANT' and not exists(
    select 1 from tenants t where t.tenant_id=new.tenant_id and new.scope_id=new.tenant_id) then
    raise exception using errcode='23514',message='ROLE_BINDING_SCOPE_TARGET_NOT_FOUND';
  elsif new.scope_type in('DEPARTMENT','DEPARTMENT_SUBTREE') and not exists(
    select 1 from departments d where d.tenant_id=new.tenant_id and d.department_id=new.scope_id) then
    raise exception using errcode='23514',message='ROLE_BINDING_SCOPE_TARGET_NOT_FOUND';
  elsif new.scope_type='GROUP' and not exists(
    select 1 from organization_groups g where g.tenant_id=new.tenant_id and g.group_id=new.scope_id) then
    raise exception using errcode='23514',message='ROLE_BINDING_SCOPE_TARGET_NOT_FOUND';
  end if;
  return new;
end $$;

-- Preserve the V117 non-locking/RLS-safe validator while adding principal applicability.
create or replace function phase1a4_validate_principal_binding()
returns trigger language plpgsql as $$
declare
  role_tenant varchar(64); role_kind varchar(40); role_status varchar(32); role_code varchar(128);
  role_risk varchar(16); role_principals varchar(40)[];
begin
  select tenant_id,role_type,status,rbac_roles.role_code,risk_level,allowed_principal_types
    into role_tenant,role_kind,role_status,role_code,role_risk,role_principals
    from rbac_roles
   where role_id=new.role_id and (tenant_id is null or tenant_id is not distinct from new.tenant_id);
  if not found then raise exception 'ROLE_NOT_FOUND' using errcode='23503'; end if;
  if role_status<>'ACTIVE' then raise exception 'ROLE_DISABLED' using errcode='23514'; end if;
  if role_tenant is not null and role_tenant is distinct from new.tenant_id then raise exception 'ROLE_TENANT_MISMATCH' using errcode='23514'; end if;
  if not(new.principal_type=any(role_principals)) then raise exception 'ROLE_BINDING_PRINCIPAL_FORBIDDEN' using errcode='23514'; end if;
  if role_kind in('SYSTEM_ROLE','CUSTOM_PLATFORM_ROLE') and (new.scope_type<>'INSTANCE' or new.scope_id<>'INSTANCE' or new.tenant_id is not null) then
    raise exception 'ROLE_BINDING_SCOPE_INVALID' using errcode='23514';
  end if;
  if role_kind not in('SYSTEM_ROLE','CUSTOM_PLATFORM_ROLE') and new.scope_type='INSTANCE' then raise exception 'ROLE_BINDING_SCOPE_INVALID' using errcode='23514'; end if;
  if new.principal_type in('SERVICE_ACCOUNT','AGENT') or role_risk='CRITICAL' then
    new.review_required=true;
    new.next_review_at=coalesce(new.next_review_at,now()+case when role_risk='CRITICAL' then interval '30 days' else interval '90 days' end);
  end if;
  return new;
end $$;

-- ---------------------------------------------------------------------------
-- Agent accountable ownership and Responsibility authority.
-- ---------------------------------------------------------------------------
alter table agent_profiles
  add column if not exists business_owner_user_id varchar(128),
  add column if not exists technical_steward_user_id varchar(128),
  add column if not exists responsibility_role_id varchar(128),
  add column if not exists responsibility_binding_id varchar(128),
  add column if not exists ownership_review_status varchar(32) not null default 'REVIEW_REQUIRED',
  add column if not exists ownership_review_reason varchar(500),
  add column if not exists next_ownership_review_at timestamptz;

alter table agent_profiles drop constraint if exists ck_agent_ownership_review_status;
alter table agent_profiles add constraint ck_agent_ownership_review_status
  check(ownership_review_status in('CURRENT','REVIEW_REQUIRED','OVERDUE','ORPHANED')) not valid;
alter table agent_profiles validate constraint ck_agent_ownership_review_status;

create index if not exists idx_agent_profiles_business_owner on agent_profiles(tenant_id,business_owner_user_id,approval_status,enabled);
create index if not exists idx_agent_profiles_technical_steward on agent_profiles(tenant_id,technical_steward_user_id,approval_status,enabled);
create index if not exists idx_agent_profiles_responsibility on agent_profiles(tenant_id,responsibility_role_id,approval_status,enabled);
create unique index if not exists uq_agent_profiles_responsibility_binding on agent_profiles(responsibility_binding_id) where responsibility_binding_id is not null;

update agent_profiles
   set ownership_review_status='REVIEW_REQUIRED',
       ownership_review_reason=coalesce(ownership_review_reason,'Phase 12.2 accountable Human owner must be assigned'),
       next_ownership_review_at=coalesce(next_ownership_review_at,now()+interval '30 days')
 where business_owner_user_id is null;

create or replace function phase12_2_validate_agent_ownership() returns trigger language plpgsql as $$
declare owner_ok boolean; steward_ok boolean;
begin
  if new.responsibility_role_id is not null then
    new.responsibility_binding_id='agent-resp-'||substr(md5(new.tenant_id||':'||new.agent_id),1,40);
  else
    new.responsibility_binding_id=null;
  end if;

  if new.approval_status='APPROVED' and new.enabled and (new.owner_department_id is null or btrim(new.owner_department_id)='' or new.owner_department_id='UNASSIGNED') then
    raise exception 'AGENT_OWNER_DEPARTMENT_REQUIRED' using errcode='23514';
  end if;
  if new.approval_status='APPROVED' and new.enabled and new.responsibility_role_id is null then
    raise exception 'AGENT_RESPONSIBILITY_REQUIRED' using errcode='23514';
  end if;

  if new.business_owner_user_id is null or btrim(new.business_owner_user_id)='' then
    new.ownership_review_status='ORPHANED';
    new.ownership_review_reason='Business owner is required';
    if new.approval_status='APPROVED' and new.enabled then raise exception 'AGENT_BUSINESS_OWNER_REQUIRED' using errcode='23514'; end if;
    return new;
  end if;

  select exists(
    select 1 from iam_users u
    join org_tenant_memberships tm on tm.user_id=u.user_id and tm.tenant_id=new.tenant_id
     and tm.status='ACTIVE' and coalesce(tm.activated_at,tm.joined_at)<=now() and (tm.expires_at is null or tm.expires_at>now())
    where u.user_id=new.business_owner_user_id and u.status='ACTIVE'
      and (new.owner_department_id='UNASSIGNED' or exists(
        select 1 from org_department_memberships dm where dm.tenant_id=new.tenant_id and dm.user_id=u.user_id
          and dm.department_id=new.owner_department_id and dm.status='ACTIVE' and dm.effective_at<=now()
          and (dm.expires_at is null or dm.expires_at>now())))
  ) into owner_ok;
  if not owner_ok then
    new.ownership_review_status='ORPHANED'; new.ownership_review_reason='Business owner is not active in the Agent ownership scope';
    if new.approval_status='APPROVED' and new.enabled then raise exception 'AGENT_BUSINESS_OWNER_INACTIVE' using errcode='23514'; end if;
    return new;
  end if;

  if new.technical_steward_user_id is not null then
    select exists(
      select 1 from iam_users u join org_tenant_memberships tm on tm.user_id=u.user_id and tm.tenant_id=new.tenant_id
       and tm.status='ACTIVE' and coalesce(tm.activated_at,tm.joined_at)<=now() and (tm.expires_at is null or tm.expires_at>now())
       where u.user_id=new.technical_steward_user_id and u.status='ACTIVE') into steward_ok;
    if not steward_ok then raise exception 'AGENT_TECHNICAL_STEWARD_INACTIVE' using errcode='23514'; end if;
  end if;

  if new.ownership_review_status in('ORPHANED','REVIEW_REQUIRED','OVERDUE') or new.ownership_review_status is null then
    new.ownership_review_status='CURRENT';
  end if;
  new.ownership_review_reason=coalesce(nullif(new.ownership_review_reason,''),'Accountable ownership validated');
  new.next_ownership_review_at=coalesce(new.next_ownership_review_at,now()+interval '90 days');
  return new;
end $$;

drop trigger if exists trg_phase12_2_agent_ownership on agent_profiles;
create trigger trg_phase12_2_agent_ownership
before insert or update of business_owner_user_id,technical_steward_user_id,owner_department_id,owner_group_id,
  responsibility_role_id,approval_status,enabled,ownership_review_status,next_ownership_review_at
on agent_profiles for each row execute function phase12_2_validate_agent_ownership();

create or replace function phase12_2_sync_agent_responsibility() returns trigger language plpgsql as $$
declare scope_kind varchar(32); scope_target varchar(128);
begin
  if tg_op='UPDATE' and old.responsibility_binding_id is not null
     and (new.responsibility_binding_id is null or old.responsibility_binding_id<>new.responsibility_binding_id) then
    update rbac_principal_role_bindings set status='REVOKED',revoked_at=now(),revoked_by='phase12-2-agent-sync',
      revoke_reason='Agent Responsibility changed',version=version+1
     where binding_id=old.responsibility_binding_id and status='ACTIVE';
  end if;
  if new.responsibility_role_id is null then return new; end if;

  -- The built-in AGENT_RUNTIME permission catalog is Tenant-scoped. Custom Agent Responsibilities
  -- may later narrow scope when their entire permission set supports that scope.
  scope_kind='TENANT'; scope_target=new.tenant_id;
  insert into rbac_principal_role_bindings(binding_id,tenant_id,principal_type,principal_id,role_id,scope_type,scope_id,
    effective_at,expires_at,status,created_at,created_by,version,review_required,next_review_at)
  values(new.responsibility_binding_id,new.tenant_id,'AGENT',new.agent_id,new.responsibility_role_id,scope_kind,scope_target,
    now(),null,'ACTIVE',now(),'phase12-2-agent-sync',1,true,now()+interval '90 days')
  on conflict(binding_id) do update set role_id=excluded.role_id,scope_type=excluded.scope_type,scope_id=excluded.scope_id,
    status='ACTIVE',effective_at=least(rbac_principal_role_bindings.effective_at,excluded.effective_at),expires_at=null,
    revoked_at=null,revoked_by=null,revoke_reason=null,review_required=true,
    next_review_at=excluded.next_review_at,version=rbac_principal_role_bindings.version+1;
  return new;
end $$;

drop trigger if exists trg_phase12_2_agent_responsibility_sync on agent_profiles;
create trigger trg_phase12_2_agent_responsibility_sync
after insert or update of responsibility_role_id,responsibility_binding_id on agent_profiles
for each row execute function phase12_2_sync_agent_responsibility();

-- ---------------------------------------------------------------------------
-- People lifecycle impact and bulk ownership transfer primitive.
-- ---------------------------------------------------------------------------
create or replace view iam_user_machine_ownership_impact as
select u.user_id, tm.tenant_id,
       (select count(*) from token_service_accounts sa where sa.tenant_id=tm.tenant_id and sa.owner_user_id=u.user_id and sa.status in('ACTIVE','ROTATING','SUSPENDED')) as service_account_count,
       (select count(*) from agent_profiles a where a.tenant_id=tm.tenant_id and a.business_owner_user_id=u.user_id and a.approval_status<>'REVOKED') as agent_business_owner_count,
       (select count(*) from agent_profiles a where a.tenant_id=tm.tenant_id and a.technical_steward_user_id=u.user_id and a.approval_status<>'REVOKED') as agent_technical_steward_count,
       (select count(*) from agent_profiles a where a.tenant_id=tm.tenant_id and (a.business_owner_user_id=u.user_id or a.technical_steward_user_id=u.user_id)
          and a.ownership_review_status<>'CURRENT') as ownership_review_required_count
  from iam_users u join org_tenant_memberships tm on tm.user_id=u.user_id and tm.status<>'REMOVED';

create table if not exists iam_machine_ownership_transfer_events(
  transfer_id varchar(128) primary key,
  tenant_id varchar(64) not null references tenants(tenant_id),
  from_user_id varchar(128) not null,
  to_user_id varchar(128) not null,
  actor_id varchar(128) not null,
  reason varchar(500) not null,
  service_accounts_updated bigint not null default 0,
  agent_business_owners_updated bigint not null default 0,
  agent_stewards_updated bigint not null default 0,
  transferred_at timestamptz not null default now()
);
alter table iam_machine_ownership_transfer_events enable row level security;
alter table iam_machine_ownership_transfer_events force row level security;
drop policy if exists tenant_isolation on iam_machine_ownership_transfer_events;
create policy tenant_isolation on iam_machine_ownership_transfer_events
  using(tenant_id=current_setting('app.current_tenant_id',true))
  with check(tenant_id=current_setting('app.current_tenant_id',true));
create index if not exists idx_machine_ownership_transfer_actor on iam_machine_ownership_transfer_events(tenant_id,actor_id,transferred_at desc);
create index if not exists idx_machine_ownership_transfer_users on iam_machine_ownership_transfer_events(tenant_id,from_user_id,to_user_id,transferred_at desc);

create or replace function phase12_2_transfer_machine_ownership(
  p_tenant_id varchar,p_from_user_id varchar,p_to_user_id varchar,p_actor varchar,p_reason varchar)
returns table(transfer_id varchar,service_accounts_updated bigint,agent_business_owners_updated bigint,agent_stewards_updated bigint,transferred_at timestamptz)
language plpgsql as $$
declare sa_count bigint:=0; owner_count bigint:=0; steward_count bigint:=0; event_id varchar(128); event_at timestamptz:=clock_timestamp();
begin
  if p_from_user_id is null or p_to_user_id is null or p_from_user_id=p_to_user_id then
    raise exception 'MACHINE_OWNERSHIP_TRANSFER_TARGET_INVALID' using errcode='23514';
  end if;
  if coalesce(trim(p_reason),'')='' then
    raise exception 'MACHINE_OWNERSHIP_TRANSFER_REASON_REQUIRED' using errcode='23514';
  end if;
  if not exists(select 1 from iam_users u join org_tenant_memberships tm on tm.user_id=u.user_id and tm.tenant_id=p_tenant_id
    where u.user_id=p_to_user_id and u.status='ACTIVE' and tm.status='ACTIVE' and coalesce(tm.activated_at,tm.joined_at)<=now()
      and (tm.expires_at is null or tm.expires_at>now())) then
    raise exception 'MACHINE_OWNERSHIP_TARGET_INACTIVE' using errcode='23514';
  end if;
  if exists(select 1 from token_service_accounts sa where sa.tenant_id=p_tenant_id and sa.owner_user_id=p_from_user_id
    and sa.status in('ACTIVE','ROTATING','SUSPENDED') and not exists(select 1 from org_department_memberships dm where dm.tenant_id=p_tenant_id
      and dm.user_id=p_to_user_id and dm.department_id=sa.owner_department_id and dm.status='ACTIVE' and dm.effective_at<=now()
      and (dm.expires_at is null or dm.expires_at>now()))) then
    raise exception 'MACHINE_OWNERSHIP_TARGET_SCOPE_INVALID' using errcode='23514';
  end if;
  if exists(select 1 from agent_profiles a where a.tenant_id=p_tenant_id and a.business_owner_user_id=p_from_user_id
    and a.approval_status<>'REVOKED' and a.owner_department_id<>'UNASSIGNED' and not exists(select 1 from org_department_memberships dm
      where dm.tenant_id=p_tenant_id and dm.user_id=p_to_user_id and dm.department_id=a.owner_department_id and dm.status='ACTIVE'
      and dm.effective_at<=now() and (dm.expires_at is null or dm.expires_at>now()))) then
    raise exception 'MACHINE_OWNERSHIP_TARGET_SCOPE_INVALID' using errcode='23514';
  end if;

  update token_service_accounts set owner_user_id=p_to_user_id,updated_at=now(),updated_by=p_actor,version=version+1
   where tenant_id=p_tenant_id and owner_user_id=p_from_user_id and status in('ACTIVE','ROTATING','SUSPENDED');
  get diagnostics sa_count=row_count;
  update agent_profiles set business_owner_user_id=p_to_user_id,ownership_review_status='CURRENT',
    ownership_review_reason=p_reason,next_ownership_review_at=now()+interval '90 days',policy_version=policy_version+1,updated_at=now()
   where tenant_id=p_tenant_id and business_owner_user_id=p_from_user_id and approval_status<>'REVOKED';
  get diagnostics owner_count=row_count;
  update agent_profiles set technical_steward_user_id=p_to_user_id,ownership_review_status='CURRENT',
    ownership_review_reason=p_reason,next_ownership_review_at=now()+interval '90 days',policy_version=policy_version+1,updated_at=now()
   where tenant_id=p_tenant_id and technical_steward_user_id=p_from_user_id and approval_status<>'REVOKED';
  get diagnostics steward_count=row_count;

  event_id='machine-owner-'||substr(md5(p_tenant_id||'|'||p_from_user_id||'|'||p_to_user_id||'|'||p_actor||'|'||event_at::text||'|'||random()::text),1,32);
  insert into iam_machine_ownership_transfer_events(transfer_id,tenant_id,from_user_id,to_user_id,actor_id,reason,
    service_accounts_updated,agent_business_owners_updated,agent_stewards_updated,transferred_at)
  values(event_id,p_tenant_id,p_from_user_id,p_to_user_id,p_actor,p_reason,sa_count,owner_count,steward_count,event_at);
  return query select event_id,sa_count,owner_count,steward_count,event_at;
end $$;

-- User suspension/disable/delete cannot create hidden machine or Agent orphans.
create or replace function phase12_2_guard_user_machine_ownership() returns trigger language plpgsql as $$
declare tenant_value varchar(64); previous_tenant text; blocked boolean:=false;
begin
  if old.status='ACTIVE' and new.status<>'ACTIVE' then
    previous_tenant=current_setting('app.current_tenant_id',true);
    for tenant_value in
      select d.tenant_id from iam_user_tenant_directory d
       where d.user_id=new.user_id and d.membership_status<>'REMOVED'
       order by d.tenant_id
    loop
      perform set_config('app.current_tenant_id',tenant_value,true);
      select exists(
        select 1 from iam_user_machine_ownership_impact i where i.tenant_id=tenant_value and i.user_id=new.user_id
          and (i.service_account_count>0 or i.agent_business_owner_count>0 or i.agent_technical_steward_count>0)
      ) into blocked;
      exit when blocked;
    end loop;
    perform set_config('app.current_tenant_id',coalesce(nullif(previous_tenant,''),'INSTANCE'),true);
    if blocked then
      raise exception 'MACHINE_OWNERSHIP_TRANSFER_REQUIRED' using errcode='23514';
    end if;
  end if;
  return new;
exception when others then
  perform set_config('app.current_tenant_id',coalesce(nullif(previous_tenant,''),'INSTANCE'),true);
  raise;
end $$;

drop trigger if exists trg_phase12_2_user_machine_ownership on iam_users;
create trigger trg_phase12_2_user_machine_ownership
before update of status on iam_users for each row execute function phase12_2_guard_user_machine_ownership();

-- Tenant/Department membership lifecycle is also governed; keeping the Person ACTIVE must not
-- silently orphan machine ownership when organization membership is removed or suspended.
create or replace function phase12_2_guard_tenant_membership_machine_ownership() returns trigger language plpgsql as $$
begin
  if old.status='ACTIVE' and new.status<>'ACTIVE' and exists(
    select 1 from iam_user_machine_ownership_impact i
     where i.tenant_id=new.tenant_id and i.user_id=new.user_id
       and (i.service_account_count>0 or i.agent_business_owner_count>0 or i.agent_technical_steward_count>0)) then
    raise exception 'MACHINE_OWNERSHIP_TRANSFER_REQUIRED' using errcode='23514';
  end if;
  return new;
end $$;

drop trigger if exists trg_phase12_2_tenant_membership_machine_ownership on org_tenant_memberships;
create trigger trg_phase12_2_tenant_membership_machine_ownership
before update of status on org_tenant_memberships for each row execute function phase12_2_guard_tenant_membership_machine_ownership();

create or replace function phase12_2_guard_department_membership_machine_ownership() returns trigger language plpgsql as $$
begin
  if old.status='ACTIVE' and new.status<>'ACTIVE' and (
    exists(select 1 from token_service_accounts sa where sa.tenant_id=new.tenant_id and sa.owner_user_id=new.user_id
      and sa.owner_department_id=new.department_id and sa.status in('ACTIVE','ROTATING','SUSPENDED'))
    or exists(select 1 from agent_profiles a where a.tenant_id=new.tenant_id and a.business_owner_user_id=new.user_id
      and a.owner_department_id=new.department_id and a.approval_status<>'REVOKED')
  ) then
    raise exception 'MACHINE_OWNERSHIP_DEPARTMENT_MEMBERSHIP_REQUIRED' using errcode='23514';
  end if;
  return new;
end $$;

drop trigger if exists trg_phase12_2_department_membership_machine_ownership on org_department_memberships;
create trigger trg_phase12_2_department_membership_machine_ownership
before update of status on org_department_memberships for each row execute function phase12_2_guard_department_membership_machine_ownership();

-- Canonical Human Admin route mapping for atomic ownership transfer. The controller also
-- requires security.token.manage as a second permission before executing the cross-domain mutation.
insert into permission_entry_point_inventory(entry_point_id,entry_point_type,application_id,owner_module,display_name,route_pattern,http_method,authority_state,target_permission_code,legacy_authority_type,legacy_authorities,resource_type,resource_resolver_id,exemption_reason,migration_deadline,manifest_revision,source_ref,source_hash,last_verified_at,created_by,updated_by)
values('REST:POST:/api/admin/access/tenants/{tenantId}/users/{userId}/machine-ownership/transfer','REST','control-plane-app','iam-api',
  'UnifiedAccessManagementController.transferMachineOwnership','/api/admin/access/tenants/{tenantId}/users/{userId}/machine-ownership/transfer','POST',
  'TARGET_ONLY','identity.user.update',null,'[]'::jsonb,'UNIFIED_ACCESS_MANAGEMENT','R3_PATH_RESOURCE_RESOLVER',null,null,
  'phase12-2-unified-principal-ownership-2026-08-14',
  'ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/UnifiedAccessManagementController.java#transferMachineOwnership',
  '3083bbfea4bba83a5f2678b54f94c1a7388a9a0cabc4cd02367ca2ccc1972d54',now(),'phase12-2-ownership-governance','phase12-2-ownership-governance')
on conflict(entry_point_id) do update set authority_state='TARGET_ONLY',target_permission_code='identity.user.update',resource_type='UNIFIED_ACCESS_MANAGEMENT',
  resource_resolver_id='R3_PATH_RESOURCE_RESOLVER',manifest_revision=excluded.manifest_revision,source_ref=excluded.source_ref,source_hash=excluded.source_hash,
  last_verified_at=now(),updated_at=now(),updated_by='phase12-2-ownership-governance',version=permission_entry_point_inventory.version+1;

comment on column agent_profiles.owner_team is 'Legacy display metadata only. Phase 12.2 authority uses accountable Human ownership and canonical Organization IDs.';
comment on column agent_profiles.business_owner_user_id is 'Accountable Human business owner. Ownership is governance metadata and never grants authorization by itself.';
comment on column agent_profiles.technical_steward_user_id is 'Optional accountable Human technical steward. Authorization remains canonical RBAC.';
comment on column agent_profiles.responsibility_role_id is 'Canonical RBAC Responsibility held by the AGENT principal.';
comment on view iam_user_machine_ownership_impact is 'Phase 12.2 read projection used before People lifecycle changes to prevent machine/Agent ownership orphans.';
