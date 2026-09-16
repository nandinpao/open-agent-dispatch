-- Phase 2E: immutable Handoff aggregate reliability, release evidence and reconciliation.

alter table handoff_context_snapshots add column if not exists aggregate_id varchar(128);
alter table handoff_context_snapshots add column if not exists schema_version integer not null default 1;
alter table handoff_context_snapshots add column if not exists source_agent_id varchar(128);
alter table handoff_context_snapshots add column if not exists target_agent_id varchar(128);
alter table handoff_context_snapshots add column if not exists target_domain_id varchar(128);
alter table handoff_context_snapshots add column if not exists target_binding_hash varchar(128);
alter table handoff_context_snapshots add column if not exists policy_version bigint;
alter table handoff_context_snapshots add column if not exists approval_evidence_hash varchar(128);
alter table handoff_context_snapshots add column if not exists release_status varchar(32);
alter table handoff_context_snapshots add column if not exists release_evidence_id varchar(128);
alter table handoff_context_snapshots add column if not exists released_at timestamptz;
alter table handoff_context_snapshots add column if not exists last_release_error_code varchar(128);
alter table handoff_context_snapshots add column if not exists reconciliation_classification varchar(64);
alter table handoff_context_snapshots add column if not exists next_reconcile_at timestamptz;
alter table handoff_context_snapshots add column if not exists reconciliation_count integer not null default 0;
alter table handoff_context_snapshots add column if not exists row_version bigint not null default 1;

update handoff_context_snapshots snapshot
set aggregate_id=coalesce(snapshot.aggregate_id,'hagg-legacy-'||md5(snapshot.tenant_id||':'||snapshot.source_task_id||':'||snapshot.target_task_id||':'||snapshot.context_policy_id)),
    source_agent_id=coalesce(snapshot.source_agent_id,source_task.requesting_agent_id,'UNASSIGNED'),
    target_domain_id=coalesce(snapshot.target_domain_id,target_task.executor_domain_id,'UNASSIGNED'),
    policy_version=coalesce(snapshot.policy_version,policy.version,1),
    approval_evidence_hash=case when snapshot.status='APPROVED' then coalesce(snapshot.approval_evidence_hash,md5('legacy-approval:'||snapshot.snapshot_id||':'||coalesce(snapshot.approved_by,'SYSTEM'))) else snapshot.approval_evidence_hash end,
    release_status=coalesce(snapshot.release_status,case when snapshot.status='PENDING_APPROVAL' then 'WAITING_APPROVAL' when snapshot.status='APPROVED' and target_task.status='WAITING_CONTEXT' then 'READY' when snapshot.status='APPROVED' then 'RELEASED' else 'WAIT_HUMAN' end),
    released_at=case when snapshot.status='APPROVED' and target_task.status<>'WAITING_CONTEXT' then coalesce(snapshot.released_at,snapshot.approved_at,snapshot.created_at) else snapshot.released_at end,
    reconciliation_classification=coalesce(snapshot.reconciliation_classification,'NONE'),
    row_version=greatest(coalesce(snapshot.row_version,1),1)
from tasks source_task,tasks target_task,handoff_context_policies policy
where source_task.tenant_id=snapshot.tenant_id and source_task.task_id=snapshot.source_task_id
  and target_task.tenant_id=snapshot.tenant_id and target_task.task_id=snapshot.target_task_id
  and policy.tenant_id=snapshot.tenant_id and policy.policy_id=snapshot.context_policy_id;

-- Fail-safe fallback for legacy rows whose referenced Task or Policy was removed before upgrade.
update handoff_context_snapshots
set aggregate_id=coalesce(aggregate_id,'hagg-legacy-'||md5(tenant_id||':'||source_task_id||':'||target_task_id||':'||context_policy_id)),
    source_agent_id=coalesce(source_agent_id,'UNASSIGNED'),
    target_domain_id=coalesce(target_domain_id,'UNASSIGNED'),
    policy_version=coalesce(policy_version,1),
    approval_evidence_hash=case when status='APPROVED' then coalesce(approval_evidence_hash,md5('legacy-approval:'||snapshot_id||':'||coalesce(approved_by,'SYSTEM'))) else approval_evidence_hash end,
    release_status=coalesce(release_status,case when status='PENDING_APPROVAL' then 'WAITING_APPROVAL' when status='APPROVED' then 'READY' else 'WAIT_HUMAN' end),
    reconciliation_classification=coalesce(reconciliation_classification,'NONE'),
    row_version=greatest(coalesce(row_version,1),1)
where aggregate_id is null or source_agent_id is null or target_domain_id is null
   or policy_version is null or release_status is null or reconciliation_classification is null;

-- Retain only the newest active aggregate version before the partial unique index is introduced.
with ranked as (
  select tenant_id,snapshot_id,row_number() over(partition by tenant_id,aggregate_id order by snapshot_version desc,created_at desc) as rn
  from handoff_context_snapshots where status in('PENDING_APPROVAL','APPROVED')
)
update handoff_context_snapshots snapshot
set status='SUPERSEDED',release_status='WAIT_HUMAN',last_release_error_code='PHASE2E_LEGACY_DUPLICATE_ACTIVE_AGGREGATE',row_version=row_version+1
from ranked where snapshot.tenant_id=ranked.tenant_id and snapshot.snapshot_id=ranked.snapshot_id and ranked.rn>1;

alter table handoff_context_snapshots alter column aggregate_id set not null;
alter table handoff_context_snapshots alter column policy_version set not null;
alter table handoff_context_snapshots alter column source_agent_id set not null;
alter table handoff_context_snapshots alter column target_domain_id set not null;
alter table handoff_context_snapshots alter column release_status set not null;
alter table handoff_context_snapshots alter column reconciliation_classification set not null;

create unique index if not exists uq_handoff_active_aggregate_p2e
  on handoff_context_snapshots(tenant_id,aggregate_id)
  where status in('PENDING_APPROVAL','APPROVED');
create index if not exists idx_handoff_release_due_p2e
  on handoff_context_snapshots(release_status,next_reconcile_at,created_at)
  where status='APPROVED' and release_status in('READY','RELEASING','FAILED_RETRYABLE');

create table if not exists handoff_release_evidence (
  tenant_id varchar(64) not null,
  evidence_id varchar(128) not null,
  snapshot_id varchar(128) not null,
  evidence_type varchar(64) not null,
  release_status varchar(32) not null,
  classification varchar(64) not null default 'NONE',
  dispatch_evidence_reference varchar(512),
  reason_code varchar(128),
  attempt_no integer not null,
  actor_type varchar(32) not null,
  actor_id varchar(128) not null,
  correlation_id varchar(128),
  occurred_at timestamptz not null default now(),
  primary key(tenant_id,evidence_id),
  unique(tenant_id,snapshot_id,evidence_type,attempt_no),
  constraint fk_handoff_release_snapshot_p2e foreign key(tenant_id,snapshot_id)
    references handoff_context_snapshots(tenant_id,snapshot_id)
);
create index if not exists idx_handoff_release_evidence_snapshot_p2e
  on handoff_release_evidence(tenant_id,snapshot_id,occurred_at desc);

alter table handoff_context_snapshots add constraint ck_handoff_schema_version_p2e check(schema_version in(1,2));
alter table handoff_context_snapshots add constraint ck_handoff_release_status_p2e check(release_status in('WAITING_APPROVAL','READY','RELEASING','RELEASED','FAILED_RETRYABLE','WAIT_HUMAN'));
alter table handoff_context_snapshots add constraint ck_handoff_reconciliation_p2e check(reconciliation_classification in('NONE','RELEASE_EVENT_MISSING','APPROVAL_RELEASE_GAP','SNAPSHOT_EXPIRED','TARGET_BINDING_CHANGED','HASH_CONFLICT','RELEASE_PERSISTENCE_UNCERTAIN','RETRY_EXHAUSTED'));
alter table handoff_context_snapshots add constraint ck_handoff_release_approval_p2e check(status<>'APPROVED' or approval_evidence_hash is not null);
alter table handoff_context_snapshots add constraint ck_handoff_release_complete_p2e check(release_status<>'RELEASED' or released_at is not null);
alter table handoff_release_evidence add constraint ck_handoff_release_evidence_type_p2e check(evidence_type in('SNAPSHOT_READY','RELEASE_REQUESTED','RELEASED','RELEASE_FAILED','RECONCILED','EXPIRED','BINDING_CHANGED','HASH_CONFLICT'));
alter table handoff_release_evidence add constraint ck_handoff_release_evidence_status_p2e check(release_status in('WAITING_APPROVAL','READY','RELEASING','RELEASED','FAILED_RETRYABLE','WAIT_HUMAN'));

create or replace function phase2e_protect_immutable_snapshot() returns trigger language plpgsql as $$
begin
  if tg_op='DELETE' then raise exception 'HANDOFF_CONTEXT_SNAPSHOT_IMMUTABLE'; end if;
  if new.tenant_id is distinct from old.tenant_id or new.snapshot_id is distinct from old.snapshot_id
     or new.aggregate_id is distinct from old.aggregate_id or new.schema_version is distinct from old.schema_version
     or new.root_task_id is distinct from old.root_task_id or new.source_task_id is distinct from old.source_task_id
     or new.target_task_id is distinct from old.target_task_id or new.source_agent_id is distinct from old.source_agent_id
     or new.target_agent_id is distinct from old.target_agent_id or new.target_domain_id is distinct from old.target_domain_id
     or new.target_binding_hash is distinct from old.target_binding_hash or new.context_policy_id is distinct from old.context_policy_id
     or new.policy_version is distinct from old.policy_version or new.snapshot_version is distinct from old.snapshot_version
     or new.summary is distinct from old.summary or new.structured_context_json is distinct from old.structured_context_json
     or new.allowed_comment_refs_json is distinct from old.allowed_comment_refs_json
     or new.attachment_metadata_json is distinct from old.attachment_metadata_json
     or new.redacted_field_paths_json is distinct from old.redacted_field_paths_json
     or new.omitted_content_reasons_json is distinct from old.omitted_content_reasons_json
     or new.sensitivity_level is distinct from old.sensitivity_level or new.content_hash is distinct from old.content_hash
     or new.source_observed_at is distinct from old.source_observed_at or new.created_at is distinct from old.created_at
     or new.created_by_type is distinct from old.created_by_type or new.created_by_id is distinct from old.created_by_id
     or new.expires_at is distinct from old.expires_at or new.correlation_id is distinct from old.correlation_id then
    raise exception 'HANDOFF_CONTEXT_SNAPSHOT_CONTENT_IMMUTABLE';
  end if;
  if new.row_version<>old.row_version+1 then raise exception 'HANDOFF_CONTEXT_ROW_VERSION_REQUIRED'; end if;
  if old.status='PENDING_APPROVAL' and new.status not in('PENDING_APPROVAL','APPROVED','REJECTED') then raise exception 'INVALID_HANDOFF_SNAPSHOT_STATUS_TRANSITION'; end if;
  if old.status='APPROVED' and new.status not in('APPROVED','SUPERSEDED','EXPIRED') then raise exception 'INVALID_HANDOFF_SNAPSHOT_STATUS_TRANSITION'; end if;
  if old.status in('SUPERSEDED','EXPIRED','REJECTED') and new.status<>old.status then raise exception 'INVALID_HANDOFF_SNAPSHOT_STATUS_TRANSITION'; end if;
  if old.release_status='WAITING_APPROVAL' and new.release_status not in('WAITING_APPROVAL','READY','WAIT_HUMAN') then raise exception 'INVALID_HANDOFF_RELEASE_STATUS_TRANSITION'; end if;
  if old.release_status='READY' and new.release_status not in('READY','RELEASING','WAIT_HUMAN') then raise exception 'INVALID_HANDOFF_RELEASE_STATUS_TRANSITION'; end if;
  if old.release_status='RELEASING' and new.release_status not in('RELEASING','RELEASED','FAILED_RETRYABLE','WAIT_HUMAN') then raise exception 'INVALID_HANDOFF_RELEASE_STATUS_TRANSITION'; end if;
  if old.release_status='FAILED_RETRYABLE' and new.release_status not in('FAILED_RETRYABLE','READY','RELEASING','WAIT_HUMAN') then raise exception 'INVALID_HANDOFF_RELEASE_STATUS_TRANSITION'; end if;
  if old.release_status in('RELEASED','WAIT_HUMAN') and new.release_status<>old.release_status then raise exception 'INVALID_HANDOFF_RELEASE_STATUS_TRANSITION'; end if;
  return new;
end $$;
drop trigger if exists trg_phase0f_snapshot_immutable on handoff_context_snapshots;
drop trigger if exists trg_phase2e_snapshot_immutable on handoff_context_snapshots;
create trigger trg_phase2e_snapshot_immutable before update or delete on handoff_context_snapshots for each row execute function phase2e_protect_immutable_snapshot();

create or replace function phase2e_protect_release_evidence() returns trigger language plpgsql as $$
begin raise exception 'HANDOFF_RELEASE_EVIDENCE_IMMUTABLE'; end $$;
drop trigger if exists trg_phase2e_release_evidence_immutable on handoff_release_evidence;
create trigger trg_phase2e_release_evidence_immutable before update or delete on handoff_release_evidence for each row execute function phase2e_protect_release_evidence();
