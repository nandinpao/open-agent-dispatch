-- Phase 0E-1: Project Isolation and Credential Federation enforcement.

do $$ begin
  if not exists(select 1 from pg_constraint where conname='fk_integration_principal_scope_principal') then
    alter table integration_principal_scopes add constraint fk_integration_principal_scope_principal
      foreign key(tenant_id,principal_id) references integration_principals(tenant_id,principal_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_integration_override_principal') then
    alter table integration_security_overrides add constraint fk_integration_override_principal
      foreign key(tenant_id,principal_id) references integration_principals(tenant_id,principal_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_integration_override_mapping') then
    alter table integration_security_overrides add constraint fk_integration_override_mapping
      foreign key(tenant_id,mapping_id) references integration_project_mappings(tenant_id,mapping_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_integration_permission_change_principal') then
    alter table integration_permission_change_events add constraint fk_integration_permission_change_principal
      foreign key(tenant_id,principal_id) references integration_principals(tenant_id,principal_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_integration_auth_failure_principal') then
    alter table integration_provider_authorization_failures add constraint fk_integration_auth_failure_principal
      foreign key(tenant_id,principal_id) references integration_principals(tenant_id,principal_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_integration_permission_change_mapping') then
    alter table integration_permission_change_events add constraint fk_integration_permission_change_mapping
      foreign key(tenant_id,mapping_id) references integration_project_mappings(tenant_id,mapping_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_integration_permission_change_previous_probe') then
    alter table integration_permission_change_events add constraint fk_integration_permission_change_previous_probe
      foreign key(tenant_id,previous_probe_id) references integration_permission_probe_runs(tenant_id,probe_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_integration_permission_change_current_probe') then
    alter table integration_permission_change_events add constraint fk_integration_permission_change_current_probe
      foreign key(tenant_id,current_probe_id) references integration_permission_probe_runs(tenant_id,probe_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_integration_auth_failure_connection') then
    alter table integration_provider_authorization_failures add constraint fk_integration_auth_failure_connection
      foreign key(tenant_id,connection_id) references integration_connections(tenant_id,connection_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_integration_auth_failure_credential') then
    alter table integration_provider_authorization_failures add constraint fk_integration_auth_failure_credential
      foreign key(tenant_id,credential_id) references integration_credentials(tenant_id,credential_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_integration_auth_failure_mapping') then
    alter table integration_provider_authorization_failures add constraint fk_integration_auth_failure_mapping
      foreign key(tenant_id,mapping_id) references integration_project_mappings(tenant_id,mapping_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_integration_rotation_old_credential') then
    alter table integration_credential_rotation_events add constraint fk_integration_rotation_old_credential
      foreign key(tenant_id,old_credential_id) references integration_credentials(tenant_id,credential_id) not valid;
  end if;
end $$;

alter table integration_principal_scopes drop constraint if exists ck_integration_principal_scope_mode;
alter table integration_principal_scopes add constraint ck_integration_principal_scope_mode
  check(isolation_mode in('PER_PROJECT','PER_TRUST_ZONE','SHARED_SCOPED','GLOBAL_ADMIN'));
alter table integration_security_overrides drop constraint if exists ck_integration_override_status;
alter table integration_security_overrides add constraint ck_integration_override_status
  check(status in('APPROVED','EXPIRED','REVOKED'));
alter table integration_security_overrides drop constraint if exists ck_integration_override_expiration;
alter table integration_security_overrides add constraint ck_integration_override_expiration
  check(expires_at > approved_at);
alter table integration_provider_authorization_failures drop constraint if exists ck_integration_auth_failure_status;
alter table integration_provider_authorization_failures add constraint ck_integration_auth_failure_status
  check(provider_status in(401,403));

create or replace function phase0e1_validate_principal_scope() returns trigger language plpgsql as $$
declare principal_type_value varchar(32); principal_zone varchar(128);
begin
  select principal_type, trust_zone_id into principal_type_value, principal_zone
    from integration_principals where tenant_id=new.tenant_id and principal_id=new.principal_id;
  if principal_type_value is null then raise exception 'INTEGRATION_PRINCIPAL_NOT_FOUND'; end if;
  if new.isolation_mode='GLOBAL_ADMIN' and principal_type_value<>'BREAK_GLASS' then
    raise exception 'GLOBAL_ADMIN_SCOPE_REQUIRES_BREAK_GLASS_PRINCIPAL';
  end if;
  if new.isolation_mode='PER_TRUST_ZONE' and (principal_zone is null or new.scope_reference is distinct from principal_zone) then
    raise exception 'TRUST_ZONE_SCOPE_MISMATCH';
  end if;
  if new.isolation_mode='PER_TRUST_ZONE' and jsonb_array_length(new.allowed_project_ids_json)=0 then
    raise exception 'TRUST_ZONE_PROJECT_ALLOW_LIST_REQUIRED';
  end if;
  if new.isolation_mode='SHARED_SCOPED' and jsonb_array_length(new.allowed_project_ids_json)=0 then
    raise exception 'SHARED_SCOPED_PROJECT_ALLOW_LIST_REQUIRED';
  end if;
  if new.isolation_mode='PER_PROJECT' and (new.scope_reference is null or btrim(new.scope_reference)='') then
    raise exception 'PER_PROJECT_SCOPE_REFERENCE_REQUIRED';
  end if;
  if new.isolation_mode='PER_PROJECT' and not (new.allowed_project_ids_json ? new.scope_reference) then
    raise exception 'PER_PROJECT_ALLOW_LIST_MUST_INCLUDE_SCOPE_REFERENCE';
  end if;
  if new.isolation_mode='GLOBAL_ADMIN' and new.production_allowed then
    raise exception 'GLOBAL_ADMIN_SCOPE_CANNOT_BE_PRODUCTION_ALLOWED';
  end if;
  return new;
end $$;
drop trigger if exists trg_phase0e1_validate_principal_scope on integration_principal_scopes;
create trigger trg_phase0e1_validate_principal_scope before insert or update on integration_principal_scopes
for each row execute function phase0e1_validate_principal_scope();

create or replace function phase0e1_protect_security_evidence() returns trigger language plpgsql as $$
begin raise exception 'Phase 0E-1 security evidence is immutable'; end $$;

drop trigger if exists trg_phase0e1_immutable_integration_permission_change_events on integration_permission_change_events;
create trigger trg_phase0e1_immutable_integration_permission_change_events
before update or delete on integration_permission_change_events
for each row execute function phase0e1_protect_security_evidence();

-- Authorization failure facts are immutable, while the re-probe lifecycle may move once
-- from PENDING to COMPLETED or FAILED.
create or replace function phase0e1_guard_authorization_failure_lifecycle() returns trigger language plpgsql as $$
begin
  if tg_op='DELETE' then raise exception 'Phase 0E-1 authorization failure evidence cannot be deleted'; end if;
  if new.tenant_id is distinct from old.tenant_id
     or new.failure_id is distinct from old.failure_id
     or new.connection_id is distinct from old.connection_id
     or new.principal_id is distinct from old.principal_id
     or new.credential_id is distinct from old.credential_id
     or new.mapping_id is distinct from old.mapping_id
     or new.provider_status is distinct from old.provider_status
     or new.operation_code is distinct from old.operation_code
     or new.reason_code is distinct from old.reason_code
     or new.correlation_id is distinct from old.correlation_id
     or new.occurred_at is distinct from old.occurred_at then
    raise exception 'Phase 0E-1 authorization failure facts are immutable';
  end if;
  if old.reprobe_status<>'PENDING' or new.reprobe_status not in('COMPLETED','FAILED') or new.reprobed_at is null then
    raise exception 'INVALID_AUTHORIZATION_FAILURE_REPROBE_TRANSITION';
  end if;
  return new;
end $$;
drop trigger if exists trg_phase0e1_authorization_failure_lifecycle on integration_provider_authorization_failures;
create trigger trg_phase0e1_authorization_failure_lifecycle
before update or delete on integration_provider_authorization_failures
for each row execute function phase0e1_guard_authorization_failure_lifecycle();


-- Phase 0E made rotation evidence immutable. Phase 0E-1 preserves immutable identity facts
-- while allowing the governed rotation lifecycle to advance.
drop trigger if exists trg_phase0e_evidence_immutable on integration_credential_rotation_events;
create or replace function phase0e1_guard_rotation_lifecycle() returns trigger language plpgsql as $$
begin
  if tg_op='DELETE' then raise exception 'Credential rotation evidence cannot be deleted'; end if;
  if new.tenant_id is distinct from old.tenant_id
     or new.rotation_event_id is distinct from old.rotation_event_id
     or new.principal_id is distinct from old.principal_id
     or new.old_credential_id is distinct from old.old_credential_id
     or new.new_credential_id is distinct from old.new_credential_id
     or new.correlation_id is distinct from old.correlation_id
     or new.actor_type is distinct from old.actor_type
     or new.actor_id is distinct from old.actor_id
     or new.created_at is distinct from old.created_at then
    raise exception 'Credential rotation identity facts are immutable';
  end if;
  if old.status='PENDING_PROBE' and new.status='ACTIVE_GRACE_PERIOD' then
    if new.activated_at is null or new.grace_expires_at is null or new.grace_expires_at<=new.activated_at then
      raise exception 'INVALID_CREDENTIAL_ROTATION_GRACE_PERIOD';
    end if;
    return new;
  end if;
  if old.status='ACTIVE_GRACE_PERIOD' and new.status='COMPLETED' then
    if new.completed_at is null then raise exception 'CREDENTIAL_ROTATION_COMPLETION_TIME_REQUIRED'; end if;
    return new;
  end if;
  raise exception 'INVALID_CREDENTIAL_ROTATION_STATUS_TRANSITION';
end $$;
drop trigger if exists trg_phase0e1_rotation_lifecycle on integration_credential_rotation_events;
create trigger trg_phase0e1_rotation_lifecycle
before update or delete on integration_credential_rotation_events
for each row execute function phase0e1_guard_rotation_lifecycle();

create or replace function phase0e1_prevent_expired_override_activation() returns trigger language plpgsql as $$
begin
  if new.status='APPROVED' and new.expires_at<=now() then raise exception 'SECURITY_OVERRIDE_EXPIRED'; end if;
  return new;
end $$;
drop trigger if exists trg_phase0e1_override_expiration on integration_security_overrides;
create trigger trg_phase0e1_override_expiration before insert or update on integration_security_overrides
for each row execute function phase0e1_prevent_expired_override_activation();

alter table integration_principal_scopes validate constraint fk_integration_principal_scope_principal;
alter table integration_security_overrides validate constraint fk_integration_override_principal;
alter table integration_permission_change_events validate constraint fk_integration_permission_change_principal;
alter table integration_provider_authorization_failures validate constraint fk_integration_auth_failure_principal;
alter table integration_security_overrides validate constraint fk_integration_override_mapping;
alter table integration_permission_change_events validate constraint fk_integration_permission_change_mapping;
alter table integration_permission_change_events validate constraint fk_integration_permission_change_previous_probe;
alter table integration_permission_change_events validate constraint fk_integration_permission_change_current_probe;
alter table integration_provider_authorization_failures validate constraint fk_integration_auth_failure_connection;
alter table integration_provider_authorization_failures validate constraint fk_integration_auth_failure_credential;
alter table integration_provider_authorization_failures validate constraint fk_integration_auth_failure_mapping;
alter table integration_credential_rotation_events validate constraint fk_integration_rotation_old_credential;
