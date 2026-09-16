-- Phase 0E enforcement: Tenant-aware references, lifecycle checks and secret safety.

do $$ begin
  if not exists(select 1 from pg_constraint where conname='fk_integration_connections_tenant') then
    alter table integration_connections add constraint fk_integration_connections_tenant foreign key(tenant_id) references tenants(tenant_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_integration_principals_connection') then
    alter table integration_principals add constraint fk_integration_principals_connection foreign key(tenant_id,connection_id) references integration_connections(tenant_id,connection_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_integration_principals_department') then
    alter table integration_principals add constraint fk_integration_principals_department foreign key(tenant_id,owner_department_id) references departments(tenant_id,department_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_integration_principals_group') then
    alter table integration_principals add constraint fk_integration_principals_group foreign key(tenant_id,owner_group_id) references organization_groups(tenant_id,group_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_integration_principals_trust_zone') then
    alter table integration_principals add constraint fk_integration_principals_trust_zone foreign key(tenant_id,trust_zone_id) references trust_zones(tenant_id,trust_zone_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_integration_credentials_principal') then
    alter table integration_credentials add constraint fk_integration_credentials_principal foreign key(tenant_id,principal_id) references integration_principals(tenant_id,principal_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_integration_mappings_connection') then
    alter table integration_project_mappings add constraint fk_integration_mappings_connection foreign key(tenant_id,connection_id) references integration_connections(tenant_id,connection_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_integration_mappings_department') then
    alter table integration_project_mappings add constraint fk_integration_mappings_department foreign key(tenant_id,department_id) references departments(tenant_id,department_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_integration_mappings_group') then
    alter table integration_project_mappings add constraint fk_integration_mappings_group foreign key(tenant_id,group_id) references organization_groups(tenant_id,group_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_integration_mappings_domain') then
    alter table integration_project_mappings add constraint fk_integration_mappings_domain foreign key(tenant_id,service_domain_id) references service_domains(tenant_id,service_domain_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_integration_mappings_source_system') then
    alter table integration_project_mappings add constraint fk_integration_mappings_source_system foreign key(tenant_id,source_system_id) references source_systems(tenant_id,source_system_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_integration_mappings_permission_profile') then
    alter table integration_project_mappings add constraint fk_integration_mappings_permission_profile foreign key(tenant_id,permission_profile_id) references integration_permission_profiles(tenant_id,permission_profile_id) not valid;
  end if;
end $$;

-- All operation principals must belong to the same Tenant and Connection as the Mapping.
create or replace function phase0e_validate_project_mapping_principals() returns trigger language plpgsql as $$
declare p varchar(128);
begin
  foreach p in array array[new.read_principal_id,new.create_principal_id,new.comment_principal_id,new.update_principal_id,new.relation_principal_id,new.webhook_principal_id]
  loop
    if p is not null and not exists(
      select 1 from integration_principals ip
      where ip.tenant_id=new.tenant_id and ip.principal_id=p and ip.connection_id=new.connection_id
    ) then raise exception 'Project Mapping principal % must belong to the same Tenant and Connection',p; end if;
  end loop;
  if new.enabled and not exists(
    select 1 from integration_principals ip
    where ip.tenant_id=new.tenant_id
      and ip.principal_id=coalesce(new.create_principal_id,new.read_principal_id,new.comment_principal_id)
      and ip.status='ACTIVE'
      and ip.status <> 'OVER_PRIVILEGED'
  ) then raise exception 'Enabled Project Mapping requires an ACTIVE scoped principal'; end if;
  return new;
end $$;
drop trigger if exists trg_phase0e_project_mapping_principals on integration_project_mappings;
create trigger trg_phase0e_project_mapping_principals before insert or update on integration_project_mappings for each row execute function phase0e_validate_project_mapping_principals();

-- Tenant and aggregate identity are immutable after creation.
create or replace function phase0e_reject_identity_reassignment() returns trigger language plpgsql as $$
begin
  if old.tenant_id is distinct from new.tenant_id then raise exception 'Integration Tenant is immutable'; end if;
  return new;
end $$;

do $$ declare t text; begin
  foreach t in array array['integration_connections','integration_principals','integration_credentials','integration_project_mappings','integration_permission_profiles'] loop
    execute format('drop trigger if exists trg_phase0e_tenant_immutable on %I',t);
    execute format('create trigger trg_phase0e_tenant_immutable before update on %I for each row execute function phase0e_reject_identity_reassignment()',t);
  end loop;
end $$;

-- Database stores only Secret references and non-sensitive metadata.
alter table integration_credentials add constraint ck_integration_credentials_secret_ref check(
  length(trim(secret_ref)) >= 8
  and lower(secret_ref) !~ '(password|token|secret)=[^& ]+'
  and secret_ref !~ '^[A-Za-z0-9_\-]{20,}$'
);
alter table integration_credentials add constraint ck_integration_credentials_last4 check(secret_last4 is null or length(secret_last4)=4);
alter table integration_credentials add constraint ck_integration_credentials_status check(status in('PENDING_VALIDATION','ACTIVE','GRACE_PERIOD','EXPIRED','REVOKED','DISABLED'));
alter table integration_connections add constraint ck_integration_connections_provider check(provider_type in('REDMINE','JIRA','GITLAB_ISSUES'));
alter table integration_connections add constraint ck_integration_connections_status check(status in('DRAFT','VALIDATING','ACTIVE','DEGRADED','DISABLED','UNREACHABLE'));
alter table integration_principals add constraint ck_integration_principals_type check(principal_type in('SERVICE_ACCOUNT','TECHNICAL_USER','OAUTH_APPLICATION','USER_DELEGATED','BREAK_GLASS'));
alter table integration_principals add constraint ck_integration_principals_status check(status in('DRAFT','VALIDATING','ACTIVE','DEGRADED','EXPIRED','REVOKED','DISABLED','OVER_PRIVILEGED'));
alter table integration_project_mappings add constraint ck_integration_mapping_status check(mapping_status in('DRAFT','VALID','DEGRADED','MISCONFIGURED','EXPIRED','REVOKED','OVER_PRIVILEGED','UNREACHABLE','DISABLED'));
alter table integration_permission_probe_results add constraint ck_integration_probe_result check(result_status in('GRANTED','DENIED','UNSUPPORTED','NOT_TESTED','ERROR'));

-- Mapping resolution must be deterministic within a specificity level.
create unique index if not exists uq_integration_mapping_default_scope on integration_project_mappings(
 tenant_id, connection_id,
 coalesce(department_id,''),coalesce(group_id,''),coalesce(service_domain_id,''),coalesce(source_system_id,''),coalesce(task_type,''),resolution_priority
) where enabled=true;

-- Credential rotation events and probe evidence are immutable audit records.
create or replace function phase0e_reject_audit_mutation() returns trigger language plpgsql as $$
begin raise exception 'Phase 0E integration evidence is immutable'; end $$;
do $$ declare t text; begin
  foreach t in array array['integration_permission_probe_results','integration_credential_rotation_events'] loop
    execute format('drop trigger if exists trg_phase0e_evidence_immutable on %I',t);
    execute format('create trigger trg_phase0e_evidence_immutable before update or delete on %I for each row execute function phase0e_reject_audit_mutation()',t);
  end loop;
end $$;

-- FK references for operation principals are added individually so PostgreSQL enforces Tenant identity.
do $$ declare c text; col text; begin
  foreach col in array array['read_principal_id','create_principal_id','comment_principal_id','update_principal_id','relation_principal_id','webhook_principal_id'] loop
    c='fk_integration_mapping_'||replace(col,'_principal_id','')||'_principal';
    if not exists(select 1 from pg_constraint where conname=c) then
      execute format('alter table integration_project_mappings add constraint %I foreign key(tenant_id,%I) references integration_principals(tenant_id,principal_id) not valid',c,col);
    end if;
  end loop;
end $$;



do $$ begin
  if not exists(select 1 from pg_constraint where conname='fk_integration_probe_runs_connection') then
    alter table integration_permission_probe_runs add constraint fk_integration_probe_runs_connection foreign key(tenant_id,connection_id) references integration_connections(tenant_id,connection_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_integration_probe_runs_principal') then
    alter table integration_permission_probe_runs add constraint fk_integration_probe_runs_principal foreign key(tenant_id,principal_id) references integration_principals(tenant_id,principal_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_integration_probe_results_run') then
    alter table integration_permission_probe_results add constraint fk_integration_probe_results_run foreign key(tenant_id,probe_id) references integration_permission_probe_runs(tenant_id,probe_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_integration_rotation_principal') then
    alter table integration_credential_rotation_events add constraint fk_integration_rotation_principal foreign key(tenant_id,principal_id) references integration_principals(tenant_id,principal_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_integration_rotation_new_credential') then
    alter table integration_credential_rotation_events add constraint fk_integration_rotation_new_credential foreign key(tenant_id,new_credential_id) references integration_credentials(tenant_id,credential_id) not valid;
  end if;
end $$;

create or replace function phase0e_seed_tenant_permission_profiles() returns trigger language plpgsql as $$
begin
  insert into integration_permission_profiles(tenant_id,permission_profile_id,profile_code,profile_name,required_capabilities_json,optional_capabilities_json,description)
  values(new.tenant_id,'profile-issue-create','ISSUE_CREATE','Create external issues','["AUTHENTICATE","PROJECT_VISIBLE","CREATE_ISSUE"]'::jsonb,'["ADD_COMMENT","CREATE_RELATION","OBSERVE_STATUS"]'::jsonb,'Minimum permission contract for creating an external issue.'),
        (new.tenant_id,'profile-issue-relay','ISSUE_RELAY','Cross-project issue relay','["AUTHENTICATE","PROJECT_VISIBLE","CREATE_ISSUE","ADD_COMMENT"]'::jsonb,'["READ_ISSUE","CREATE_RELATION","READ_ATTACHMENT_METADATA"]'::jsonb,'Minimum permission contract for dual-sided issue relay.')
  on conflict do nothing;
  return new;
end $$;
drop trigger if exists trg_phase0e_seed_tenant_permission_profiles on tenants;
create trigger trg_phase0e_seed_tenant_permission_profiles after insert on tenants for each row execute function phase0e_seed_tenant_permission_profiles();

alter table integration_connections validate constraint fk_integration_connections_tenant;
alter table integration_principals validate constraint fk_integration_principals_connection;
alter table integration_credentials validate constraint fk_integration_credentials_principal;
alter table integration_project_mappings validate constraint fk_integration_mappings_connection;
alter table integration_project_mappings validate constraint fk_integration_mappings_permission_profile;
