-- Phase 5C enforcement: singleton Root survival, bootstrap evidence immutability, and source consistency.

alter table auth_root_bootstrap_state
  add constraint ck_auth_root_installation_credential_version
    check(installation_credential_version is null or installation_credential_version > 0),
  add constraint ck_auth_root_installation_change_order
    check(initial_password_changed_at is null
       or installation_completed_at is null
       or initial_password_changed_at >= installation_completed_at);

alter table auth_root_installation_events
  add constraint ck_auth_root_installation_event_type
    check(event_type in(
      'ROOT_INSTALLATION_CREATED',
      'ROOT_INSTALLATION_SECRET_IGNORED',
      'ROOT_INITIAL_PASSWORD_CHANGED',
      'ROOT_INSTALLATION_RECOVERY_REHEARSED'
    )),
  add constraint ck_auth_root_installation_source
    check(bootstrap_source in('INSTALLATION_SECRET','RECOVERY')),
  add constraint ck_auth_root_installation_event_credential_version
    check(credential_version is null or credential_version > 0);

create or replace function phase5c_guard_root_installation_event()
returns trigger language plpgsql as $$
begin
  raise exception 'ROOT_INSTALLATION_EVENT_APPEND_ONLY';
end $$;

drop trigger if exists trg_root_installation_event_append_only on auth_root_installation_events;
create trigger trg_root_installation_event_append_only
before update or delete on auth_root_installation_events
for each row execute function phase5c_guard_root_installation_event();

create or replace function phase5c_guard_singleton_root_survival()
returns trigger language plpgsql as $$
begin
  if tg_op='DELETE' then
    raise exception 'LAST_PLATFORM_ROOT_CANNOT_BE_DELETED';
  end if;
  if new.root_identity_id='root' and new.status='DISABLED' then
    raise exception 'LAST_PLATFORM_ROOT_CANNOT_BE_DISABLED';
  end if;
  return new;
end $$;

drop trigger if exists trg_singleton_root_survival on iam_root_identities;
create trigger trg_singleton_root_survival
before update or delete on iam_root_identities
for each row execute function phase5c_guard_singleton_root_survival();
