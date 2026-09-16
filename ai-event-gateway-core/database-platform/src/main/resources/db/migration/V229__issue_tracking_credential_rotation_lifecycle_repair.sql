-- Issue Tracking credential rotation lifecycle repair.
--
-- Canonical lifecycle for integration_credential_rotation_events is:
--   PENDING_PROBE -> ACTIVE_GRACE_PERIOD -> COMPLETED
--
-- Older application builds could insert PENDING_VALIDATION rotation events even though
-- the database lifecycle guard only accepted PENDING_PROBE. Preserve fail-closed identity
-- immutability while allowing those already-persisted events to complete exactly the same
-- provider-authenticated activation transition.
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

  -- PENDING_VALIDATION is accepted only as a compatibility source state written by
  -- previous application builds. New application code writes PENDING_PROBE.
  if old.status in ('PENDING_PROBE','PENDING_VALIDATION') and new.status='ACTIVE_GRACE_PERIOD' then
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
