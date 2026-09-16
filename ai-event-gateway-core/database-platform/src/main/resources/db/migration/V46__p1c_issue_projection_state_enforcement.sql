-- P1C enforcement: valid projection states, CAS and immutable source identity.
alter table issue_projection_states
    add constraint chk_issue_projection_desired_state
        check (desired_state in ('NONE','ACTIVE','SUSPENDED','DELETED'));
alter table issue_projection_states
    add constraint chk_issue_projection_observed_state
        check (observed_state in ('UNKNOWN','NOT_FOUND','CREATING','ACTIVE','FAILED','CONFLICT','SUSPENDED','DELETED'));
alter table issue_projection_states
    add constraint chk_issue_projection_lifecycle_state
        check (lifecycle_state in ('NOT_REQUESTED','PENDING','CREATING','ACTIVE','UPDATE_PENDING','FAILED','CONFLICT','SUSPENDED','DEAD_LETTER'));
alter table issue_projection_states
    add constraint chk_issue_projection_conflict_policy
        check (conflict_policy in ('CORE_WINS','EXTERNAL_METADATA_ONLY','MANUAL_REVIEW','SUSPEND_ON_CONFLICT'));
alter table issue_projection_states
    add constraint chk_issue_projection_retry_budget
        check (retry_count >= 0 and max_attempts > 0 and retry_count <= max_attempts);
alter table issue_projection_states
    add constraint chk_issue_projection_version
        check (version > 0);

alter table issue_projection_reconciliation_cases
    add constraint chk_issue_projection_case_status
        check (status in ('OPEN','RETRY_SCHEDULED','WAIT_HUMAN','RESOLVED','IGNORED'));

create or replace function prevent_issue_projection_source_identity_change()
returns trigger language plpgsql as $$
begin
    if old.tenant_id is distinct from new.tenant_id
       or old.projection_id is distinct from new.projection_id
       or old.source_event_id is distinct from new.source_event_id
       or old.source_event_type is distinct from new.source_event_type
       or old.source_aggregate_type is distinct from new.source_aggregate_type
       or old.source_aggregate_id is distinct from new.source_aggregate_id then
        raise exception 'ISSUE_PROJECTION_SOURCE_IDENTITY_IMMUTABLE';
    end if;
    if new.version <> old.version + 1 then
        raise exception 'ISSUE_PROJECTION_VERSION_MUST_INCREMENT_BY_ONE';
    end if;
    return new;
end;
$$;

drop trigger if exists trg_issue_projection_source_identity on issue_projection_states;
create trigger trg_issue_projection_source_identity
before update on issue_projection_states
for each row execute function prevent_issue_projection_source_identity_change();
