-- P1D enforcement: the legacy A2A CANCELLED value is no longer accepted.

do $$
begin
  if not exists (
      select 1 from pg_constraint where conname = 'ck_a2a_request_status_p1d') then
    alter table a2a_requests
      add constraint ck_a2a_request_status_p1d check (request_status in (
        'REQUESTED','VALIDATING','REJECTED','WAITING_APPROVAL','APPROVED',
        'CHILD_TASK_CREATED','DISPATCHING','RUNNING','COMPLETED','FAILED',
        'CANCEL_REQUESTED','CANCELLED_CONFIRMED','CANCELLED_UNCONFIRMED',
        'WAIT_HUMAN','EXPIRED'
      )) not valid;
  end if;

  if not exists (
      select 1 from pg_constraint where conname = 'ck_a2a_history_from_status_p1d') then
    alter table a2a_state_history
      add constraint ck_a2a_history_from_status_p1d check (
        from_status is null or from_status in (
          'REQUESTED','VALIDATING','REJECTED','WAITING_APPROVAL','APPROVED',
          'CHILD_TASK_CREATED','DISPATCHING','RUNNING','COMPLETED','FAILED',
          'CANCEL_REQUESTED','CANCELLED_CONFIRMED','CANCELLED_UNCONFIRMED',
          'WAIT_HUMAN','EXPIRED'
        )) not valid;
  end if;

  if not exists (
      select 1 from pg_constraint where conname = 'ck_a2a_history_to_status_p1d') then
    alter table a2a_state_history
      add constraint ck_a2a_history_to_status_p1d check (to_status in (
        'REQUESTED','VALIDATING','REJECTED','WAITING_APPROVAL','APPROVED',
        'CHILD_TASK_CREATED','DISPATCHING','RUNNING','COMPLETED','FAILED',
        'CANCEL_REQUESTED','CANCELLED_CONFIRMED','CANCELLED_UNCONFIRMED',
        'WAIT_HUMAN','EXPIRED'
      )) not valid;
  end if;
end $$;

alter table a2a_requests validate constraint ck_a2a_request_status_p1d;
alter table a2a_state_history validate constraint ck_a2a_history_from_status_p1d;
alter table a2a_state_history validate constraint ck_a2a_history_to_status_p1d;
