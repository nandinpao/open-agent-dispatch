-- Phase 5A enforcement: catalog revision immutability and authority constraints.

alter table permission_catalog_revisions
  add constraint ck_permission_catalog_revision_status
    check(status in('DRAFT','PUBLISHED','SUPERSEDED','RETIRED')),
  add constraint ck_permission_catalog_revision_number check(revision_number > 0),
  add constraint ck_permission_catalog_revision_publish
    check((status='DRAFT' and published_at is null and published_by is null)
       or (status<>'DRAFT' and published_at is not null and published_by is not null)),
  add constraint ck_permission_catalog_revision_version check(version > 0);

alter table permission_definitions
  add constraint ck_permission_definition_owner_module check(length(trim(owner_module)) > 0),
  add constraint ck_permission_definition_risk_lane check(risk_lane in('READ','WRITE','EXPORT','ADMIN','CRITICAL')),
  add constraint ck_permission_definition_lifecycle check(lifecycle in('DRAFT','ACTIVE','DEPRECATED','RETIRED')),
  add constraint ck_permission_definition_retirement
    check((lifecycle <> 'DEPRECATED' or deprecated_at is not null)
      and (lifecycle <> 'RETIRED' or retired_at is not null or active=false)),
  add constraint ck_permission_definition_replacement check(replacement_permission_code is null or replacement_permission_code <> permission_code);

alter table permission_catalog_aliases
  add constraint ck_permission_alias_type check(alias_type in('RENAMED','LEGACY','COMPATIBILITY')),
  add constraint ck_permission_alias_window check(valid_until is null or valid_until > valid_from),
  add constraint ck_permission_alias_not_self check(alias_code <> canonical_permission_code),
  add constraint ck_permission_alias_version check(version > 0);

alter table iam_users
  add constraint ck_iam_user_account_type check(account_type in('STANDARD','SERVICE','BREAK_GLASS')),
  add constraint ck_iam_user_authentication_type check(authentication_type in('LOCAL','OIDC','SAML','LDAP'));

alter table iam_root_identities
  add constraint ck_iam_root_bootstrap_origin check(bootstrap_origin in('INTERACTIVE','INSTALLATION_SECRET','RECOVERY'));

alter table auth_root_bootstrap_state
  add constraint ck_auth_root_bootstrap_source check(bootstrap_source in('INTERACTIVE','INSTALLATION_SECRET'));

create or replace function phase5a_guard_permission_revision()
returns trigger language plpgsql as $$
begin
  if old.status in ('PUBLISHED','SUPERSEDED','RETIRED') then
    raise exception 'PERMISSION_CATALOG_REVISION_IMMUTABLE';
  end if;
  return case when tg_op='DELETE' then old else new end;
end $$;

drop trigger if exists trg_permission_catalog_revision_immutable on permission_catalog_revisions;
create trigger trg_permission_catalog_revision_immutable
before update or delete on permission_catalog_revisions
for each row execute function phase5a_guard_permission_revision();

create or replace function phase5a_guard_permission_change_event()
returns trigger language plpgsql as $$
begin
  raise exception 'PERMISSION_CATALOG_CHANGE_EVENT_APPEND_ONLY';
end $$;

drop trigger if exists trg_permission_catalog_change_event_append_only on permission_catalog_change_events;
create trigger trg_permission_catalog_change_event_append_only
before update or delete on permission_catalog_change_events
for each row execute function phase5a_guard_permission_change_event();

create or replace function phase5a_guard_active_catalog_revision()
returns trigger language plpgsql as $$
declare state varchar(24);
begin
  select status into state from permission_catalog_revisions where revision_id=new.revision_id;
  if state <> 'PUBLISHED' then
    raise exception 'ACTIVE_PERMISSION_CATALOG_REVISION_MUST_BE_PUBLISHED';
  end if;
  if new.singleton_id <> 'ACTIVE' then
    raise exception 'INVALID_PERMISSION_CATALOG_SINGLETON';
  end if;
  return new;
end $$;

drop trigger if exists trg_permission_catalog_active_revision_guard on permission_catalog_active_revision;
create trigger trg_permission_catalog_active_revision_guard
before insert or update on permission_catalog_active_revision
for each row execute function phase5a_guard_active_catalog_revision();

create index if not exists idx_permission_definitions_owner_lifecycle
  on permission_definitions(owner_module,lifecycle,permission_code);
create index if not exists idx_permission_definitions_risk_lane
  on permission_definitions(risk_lane,lifecycle,permission_code);
