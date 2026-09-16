-- A0-R5.5 / Stage 6 — Contract Hardening Gate
-- Contract authority: OpenDispatch MRS v5.2.1 A0-C FROZEN + Stage 6 hardening corrections.
-- No RoutingDecision / ExecutionAssignment authority cutover is granted by this migration.

-- -----------------------------------------------------------------------------
-- 1. A2A push/event identity: content/payload hash is integrity only, never event identity.
-- -----------------------------------------------------------------------------
alter table a2a_push_inbox
  add column if not exists delivery_identity varchar(255),
  add column if not exists remote_event_identity varchar(255);

alter table a2a_push_inbox drop constraint if exists a2a_push_inbox_tenant_id_tracking_id_payload_hash_key;
drop index if exists uq_a2a_push_inbox_payload_hash;
create unique index if not exists uq_a2a_push_delivery_identity
  on a2a_push_inbox(tenant_id,tracking_id,delivery_identity)
  where delivery_identity is not null;

alter table a2a_remote_event_journal
  add column if not exists local_remote_sequence bigint,
  add column if not exists stream_id varchar(255),
  add column if not exists is_authoritative boolean not null default true;

alter table a2a_remote_event_journal drop constraint if exists a2a_remote_event_journal_tenant_id_tracking_id_dedup_key_key;
alter table a2a_remote_event_journal alter column dedup_key drop not null;

-- Temporarily remove the historical immutability trigger only for this schema backfill.
drop trigger if exists trg_a2a_remote_event_journal_immutable on a2a_remote_event_journal;

with ranked as (
  select tenant_id,journal_event_id,
         row_number() over(partition by tenant_id,tracking_id order by observed_at,journal_event_id) as seq
    from a2a_remote_event_journal
)
update a2a_remote_event_journal j
   set local_remote_sequence=r.seq
  from ranked r
 where j.tenant_id=r.tenant_id and j.journal_event_id=r.journal_event_id and j.local_remote_sequence is null;

alter table a2a_remote_event_journal alter column local_remote_sequence set not null;

create trigger trg_a2a_remote_event_journal_immutable before update or delete on a2a_remote_event_journal
for each row execute function prevent_a2a_remote_event_journal_mutation();
create unique index if not exists uq_a2a_remote_event_local_sequence
  on a2a_remote_event_journal(tenant_id,tracking_id,local_remote_sequence);
create unique index if not exists uq_a2a_remote_event_remote_identity
  on a2a_remote_event_journal(tenant_id,tracking_id,dedup_key)
  where dedup_key is not null;

comment on column a2a_push_inbox.payload_hash is 'Integrity digest only. MUST NOT be used as push/event identity.';
comment on column a2a_remote_event_journal.payload_hash is 'Integrity digest only. MUST NOT be used as append-event identity.';
comment on column a2a_remote_event_journal.local_remote_sequence is 'OpenDispatch-local ordering assigned by the authoritative tracking owner.';

-- -----------------------------------------------------------------------------
-- 2. Agent Card trust semantics: successful fetch/hash means integrity recorded, not identity/trust verified.
-- -----------------------------------------------------------------------------
update a2a_peer_registrations set trust_status='INTEGRITY_RECORDED' where trust_status='CARD_VERIFIED';
update a2a_peer_interfaces set trust_status='INTEGRITY_RECORDED' where trust_status='CARD_VERIFIED';

alter table a2a_peer_registrations drop constraint if exists a2a_peer_trust_status_check;
alter table a2a_peer_registrations add constraint a2a_peer_trust_status_check
  check (trust_status in ('PROPOSED','INTEGRITY_RECORDED','MANUAL_TRUSTED','IDENTITY_VERIFIED','TRUST_APPROVED','REVOKED'));

alter table a2a_peer_interfaces drop constraint if exists a2a_interface_trust_check;
alter table a2a_peer_interfaces add constraint a2a_interface_trust_check
  check (trust_status in ('PROPOSED','INTEGRITY_RECORDED','MANUAL_TRUSTED','IDENTITY_VERIFIED','TRUST_APPROVED','REVOKED'));

-- Peers that had only the old CARD_VERIFIED semantics are not execution-authoritative after hardening.
update a2a_peer_registrations
   set status='SUSPENDED',updated_at=now()
 where status='ACTIVE' and trust_status='INTEGRITY_RECORDED';
update a2a_peer_provider_links l
   set status='SUSPENDED',updated_at=now()
 where status='ACTIVE' and exists (
   select 1 from a2a_peer_registrations p
    where p.tenant_id=l.tenant_id and p.peer_id=l.peer_id and p.status='SUSPENDED' and p.trust_status='INTEGRITY_RECORDED'
 );

-- -----------------------------------------------------------------------------
-- 3. Source Registration ownership is inherited from SourceSystem master data.
--    Client-supplied registration ownership is compatibility input only and never authoritative.
-- -----------------------------------------------------------------------------
update workload_source_registrations r
   set owner_department_id=s.owner_department_id,
       owner_group_id=s.owner_group_id,
       version=r.version+1,
       updated_at=now()
  from source_systems s
 where s.tenant_id=r.tenant_id and s.source_system_id=r.source_system_id
   and (r.owner_department_id is distinct from s.owner_department_id or r.owner_group_id is distinct from s.owner_group_id);

create or replace function a0r55_enforce_registration_inherited_ownership()
returns trigger language plpgsql as $$
declare dep varchar(128); grp varchar(128);
begin
  select owner_department_id,owner_group_id into dep,grp
    from source_systems
   where tenant_id=new.tenant_id and source_system_id=new.source_system_id;
  if not found then raise exception 'SOURCE_SYSTEM_NOT_FOUND_FOR_REGISTRATION'; end if;
  new.owner_department_id=dep;
  new.owner_group_id=grp;
  return new;
end $$;

drop trigger if exists trg_a0r55_registration_inherited_ownership on workload_source_registrations;
create trigger trg_a0r55_registration_inherited_ownership
before insert or update of source_system_id,owner_department_id,owner_group_id on workload_source_registrations
for each row execute function a0r55_enforce_registration_inherited_ownership();

create or replace function a0r55_sync_registration_ownership_from_source()
returns trigger language plpgsql as $$
begin
  if old.owner_department_id is not distinct from new.owner_department_id
     and old.owner_group_id is not distinct from new.owner_group_id then return new; end if;
  update workload_source_registrations
     set owner_department_id=new.owner_department_id,owner_group_id=new.owner_group_id,version=version+1,updated_at=now()
   where tenant_id=new.tenant_id and source_system_id=new.source_system_id;
  return new;
end $$;

drop trigger if exists trg_a0r55_source_registration_ownership_sync on source_systems;
create trigger trg_a0r55_source_registration_ownership_sync
after update of owner_department_id,owner_group_id on source_systems
for each row execute function a0r55_sync_registration_ownership_from_source();

-- -----------------------------------------------------------------------------
-- 4. Idempotency canonicalization v2 excludes observability-only correlationId from semantic digest.
--    Historical V1 records remain immutable/replayable with their original version.
-- -----------------------------------------------------------------------------
alter table intake_records alter column canonicalization_version set default 'A0R1_JSON_V2';
alter table ingestion_idempotency_records alter column canonicalization_version set default 'A0R1_JSON_V2';

insert into schema_contract_authority(contract_id,contract_family,authority_note,created_at,schema_version)
values(
  'a0-r5-5-contract-hardening-v1',
  'A0_R5_5_CONTRACT_HARDENING',
  'PAYLOAD_HASH_IS_INTEGRITY_NOT_IDENTITY; AGENT_CARD_FETCH_IS_INTEGRITY_NOT_TRUST; SOURCE_REGISTRATION_OWNERSHIP_INHERITS_SOURCE_SYSTEM; IDEMPOTENCY_CANONICALIZATION_V2_EXCLUDES_CORRELATION_ID; ROUTING_AND_ASSIGNMENT_AUTHORITY_UNCHANGED',
  now(),
  'V204'
)
on conflict(contract_id) do nothing;
