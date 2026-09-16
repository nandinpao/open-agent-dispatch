-- Phase 0G: Issue Projection and Sync Reliability enforcement.

do $$ begin
  if not exists(select 1 from pg_constraint where conname='fk_issue_relationship_from_link') then
    alter table issue_relationships add constraint fk_issue_relationship_from_link
      foreign key(tenant_id,from_task_issue_link_id) references task_issue_links(tenant_id,link_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_issue_relationship_to_link') then
    alter table issue_relationships add constraint fk_issue_relationship_to_link
      foreign key(tenant_id,to_task_issue_link_id) references task_issue_links(tenant_id,link_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_outbox_task') then
    alter table integration_outbox add constraint fk_outbox_task
      foreign key(tenant_id,task_id) references tasks(tenant_id,task_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_outbox_task_issue_link') then
    alter table integration_outbox add constraint fk_outbox_task_issue_link
      foreign key(tenant_id,task_issue_link_id) references task_issue_links(tenant_id,link_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_outbox_issue_relationship') then
    alter table integration_outbox add constraint fk_outbox_issue_relationship
      foreign key(tenant_id,issue_relationship_id) references issue_relationships(tenant_id,relationship_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_outbox_connection') then
    alter table integration_outbox add constraint fk_outbox_connection
      foreign key(tenant_id,connection_id) references integration_connections(tenant_id,connection_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_outbox_mapping') then
    alter table integration_outbox add constraint fk_outbox_mapping
      foreign key(tenant_id,project_mapping_id) references integration_project_mappings(tenant_id,mapping_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_inbox_connection') then
    alter table integration_inbox add constraint fk_inbox_connection
      foreign key(tenant_id,connection_id) references integration_connections(tenant_id,connection_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_sync_attempt_outbox') then
    alter table integration_sync_attempts add constraint fk_sync_attempt_outbox
      foreign key(tenant_id,outbox_id) references integration_outbox(tenant_id,outbox_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_dead_letter_outbox') then
    alter table integration_dead_letters add constraint fk_dead_letter_outbox
      foreign key(tenant_id,outbox_id) references integration_outbox(tenant_id,outbox_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_conflict_task') then
    alter table integration_conflicts add constraint fk_conflict_task
      foreign key(tenant_id,task_id) references tasks(tenant_id,task_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_conflict_link') then
    alter table integration_conflicts add constraint fk_conflict_link
      foreign key(tenant_id,task_issue_link_id) references task_issue_links(tenant_id,link_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_conflict_inbox') then
    alter table integration_conflicts add constraint fk_conflict_inbox
      foreign key(tenant_id,inbox_id) references integration_inbox(tenant_id,inbox_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_circuit_connection') then
    alter table integration_circuit_breakers add constraint fk_circuit_connection
      foreign key(tenant_id,connection_id) references integration_connections(tenant_id,connection_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_circuit_mapping') then
    alter table integration_circuit_breakers add constraint fk_circuit_mapping
      foreign key(tenant_id,project_mapping_id) references integration_project_mappings(tenant_id,mapping_id) not valid;
  end if;
end $$;

do $$ begin
  if not exists(select 1 from pg_constraint where conname='ck_task_issue_link_role') then
    alter table task_issue_links add constraint ck_task_issue_link_role check(link_role in ('PRIMARY','CHILD_PROCESS','REFERENCE','AUDIT','MANUAL'));
  end if;
  if not exists(select 1 from pg_constraint where conname='ck_task_issue_projection_strategy') then
    alter table task_issue_links add constraint ck_task_issue_projection_strategy check(projection_strategy in ('NONE','APPEND_TO_PARENT_ISSUE','CREATE_NEW_ISSUE','CREATE_NEW_AND_RELATE','LINK_EXISTING_ISSUE','MANUAL_DECISION'));
  end if;
  if not exists(select 1 from pg_constraint where conname='ck_task_issue_sync_status') then
    alter table task_issue_links add constraint ck_task_issue_sync_status check(sync_status in ('NOT_REQUIRED','PENDING','IN_PROGRESS','SYNCED','FAILED_RETRYABLE','FAILED_PERMANENT','CONFLICT','DISABLED'));
  end if;
  if not exists(select 1 from pg_constraint where conname='ck_issue_relationship_type') then
    alter table issue_relationships add constraint ck_issue_relationship_type check(relationship_type in ('RELATES','BLOCKS','BLOCKED_BY','DUPLICATES','DUPLICATED_BY','PARENT_OF','CHILD_OF','FOLLOWS','PRECEDES','CAUSED_BY','RESOLVES','REFERENCES'));
  end if;
  if not exists(select 1 from pg_constraint where conname='ck_integration_outbox_status') then
    alter table integration_outbox add constraint ck_integration_outbox_status check(status in ('PENDING','CLAIMED','IN_PROGRESS','COMPLETED','FAILED_RETRYABLE','FAILED_PERMANENT','DEAD_LETTER','CANCELLED'));
  end if;
  if not exists(select 1 from pg_constraint where conname='ck_integration_inbox_status') then
    alter table integration_inbox add constraint ck_integration_inbox_status check(status in ('RECEIVED','PROCESSING','PROCESSED','REPLAYED','REJECTED','FAILED'));
  end if;
  if not exists(select 1 from pg_constraint where conname='ck_dead_letter_status') then
    alter table integration_dead_letters add constraint ck_dead_letter_status check(status in ('OPEN','RETRYING','RETRIED','RESOLVED','IGNORED'));
  end if;
  if not exists(select 1 from pg_constraint where conname='ck_conflict_status') then
    alter table integration_conflicts add constraint ck_conflict_status check(status in ('OPEN','ACKNOWLEDGED','RESOLVED','IGNORED'));
  end if;
  if not exists(select 1 from pg_constraint where conname='ck_circuit_state') then
    alter table integration_circuit_breakers add constraint ck_circuit_state check(state in ('CLOSED','OPEN','HALF_OPEN'));
  end if;
end $$;

create or replace function phase0g_task_issue_sync_guard() returns trigger as $$
begin
  if old.tenant_id is distinct from new.tenant_id or old.link_id is distinct from new.link_id or old.task_id is distinct from new.task_id then
    raise exception 'ISSUE_LINK_IDENTITY_IMMUTABLE';
  end if;
  if new.resource_version <> old.resource_version + 1 then
    raise exception 'RESOURCE_VERSION_CONFLICT: task_issue_links';
  end if;
  if not (
    new.sync_status = old.sync_status or
    (old.sync_status='PENDING' and new.sync_status in ('IN_PROGRESS','FAILED_RETRYABLE','FAILED_PERMANENT','CONFLICT','DISABLED')) or
    (old.sync_status='IN_PROGRESS' and new.sync_status in ('SYNCED','FAILED_RETRYABLE','FAILED_PERMANENT','CONFLICT')) or
    (old.sync_status='FAILED_RETRYABLE' and new.sync_status in ('PENDING','IN_PROGRESS','FAILED_PERMANENT','DISABLED')) or
    (old.sync_status='FAILED_PERMANENT' and new.sync_status in ('PENDING','IN_PROGRESS','DISABLED')) or
    (old.sync_status='SYNCED' and new.sync_status in ('CONFLICT','PENDING','DISABLED')) or
    (old.sync_status='CONFLICT' and new.sync_status in ('PENDING','SYNCED','DISABLED'))
  ) then raise exception 'ISSUE_SYNC_STATE_TRANSITION_DENIED: % -> %', old.sync_status,new.sync_status; end if;
  new.updated_at=now(); return new;
end $$ language plpgsql;
drop trigger if exists trg_phase0g_task_issue_sync_guard on task_issue_links;
create trigger trg_phase0g_task_issue_sync_guard before update on task_issue_links for each row execute function phase0g_task_issue_sync_guard();

create or replace function phase0g_outbox_guard() returns trigger as $$
begin
  if old.tenant_id is distinct from new.tenant_id or old.outbox_id is distinct from new.outbox_id or old.payload_hash is distinct from new.payload_hash or old.idempotency_key is distinct from new.idempotency_key then
    raise exception 'INTEGRATION_OUTBOX_IDENTITY_IMMUTABLE';
  end if;
  if not (
    new.status=old.status or
    (old.status in ('PENDING','FAILED_RETRYABLE') and new.status in ('CLAIMED','IN_PROGRESS','CANCELLED')) or
    (old.status='CLAIMED' and new.status in ('IN_PROGRESS','PENDING','FAILED_RETRYABLE')) or
    (old.status='IN_PROGRESS' and new.status in ('COMPLETED','FAILED_RETRYABLE','FAILED_PERMANENT','DEAD_LETTER'))
  ) then raise exception 'INTEGRATION_OUTBOX_STATE_TRANSITION_DENIED: % -> %',old.status,new.status; end if;
  new.updated_at=now(); return new;
end $$ language plpgsql;
drop trigger if exists trg_phase0g_outbox_guard on integration_outbox;
create trigger trg_phase0g_outbox_guard before update on integration_outbox for each row execute function phase0g_outbox_guard();

create or replace function phase0g_inbox_guard() returns trigger as $$
begin
  if old.tenant_id is distinct from new.tenant_id or old.inbox_id is distinct from new.inbox_id or old.connection_id is distinct from new.connection_id or old.provider_event_id is distinct from new.provider_event_id or old.payload_hash is distinct from new.payload_hash or old.payload_json is distinct from new.payload_json then
    raise exception 'INTEGRATION_INBOX_EVIDENCE_IMMUTABLE';
  end if;
  if not (new.status=old.status or (old.status='RECEIVED' and new.status in ('PROCESSING','REPLAYED','REJECTED','FAILED')) or (old.status='PROCESSING' and new.status in ('PROCESSED','FAILED'))) then
    raise exception 'INTEGRATION_INBOX_STATE_TRANSITION_DENIED: % -> %',old.status,new.status;
  end if;
  return new;
end $$ language plpgsql;
drop trigger if exists trg_phase0g_inbox_guard on integration_inbox;
create trigger trg_phase0g_inbox_guard before update on integration_inbox for each row execute function phase0g_inbox_guard();

create or replace function phase0g_immutable_evidence() returns trigger as $$ begin raise exception 'INTEGRATION_SYNC_EVIDENCE_IMMUTABLE: %',tg_table_name; end $$ language plpgsql;

drop trigger if exists trg_phase0g_attempt_immutable on integration_sync_attempts;
create trigger trg_phase0g_attempt_immutable before update or delete on integration_sync_attempts for each row execute function phase0g_immutable_evidence();

drop trigger if exists trg_phase0g_relationship_identity on issue_relationships;
create or replace function phase0g_relationship_guard() returns trigger as $$
begin
 if old.tenant_id is distinct from new.tenant_id or old.relationship_id is distinct from new.relationship_id or old.from_task_issue_link_id is distinct from new.from_task_issue_link_id or old.to_task_issue_link_id is distinct from new.to_task_issue_link_id or old.relationship_type is distinct from new.relationship_type or old.idempotency_key is distinct from new.idempotency_key then raise exception 'ISSUE_RELATIONSHIP_IDENTITY_IMMUTABLE'; end if;
 if new.resource_version <> old.resource_version+1 then raise exception 'RESOURCE_VERSION_CONFLICT: issue_relationships'; end if;
 new.updated_at=now(); return new;
end $$ language plpgsql;
create trigger trg_phase0g_relationship_identity before update on issue_relationships for each row execute function phase0g_relationship_guard();

alter table issue_relationships validate constraint fk_issue_relationship_from_link;
alter table issue_relationships validate constraint fk_issue_relationship_to_link;
alter table integration_outbox validate constraint fk_outbox_task;
alter table integration_outbox validate constraint fk_outbox_task_issue_link;
alter table integration_outbox validate constraint fk_outbox_issue_relationship;
alter table integration_outbox validate constraint fk_outbox_connection;
alter table integration_outbox validate constraint fk_outbox_mapping;
alter table integration_inbox validate constraint fk_inbox_connection;
alter table integration_sync_attempts validate constraint fk_sync_attempt_outbox;
alter table integration_dead_letters validate constraint fk_dead_letter_outbox;
alter table integration_conflicts validate constraint fk_conflict_task;
alter table integration_conflicts validate constraint fk_conflict_link;
alter table integration_conflicts validate constraint fk_conflict_inbox;
alter table integration_circuit_breakers validate constraint fk_circuit_connection;
alter table integration_circuit_breakers validate constraint fk_circuit_mapping;
