-- Phase 3D: canonical Projection aggregate, lifecycle separation and event sequencing.

alter table issue_projection_states add column if not exists projection_aggregate_key text;
alter table issue_projection_states add column if not exists projection_purpose varchar(64) not null default 'PRIMARY_ISSUE';
alter table issue_projection_states add column if not exists canonical_document_schema_version integer not null default 2;
alter table issue_projection_states add column if not exists canonical_document_hash varchar(128);
alter table issue_projection_states add column if not exists failure_classification varchar(64) not null default 'NONE';
alter table issue_projection_states add column if not exists recovery_strategy varchar(64) not null default 'NONE';
alter table issue_projection_states add column if not exists last_applied_domain_event_id varchar(160);
alter table issue_projection_states add column if not exists operation_sequence bigint not null default 1;
alter table issue_projection_states add column if not exists projection_version bigint not null default 1;
alter table issue_projection_states add column if not exists superseded_by varchar(160);

update issue_projection_states
set projection_aggregate_key=coalesce(projection_aggregate_key,
      tenant_id||'|'||coalesce(task_id,source_aggregate_id)||'|'||coalesce(connection_id,'LEGACY_UNBOUND')||'|'||coalesce(project_mapping_id,'LEGACY_UNBOUND')||'|PRIMARY_ISSUE'),
    last_applied_domain_event_id=coalesce(last_applied_domain_event_id,source_event_id),
    canonical_document_hash=coalesce(canonical_document_hash,desired_payload_hash),
    lifecycle_state=case lifecycle_state
      when 'NOT_REQUESTED' then 'REQUESTED'
      when 'PENDING' then 'REQUESTED'
      when 'CREATING' then 'IN_PROGRESS'
      when 'ACTIVE' then 'SYNCED'
      when 'UPDATE_PENDING' then 'READY'
      when 'SUSPENDED' then 'DISABLED'
      else lifecycle_state end,
    failure_classification=case
      when lifecycle_state='FAILED' then 'PROVIDER_TRANSIENT_FAILURE'
      when lifecycle_state='CONFLICT' then 'EXTERNAL_STATE_CONFLICT'
      when lifecycle_state='DEAD_LETTER' then 'RETRY_EXHAUSTED'
      else failure_classification end,
    recovery_strategy=case
      when lifecycle_state='FAILED' then 'RETRY'
      when lifecycle_state='CONFLICT' then 'MANUAL_REVIEW'
      when lifecycle_state='DEAD_LETTER' then 'DEAD_LETTER'
      when lifecycle_state in('DISABLED','SUSPENDED') then 'DISABLE'
      else recovery_strategy end;

alter table issue_projection_states alter column projection_aggregate_key set not null;

-- Legacy releases could contain more than one active Projection for the same logical aggregate.
-- Keep the most recent row as the canonical winner and preserve older rows as immutable superseded history
-- before adding the Phase 3D active aggregate uniqueness fence.
with ranked as (
  select tenant_id, projection_id, projection_aggregate_key,
         first_value(projection_id) over (
           partition by tenant_id,projection_aggregate_key
           order by coalesce(updated_at,created_at,now()) desc, projection_id desc
         ) as winner_projection_id,
         row_number() over (
           partition by tenant_id,projection_aggregate_key
           order by coalesce(updated_at,created_at,now()) desc, projection_id desc
         ) as aggregate_rank
  from issue_projection_states
  where lifecycle_state not in('SUPERSEDED','DISABLED')
)
update issue_projection_states p
set lifecycle_state='SUPERSEDED',
    superseded_by=r.winner_projection_id,
    failure_classification='NONE',
    recovery_strategy='NONE',
    next_retry_at=null,
    updated_at=now()
from ranked r
where p.tenant_id=r.tenant_id
  and p.projection_id=r.projection_id
  and r.aggregate_rank>1;

alter table issue_projection_states drop constraint if exists chk_issue_projection_lifecycle_state;
alter table issue_projection_states add constraint chk_issue_projection_lifecycle_state_p3d
 check(lifecycle_state in('REQUESTED','RESOLVING_MAPPING','VALIDATING_PRINCIPAL','READY','IN_PROGRESS',
   'VERIFYING_EXTERNAL_RESULT','SYNCED','FAILED','CONFLICT','DEAD_LETTER','SUPERSEDED','DISABLED'));
alter table issue_projection_states add constraint chk_issue_projection_purpose_p3d
 check(projection_purpose in('PRIMARY_ISSUE','INCIDENT_ESCALATION','HANDOFF_AUDIT','RESULT_SUMMARY','OPERATIONAL_FOLLOW_UP'));
alter table issue_projection_states add constraint chk_issue_projection_failure_p3d
 check(failure_classification in('NONE','MAPPING_NOT_FOUND','MAPPING_NOT_ACTIVE','MAPPING_SCHEMA_DRIFT',
   'PRINCIPAL_SCOPE_DENIED','PRINCIPAL_PERMISSION_DENIED','CANONICAL_DOCUMENT_INVALID','SENSITIVE_FIELD_BLOCKED',
   'IDEMPOTENCY_CONFLICT','STALE_DOMAIN_EVENT','VERSION_CONFLICT','PROVIDER_REJECTED',
   'PROVIDER_TRANSIENT_FAILURE','EXTERNAL_STATE_CONFLICT','RETRY_EXHAUSTED','DISABLED_BY_OPERATOR'));
alter table issue_projection_states add constraint chk_issue_projection_recovery_p3d
 check(recovery_strategy in('NONE','RETRY','REFRESH_MAPPING','REVALIDATE_PRINCIPAL','VERIFY_EXTERNAL_RESULT',
   'MANUAL_REVIEW','DEAD_LETTER','SUPERSEDE','DISABLE'));
alter table issue_projection_states add constraint chk_issue_projection_sequence_p3d
 check(operation_sequence>0 and projection_version>0 and canonical_document_schema_version>0);
alter table issue_projection_states add constraint chk_issue_projection_supersede_p3d
 check((lifecycle_state='SUPERSEDED' and superseded_by is not null) or lifecycle_state<>'SUPERSEDED');

create unique index if not exists uk_issue_projection_aggregate_active_p3d
 on issue_projection_states(tenant_id,projection_aggregate_key)
 where lifecycle_state not in('SUPERSEDED','DISABLED');
create index if not exists idx_issue_projection_event_sequence_p3d
 on issue_projection_states(tenant_id,last_applied_domain_event_id,operation_sequence desc);
create index if not exists idx_issue_projection_mapping_binding_p3d
 on issue_projection_states(tenant_id,project_mapping_id,project_mapping_version,project_mapping_schema_hash);

create table if not exists issue_projection_event_receipts(
 tenant_id varchar(128) not null,
 domain_event_id varchar(160) not null,
 projection_id varchar(160) not null,
 operation_sequence bigint not null,
 payload_hash varchar(128),
 applied_at timestamptz not null,
 primary key(tenant_id,domain_event_id),
 foreign key(tenant_id,projection_id) references issue_projection_states(tenant_id,projection_id),
 check(operation_sequence>0)
);
create index if not exists idx_issue_projection_event_receipt_projection_p3d
 on issue_projection_event_receipts(tenant_id,projection_id,operation_sequence);

insert into issue_projection_event_receipts(tenant_id,domain_event_id,projection_id,operation_sequence,payload_hash,applied_at)
select tenant_id,coalesce(last_applied_domain_event_id,source_event_id),projection_id,operation_sequence,
       desired_payload_hash,coalesce(updated_at,created_at,now())
from issue_projection_states
on conflict(tenant_id,domain_event_id) do nothing;

create or replace function prevent_issue_projection_event_receipt_mutation_p3d()
returns trigger language plpgsql as $$ begin
 raise exception 'ISSUE_PROJECTION_EVENT_RECEIPT_IMMUTABLE';
end $$;
drop trigger if exists trg_issue_projection_event_receipt_immutable_p3d on issue_projection_event_receipts;
create trigger trg_issue_projection_event_receipt_immutable_p3d
 before update or delete on issue_projection_event_receipts
 for each row execute function prevent_issue_projection_event_receipt_mutation_p3d();

create or replace function enforce_issue_projection_aggregate_p3d()
returns trigger language plpgsql as $$ begin
 if old.tenant_id is distinct from new.tenant_id
    or old.projection_id is distinct from new.projection_id
    or old.projection_aggregate_key is distinct from new.projection_aggregate_key
    or old.projection_purpose is distinct from new.projection_purpose
    or old.task_id is distinct from new.task_id
    or old.connection_id is distinct from new.connection_id
    or old.project_mapping_id is distinct from new.project_mapping_id then
   raise exception 'ISSUE_PROJECTION_AGGREGATE_IDENTITY_IMMUTABLE';
 end if;
 if new.version<>old.version+1 then raise exception 'ISSUE_PROJECTION_VERSION_MUST_INCREMENT_BY_ONE'; end if;
 if new.projection_version<>old.projection_version+1 then raise exception 'ISSUE_PROJECTION_DOMAIN_VERSION_MUST_INCREMENT_BY_ONE'; end if;
 if new.operation_sequence<old.operation_sequence then raise exception 'ISSUE_PROJECTION_OPERATION_SEQUENCE_CANNOT_DECREASE'; end if;
 return new;
end $$;
drop trigger if exists trg_issue_projection_source_identity on issue_projection_states;
drop trigger if exists trg_issue_projection_aggregate_p3d on issue_projection_states;
create trigger trg_issue_projection_aggregate_p3d
 before update on issue_projection_states
 for each row execute function enforce_issue_projection_aggregate_p3d();

comment on column issue_projection_states.projection_aggregate_key is
 'Canonical key: tenantId|taskId|connectionId|projectMappingId|projectionPurpose.';
comment on column issue_projection_states.canonical_document_hash is
 'SHA-256 of the approved provider-neutral ExternalIssueDocument; no raw Task/Handoff payload.';
