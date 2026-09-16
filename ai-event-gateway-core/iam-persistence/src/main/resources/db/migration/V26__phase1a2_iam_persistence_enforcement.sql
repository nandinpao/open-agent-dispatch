-- Phase 1A-2 Enforcement: constraints, optimistic locking prerequisites and FORCE RLS.

do $$
begin
  if exists (select 1 from tenants where tenant_code is null or btrim(tenant_code)='') then
    raise exception 'Phase 1A-2 blocked: tenant_code backfill incomplete';
  end if;
  if exists (select 1 from departments where display_order is null or updated_by is null or version is null) then
    raise exception 'Phase 1A-2 blocked: department persistence fields incomplete';
  end if;
  if exists (select 1 from organization_groups where updated_by is null or version is null) then
    raise exception 'Phase 1A-2 blocked: group persistence fields incomplete';
  end if;
  if exists (select 1 from org_department_closure where depth < 0 or depth > 10) then
    raise exception 'DEPARTMENT_MAX_DEPTH_EXCEEDED: closure depth outside 0..10';
  end if;
  if exists (select 1 from org_department_closure where ancestor_department_id=descendant_department_id and depth<>0) then
    raise exception 'DEPARTMENT_HIERARCHY_INTEGRITY_VIOLATION: self closure depth must be zero';
  end if;
  if exists (select 1 from tenants where char_length(display_name)>200 or char_length(coalesce(legal_name,''))>300) then
    raise exception 'Phase 1A-2 blocked: Tenant name exceeds the IAM domain limit';
  end if;
  if exists (select 1 from departments where char_length(department_code)>64 or char_length(department_name)>200) then
    raise exception 'Phase 1A-2 blocked: Department code/name exceeds the IAM domain limit';
  end if;
  if exists (select 1 from organization_groups where char_length(group_code)>64 or char_length(group_name)>200 or char_length(coalesce(description,''))>1000) then
    raise exception 'Phase 1A-2 blocked: Group code/name/description exceeds the IAM domain limit';
  end if;
end $$;

alter table tenants alter column tenant_code set not null;
alter table tenants alter column default_timezone set not null;
alter table tenants alter column default_locale set not null;
alter table tenants alter column data_region set not null;
alter table tenants alter column updated_by set not null;
alter table tenants alter column version set not null;
create unique index if not exists uq_tenants_tenant_code on tenants(lower(tenant_code));

alter table departments alter column display_order set not null;
alter table departments alter column updated_by set not null;
alter table departments alter column version set not null;
alter table organization_groups alter column updated_by set not null;
alter table organization_groups alter column version set not null;

alter table iam_root_identities drop constraint if exists ck_iam_root_identity_type;
alter table iam_root_identities add constraint ck_iam_root_identity_type check (identity_type='INSTANCE_ROOT');
alter table iam_root_identities drop constraint if exists ck_iam_root_singleton;
alter table iam_root_identities add constraint ck_iam_root_singleton check (root_identity_id='root');
alter table iam_root_identities drop constraint if exists ck_iam_root_version;
alter table iam_root_identities add constraint ck_iam_root_version check (version > 0);
alter table iam_users drop constraint if exists ck_iam_users_identity_type;
alter table iam_users add constraint ck_iam_users_identity_type check (identity_type='HUMAN_USER');
alter table iam_users drop constraint if exists ck_iam_users_version;
alter table iam_users add constraint ck_iam_users_version check (version > 0);

alter table tenants drop constraint if exists ck_tenants_phase1_status;
alter table tenants add constraint ck_tenants_phase1_status check (status in ('PROVISIONING','ACTIVE','SUSPENDED','DISABLED','DECOMMISSIONING')) not valid;
alter table tenants validate constraint ck_tenants_phase1_status;
alter table departments drop constraint if exists ck_departments_phase1_status;
alter table departments add constraint ck_departments_phase1_status check (status in ('ACTIVE','DISABLED')) not valid;
alter table departments validate constraint ck_departments_phase1_status;
alter table organization_groups drop constraint if exists ck_groups_phase1_type;
alter table organization_groups add constraint ck_groups_phase1_type check (group_type in ('SECURITY','OPERATIONS','DISPATCH','AUDIT','ON_CALL','CROSS_FUNCTIONAL','GENERAL')) not valid;
alter table organization_groups validate constraint ck_groups_phase1_type;
alter table organization_groups drop constraint if exists ck_groups_phase1_status;
alter table organization_groups add constraint ck_groups_phase1_status check (status in ('ACTIVE','DISABLED')) not valid;
alter table organization_groups validate constraint ck_groups_phase1_status;

alter table tenants drop constraint if exists ck_tenants_phase1_domain_length;
alter table tenants add constraint ck_tenants_phase1_domain_length check (
  char_length(tenant_code) between 1 and 64 and char_length(display_name) between 1 and 200
  and char_length(coalesce(legal_name,'')) <= 300 and char_length(data_region) between 1 and 64
) not valid;
alter table tenants validate constraint ck_tenants_phase1_domain_length;
alter table tenants drop constraint if exists ck_tenants_phase1_version;
alter table tenants add constraint ck_tenants_phase1_version check (version > 0) not valid;
alter table tenants validate constraint ck_tenants_phase1_version;

alter table departments drop constraint if exists ck_departments_phase1_domain_length;
alter table departments add constraint ck_departments_phase1_domain_length check (
  char_length(department_code) between 1 and 64 and char_length(department_name) between 1 and 200
) not valid;
alter table departments validate constraint ck_departments_phase1_domain_length;
alter table departments drop constraint if exists ck_departments_phase1_version;
alter table departments add constraint ck_departments_phase1_version check (version > 0) not valid;
alter table departments validate constraint ck_departments_phase1_version;

alter table organization_groups drop constraint if exists ck_groups_phase1_domain_length;
alter table organization_groups add constraint ck_groups_phase1_domain_length check (
  char_length(group_code) between 1 and 64 and char_length(group_name) between 1 and 200
  and char_length(coalesce(description,'')) <= 1000
) not valid;
alter table organization_groups validate constraint ck_groups_phase1_domain_length;
alter table organization_groups drop constraint if exists ck_groups_phase1_version;
alter table organization_groups add constraint ck_groups_phase1_version check (version > 0) not valid;
alter table organization_groups validate constraint ck_groups_phase1_version;

alter table iam_root_identities drop constraint if exists ck_iam_root_status;
alter table iam_root_identities add constraint ck_iam_root_status check (status in ('BOOTSTRAP_PENDING','ACTIVE','LOCKED_AFTER_RECOVERY','DISABLED'));
alter table iam_users drop constraint if exists ck_iam_users_status;
alter table iam_users add constraint ck_iam_users_status check (status in ('PENDING_ACTIVATION','ACTIVE','LOCKED','SUSPENDED','DISABLED','PASSWORD_RESET_REQUIRED','MFA_ENROLLMENT_REQUIRED','DELETED'));
alter table iam_users drop constraint if exists ck_iam_users_creation_mode;
alter table iam_users add constraint ck_iam_users_creation_mode check (creation_mode in ('ADMIN_CREATED','INVITATION','LEGACY_IMPORT'));

alter table org_tenant_memberships drop constraint if exists ck_org_tenant_membership_status;
alter table org_tenant_memberships add constraint ck_org_tenant_membership_status check (status in ('INVITED','ACTIVE','SUSPENDED','EXPIRED','REMOVED'));
alter table org_tenant_memberships drop constraint if exists ck_org_tenant_membership_version;
alter table org_tenant_memberships add constraint ck_org_tenant_membership_version check (version > 0);
alter table org_department_memberships drop constraint if exists ck_org_department_membership_type;
alter table org_department_memberships add constraint ck_org_department_membership_type check (membership_type in ('MEMBER','MANAGER','DELEGATE'));
alter table org_department_memberships drop constraint if exists ck_org_department_membership_status;
alter table org_department_memberships add constraint ck_org_department_membership_status check (status in ('INVITED','ACTIVE','SUSPENDED','EXPIRED','REMOVED'));
alter table org_department_memberships drop constraint if exists ck_org_department_membership_version;
alter table org_department_memberships add constraint ck_org_department_membership_version check (version > 0);
alter table org_group_memberships drop constraint if exists ck_org_group_membership_role;
alter table org_group_memberships add constraint ck_org_group_membership_role check (membership_role in ('MEMBER','LEAD'));
alter table org_group_memberships drop constraint if exists ck_org_group_membership_status;
alter table org_group_memberships add constraint ck_org_group_membership_status check (status in ('INVITED','ACTIVE','SUSPENDED','EXPIRED','REMOVED'));
alter table org_group_memberships drop constraint if exists ck_org_group_membership_version;
alter table org_group_memberships add constraint ck_org_group_membership_version check (version > 0);
alter table org_department_revisions drop constraint if exists ck_org_department_revision_change_type;
alter table org_department_revisions add constraint ck_org_department_revision_change_type check (change_type in ('CREATED','RENAMED','CODE_CHANGED','MOVED','MERGED','SPLIT','DISABLED','REACTIVATED'));
alter table iam_tenant_security_epochs drop constraint if exists ck_iam_tenant_security_epoch_nonnegative;
alter table iam_tenant_security_epochs add constraint ck_iam_tenant_security_epoch_nonnegative check (security_epoch >= 0);

alter table departments drop constraint if exists fk_departments_parent_phase1;
alter table departments add constraint fk_departments_parent_phase1 foreign key (tenant_id,parent_department_id)
  references departments(tenant_id,department_id) not valid;
alter table departments validate constraint fk_departments_parent_phase1;
alter table departments drop constraint if exists fk_departments_manager_phase1;
alter table departments add constraint fk_departments_manager_phase1 foreign key (tenant_id,manager_user_id)
  references org_tenant_memberships(tenant_id,user_id) not valid;
alter table departments validate constraint fk_departments_manager_phase1;
alter table organization_groups drop constraint if exists fk_groups_parent_phase1;
alter table organization_groups add constraint fk_groups_parent_phase1 foreign key (tenant_id,parent_group_id)
  references organization_groups(tenant_id,group_id) not valid;
alter table organization_groups validate constraint fk_groups_parent_phase1;
alter table organization_groups drop constraint if exists fk_groups_owner_department_phase1;
alter table organization_groups add constraint fk_groups_owner_department_phase1 foreign key (tenant_id,owner_department_id)
  references departments(tenant_id,department_id) not valid;
alter table organization_groups validate constraint fk_groups_owner_department_phase1;

alter table org_department_closure drop constraint if exists ck_org_department_closure_depth;
alter table org_department_closure add constraint ck_org_department_closure_depth check (depth between 0 and 10);
alter table org_tenant_memberships drop constraint if exists ck_org_tenant_membership_expiry;
alter table org_tenant_memberships add constraint ck_org_tenant_membership_expiry check (expires_at is null or expires_at > joined_at);
alter table org_department_memberships drop constraint if exists ck_org_department_membership_expiry;
alter table org_department_memberships add constraint ck_org_department_membership_expiry check (expires_at is null or expires_at > effective_at);
alter table org_group_memberships drop constraint if exists ck_org_group_membership_expiry;
alter table org_group_memberships add constraint ck_org_group_membership_expiry check (expires_at is null or expires_at > effective_at);
alter table org_department_revisions drop constraint if exists ck_org_department_revision_validity;
alter table org_department_revisions add constraint ck_org_department_revision_validity check (valid_to is null or valid_to > valid_from);

create or replace function iam_current_tenant_id()
returns varchar language plpgsql stable security invoker as $$
declare value text;
begin
  value := nullif(btrim(current_setting('app.current_tenant_id', true)), '');
  if value is null then
    raise exception 'TENANT_CONTEXT_REQUIRED' using errcode='42501';
  end if;
  return value;
end $$;

create or replace function iam_current_actor_id()
returns varchar language plpgsql stable security invoker as $$
declare value text;
begin
  value := nullif(btrim(current_setting('app.current_actor_id', true)), '');
  if value is null then
    raise exception 'ACTOR_CONTEXT_REQUIRED' using errcode='42501';
  end if;
  return value;
end $$;

-- Keep the Phase 0 compatibility bridge functional while new Tenant lifecycle APIs are not exposed yet.
-- The bridge sets transaction-local tenant context before RLS-protected placeholder rows are inserted.
create or replace function phase0b_seed_tenant_organization_defaults()
returns trigger language plpgsql as $$
begin
  perform set_config('app.current_tenant_id', new.tenant_id, true);
  perform set_config('app.current_actor_id', coalesce(nullif(new.updated_by, ''), 'phase0b-compatibility-bridge'), true);

  insert into departments (
    tenant_id, department_id, department_code, department_name, status,
    display_order, updated_by, version
  ) values (
    new.tenant_id, 'UNASSIGNED', 'UNASSIGNED', 'Unassigned Department', 'ACTIVE',
    0, coalesce(nullif(new.updated_by, ''), 'phase0b-compatibility-bridge'), 1
  ) on conflict (tenant_id, department_id) do nothing;

  insert into organization_groups (
    tenant_id, group_id, group_code, group_name, group_type, status, updated_by, version
  ) values (
    new.tenant_id, 'UNASSIGNED', 'UNASSIGNED', 'Unassigned Group', 'GENERAL', 'ACTIVE',
    coalesce(nullif(new.updated_by, ''), 'phase0b-compatibility-bridge'), 1
  ) on conflict (tenant_id, group_id) do nothing;

  insert into service_domains (tenant_id, service_domain_id, domain_code, domain_name, status)
  values (new.tenant_id, 'UNASSIGNED', 'UNASSIGNED', 'Unassigned Service Domain', 'ACTIVE')
  on conflict (tenant_id, service_domain_id) do nothing;

  insert into trust_zones (tenant_id, trust_zone_id, trust_zone_code, trust_zone_name, isolation_mode, status)
  values (new.tenant_id, 'DEFAULT', 'DEFAULT', 'Default Trust Zone', 'PER_TRUST_ZONE', 'ACTIVE')
  on conflict (tenant_id, trust_zone_id) do nothing;

  insert into department_group_bindings (tenant_id, department_id, group_id, binding_role, enabled)
  values (new.tenant_id, 'UNASSIGNED', 'UNASSIGNED', 'MEMBER', true)
  on conflict (tenant_id, department_id, group_id) do nothing;

  insert into department_service_domain_bindings (
    tenant_id, department_id, service_domain_id, ownership_role, is_primary, enabled
  ) values (new.tenant_id, 'UNASSIGNED', 'UNASSIGNED', 'OWNER', true, true)
  on conflict (tenant_id, department_id, service_domain_id) do nothing;

  insert into group_service_domain_bindings (
    tenant_id, group_id, service_domain_id, participation_role, enabled
  ) values (new.tenant_id, 'UNASSIGNED', 'UNASSIGNED', 'SUPPORTER', true)
  on conflict (tenant_id, group_id, service_domain_id) do nothing;

  insert into trust_zone_department_bindings (tenant_id, trust_zone_id, department_id, enabled)
  values (new.tenant_id, 'DEFAULT', 'UNASSIGNED', true)
  on conflict (tenant_id, trust_zone_id, department_id) do nothing;
  return new;
end $$;

create or replace function phase0b_register_tenant_from_resource()
returns trigger language plpgsql as $$
begin
  if new.tenant_id is null or btrim(new.tenant_id) = '' then
    raise exception 'TENANT_CONTEXT_REQUIRED: % requires tenant_id.', tg_table_name;
  end if;
  perform set_config('app.current_tenant_id', new.tenant_id, true);
  perform set_config('app.current_actor_id', 'phase0b-compatibility-bridge', true);
  insert into tenants (
    tenant_id, tenant_code, display_name, legal_name, status,
    default_timezone, default_locale, data_region, metadata_json,
    created_at, updated_at, updated_by, version
  ) values (
    new.tenant_id, new.tenant_id, new.tenant_id, null, 'ACTIVE',
    'UTC', 'en', 'GLOBAL', jsonb_build_object('registrationSource', 'PHASE_0B_COMPATIBILITY_BRIDGE'),
    now(), now(), 'phase0b-compatibility-bridge', 1
  ) on conflict (tenant_id) do nothing;
  return new;
end $$;

-- All tenant-owned IAM/organization tables are protected by FORCE RLS.
do $$
declare table_name text;
begin
  foreach table_name in array array[
    'departments','organization_groups','org_tenant_memberships','org_department_closure',
    'org_department_revisions','org_organization_snapshots','org_department_memberships',
    'org_group_memberships','iam_tenant_security_epochs'
  ] loop
    execute format('alter table %I enable row level security', table_name);
    execute format('alter table %I force row level security', table_name);
    execute format('drop policy if exists tenant_isolation on %I', table_name);
    execute format('create policy tenant_isolation on %I using (tenant_id = iam_current_tenant_id()) with check (tenant_id = iam_current_tenant_id())', table_name);
  end loop;
end $$;

-- Persisted hierarchy is immutable through direct closure writes by runtime code.
create or replace function phase1a2_reject_snapshot_mutation()
returns trigger language plpgsql as $$
begin
  raise exception 'ORGANIZATION_SNAPSHOT_IMMUTABLE' using errcode='55000';
end $$;

drop trigger if exists trg_org_snapshot_immutable on org_organization_snapshots;
create trigger trg_org_snapshot_immutable before update or delete on org_organization_snapshots
for each row execute function phase1a2_reject_snapshot_mutation();

create or replace function phase1a2_initialize_tenant_security_epoch()
returns trigger language plpgsql as $$
begin
  -- Instance-scope Tenant creation does not yet have a TenantContext. Establish
  -- transaction-local context for the RLS-protected epoch row created by this trigger.
  perform set_config('app.current_tenant_id', new.tenant_id, true);
  perform set_config('app.current_actor_id', coalesce(nullif(new.updated_by, ''), 'system'), true);
  insert into iam_tenant_security_epochs(tenant_id, security_epoch, updated_at, updated_by)
  values(new.tenant_id, 0, now(), coalesce(nullif(new.updated_by, ''), 'system'))
  on conflict(tenant_id) do nothing;
  return new;
end $$;

drop trigger if exists trg_phase1a2_tenant_security_epoch on tenants;
create trigger trg_phase1a2_tenant_security_epoch
after insert on tenants for each row execute function phase1a2_initialize_tenant_security_epoch();

create or replace function phase1a2_validate_department_hierarchy()
returns trigger language plpgsql as $$
declare parent_depth integer := -1;
declare parent_status varchar(32);
declare subtree_height integer := 0;
declare cycle_found boolean := false;
begin
  perform pg_advisory_xact_lock(hashtextextended('iam-department:' || new.tenant_id, 0));
  if new.parent_department_id is null then
    parent_depth := -1;
  else
    if new.parent_department_id = new.department_id then
      raise exception 'DEPARTMENT_CYCLE_DETECTED' using errcode='23514';
    end if;
    select p.status into parent_status
      from departments p
     where p.tenant_id=new.tenant_id and p.department_id=new.parent_department_id
     for key share;
    if not found then
      raise exception 'DEPARTMENT_PARENT_NOT_FOUND' using errcode='23503';
    end if;
    if parent_status <> 'ACTIVE' then
      raise exception 'DEPARTMENT_PARENT_DISABLED' using errcode='23514';
    end if;
    select coalesce(max(depth),0) into parent_depth from org_department_closure
      where tenant_id=new.tenant_id and descendant_department_id=new.parent_department_id;
    select exists(select 1 from org_department_closure where tenant_id=new.tenant_id
      and ancestor_department_id=new.department_id and descendant_department_id=new.parent_department_id)
      into cycle_found;
    if cycle_found then raise exception 'DEPARTMENT_CYCLE_DETECTED' using errcode='23514'; end if;
  end if;
  if tg_op='UPDATE' then
    select coalesce(max(depth),0) into subtree_height from org_department_closure
      where tenant_id=new.tenant_id and ancestor_department_id=new.department_id;
  end if;
  if parent_depth + 1 + subtree_height > 10 then
    raise exception 'DEPARTMENT_MAX_DEPTH_EXCEEDED' using errcode='23514';
  end if;
  return new;
end $$;

create or replace function phase1a2_rebuild_department_closure()
returns trigger language plpgsql as $$
begin
  delete from org_department_closure where tenant_id=new.tenant_id;
  insert into org_department_closure(tenant_id,ancestor_department_id,descendant_department_id,depth)
    select tenant_id,department_id,department_id,0 from departments where tenant_id=new.tenant_id;
  with recursive h as (
    select d.tenant_id,d.parent_department_id ancestor_id,d.department_id descendant_id,1 depth
      from departments d where d.tenant_id=new.tenant_id and d.parent_department_id is not null
    union all
    select h.tenant_id,p.parent_department_id,h.descendant_id,h.depth+1
      from h join departments p on p.tenant_id=h.tenant_id and p.department_id=h.ancestor_id
      where p.parent_department_id is not null and h.depth<10
  )
  insert into org_department_closure(tenant_id,ancestor_department_id,descendant_department_id,depth)
    select tenant_id,ancestor_id,descendant_id,depth from h where ancestor_id is not null;
  return new;
end $$;

create or replace function phase1a2_validate_group_hierarchy()
returns trigger language plpgsql as $$
declare current_id varchar(128);
declare depth integer := 0;
begin
  perform pg_advisory_xact_lock(hashtextextended('iam-group:' || new.tenant_id, 0));
  if new.parent_group_id is null then return new; end if;
  if new.parent_group_id = new.group_id then
    raise exception 'IDENTITY_GROUP_CYCLE_DETECTED' using errcode='23514';
  end if;
  current_id := new.parent_group_id;
  loop
    depth := depth + 1;
    if depth > 10 then raise exception 'GROUP_MAX_DEPTH_EXCEEDED' using errcode='23514'; end if;
    if current_id = new.group_id then
      raise exception 'IDENTITY_GROUP_CYCLE_DETECTED' using errcode='23514';
    end if;
    select parent_group_id into current_id from organization_groups
      where tenant_id=new.tenant_id and group_id=current_id;
    if not found then raise exception 'GROUP_PARENT_NOT_FOUND' using errcode='23503'; end if;
    exit when current_id is null;
  end loop;
  return new;
end $$;

drop trigger if exists trg_phase1a2_group_hierarchy_validate on organization_groups;
create trigger trg_phase1a2_group_hierarchy_validate
before insert or update of parent_group_id on organization_groups
for each row execute function phase1a2_validate_group_hierarchy();

drop trigger if exists trg_phase1a2_department_hierarchy_validate on departments;
create trigger trg_phase1a2_department_hierarchy_validate
before insert or update of parent_department_id on departments
for each row execute function phase1a2_validate_department_hierarchy();

drop trigger if exists trg_phase1a2_department_closure_rebuild on departments;
create trigger trg_phase1a2_department_closure_rebuild
after insert or update of parent_department_id on departments
for each row execute function phase1a2_rebuild_department_closure();
