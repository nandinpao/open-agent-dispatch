-- C0-B3 — External A2A F0 PeerProcessingAttestation.
-- Processing/storage-region assertions are governed facts. A recorded assertion is NOT trust evidence until explicitly verified.

create table if not exists a2a_peer_processing_attestations (
  tenant_id varchar(64) not null,
  attestation_id varchar(180) not null,
  peer_id varchar(160) not null,
  attestation_type varchar(48) not null,
  processing_region varchar(128) not null,
  storage_region varchar(128) not null,
  issuer varchar(255) not null,
  source varchar(128) not null,
  evidence_ref varchar(512),
  evidence_digest varchar(160),
  details_json jsonb not null default '{}'::jsonb,
  attestation_status varchar(24) not null default 'PENDING',
  valid_from timestamptz not null default now(),
  valid_until timestamptz,
  verified_at timestamptz,
  verified_by varchar(160),
  verification_note text,
  trust_evidence_id varchar(180),
  revoked_at timestamptz,
  revocation_reason text,
  created_by varchar(160) not null,
  created_at timestamptz not null default now(),
  primary key (tenant_id,attestation_id),
  constraint fk_a2a_processing_attestation_peer foreign key (tenant_id,peer_id)
    references a2a_peer_registrations(tenant_id,peer_id),
  constraint fk_a2a_processing_attestation_trust_evidence foreign key (tenant_id,trust_evidence_id)
    references a2a_peer_trust_evidence(tenant_id,evidence_id),
  constraint ck_a2a_processing_attestation_type check (attestation_type in (
    'SELF_ATTESTED','THIRD_PARTY_ATTESTED','PLATFORM_VERIFIED'
  )),
  constraint ck_a2a_processing_attestation_status check (attestation_status in ('PENDING','VERIFIED','REVOKED')),
  constraint ck_a2a_processing_attestation_regions check (
    length(trim(processing_region))>0 and length(trim(storage_region))>0
  ),
  constraint ck_a2a_processing_attestation_details check (jsonb_typeof(details_json)='object'),
  constraint ck_a2a_processing_attestation_validity check (valid_until is null or valid_until>valid_from),
  constraint ck_a2a_processing_attestation_lifecycle check (
    (attestation_status='PENDING'
      and verified_at is null and verified_by is null and trust_evidence_id is null
      and revoked_at is null and revocation_reason is null)
    or
    (attestation_status='VERIFIED'
      and verified_at is not null and verified_by is not null and trust_evidence_id is not null
      and revoked_at is null and revocation_reason is null)
    or
    (attestation_status='REVOKED'
      and revoked_at is not null and revocation_reason is not null
      and ((trust_evidence_id is null and verified_at is null and verified_by is null)
           or (trust_evidence_id is not null and verified_at is not null and verified_by is not null)))
  )
);

create unique index if not exists uq_a2a_processing_attestation_trust_evidence
  on a2a_peer_processing_attestations(tenant_id,trust_evidence_id)
  where trust_evidence_id is not null;
create unique index if not exists uq_a2a_processing_attestation_verified_peer
  on a2a_peer_processing_attestations(tenant_id,peer_id)
  where attestation_status='VERIFIED';
create index if not exists idx_a2a_processing_attestation_peer
  on a2a_peer_processing_attestations(tenant_id,peer_id,attestation_status,valid_until);
create index if not exists idx_a2a_processing_attestation_regions
  on a2a_peer_processing_attestations(tenant_id,processing_region,storage_region)
  where attestation_status='VERIFIED';

alter table a2a_peer_processing_attestations enable row level security;
drop policy if exists tenant_isolation on a2a_peer_processing_attestations;
create policy tenant_isolation on a2a_peer_processing_attestations
  using (tenant_id=iam_current_tenant_id())
  with check (tenant_id=iam_current_tenant_id());

-- Attestation facts are immutable. Lifecycle may only move PENDING -> VERIFIED/REVOKED or VERIFIED -> REVOKED.
create or replace function protect_a2a_peer_processing_attestation_c0b3() returns trigger language plpgsql as $$
begin
  if old.tenant_id is distinct from new.tenant_id
     or old.attestation_id is distinct from new.attestation_id
     or old.peer_id is distinct from new.peer_id
     or old.attestation_type is distinct from new.attestation_type
     or old.processing_region is distinct from new.processing_region
     or old.storage_region is distinct from new.storage_region
     or old.issuer is distinct from new.issuer
     or old.source is distinct from new.source
     or old.evidence_ref is distinct from new.evidence_ref
     or old.evidence_digest is distinct from new.evidence_digest
     or old.details_json is distinct from new.details_json
     or old.valid_from is distinct from new.valid_from
     or old.valid_until is distinct from new.valid_until
     or old.created_by is distinct from new.created_by
     or old.created_at is distinct from new.created_at then
    raise exception 'C0_B3_PROCESSING_ATTESTATION_FACT_IMMUTABLE' using errcode='55000';
  end if;

  if old.attestation_status='REVOKED' then
    raise exception 'C0_B3_PROCESSING_ATTESTATION_REVOCATION_IMMUTABLE' using errcode='55000';
  end if;
  if old.attestation_status='VERIFIED' and new.attestation_status<>'REVOKED' then
    raise exception 'C0_B3_PROCESSING_ATTESTATION_VERIFIED_IMMUTABLE' using errcode='55000';
  end if;
  if old.attestation_status='PENDING' and new.attestation_status not in ('VERIFIED','REVOKED') then
    raise exception 'C0_B3_PROCESSING_ATTESTATION_INVALID_TRANSITION' using errcode='55000';
  end if;
  if old.trust_evidence_id is not null and old.trust_evidence_id is distinct from new.trust_evidence_id then
    raise exception 'C0_B3_PROCESSING_ATTESTATION_EVIDENCE_LINK_IMMUTABLE' using errcode='55000';
  end if;
  return new;
end $$;

drop trigger if exists trg_a2a_peer_processing_attestation_c0b3 on a2a_peer_processing_attestations;
create trigger trg_a2a_peer_processing_attestation_c0b3
before update on a2a_peer_processing_attestations
for each row execute function protect_a2a_peer_processing_attestation_c0b3();

comment on table a2a_peer_processing_attestations is
'C0-B3 governed PeerProcessingAttestation facts. PENDING assertions do not satisfy TrustAssurancePolicy. VERIFIED rows produce a linked PROCESSING_ATTESTED PeerTrustEvidence fact; residency authorization remains a later hard-filter authority.';
comment on column a2a_peer_processing_attestations.processing_region is
'Peer processing-location assertion. It is distinct from A2APeerInterface endpointRegion and must not be inferred from URL/network location.';
comment on column a2a_peer_processing_attestations.storage_region is
'Peer storage-location assertion. It is distinct from endpointRegion and processingRegion.';
comment on column a2a_peer_processing_attestations.trust_evidence_id is
'Linked C0-B1 PROCESSING_ATTESTED evidence created only after explicit C0-B3 verification.';

insert into schema_contract_authority(contract_id,contract_family,authority_note,created_at,schema_version)
values('c0-b3-peer-processing-attestation-v1','C0_B3_EXTERNAL_A2A_F0_PROCESSING_ATTESTATION',
       'PENDING_ASSERTION_NOT_EVIDENCE; VERIFIED_ATTESTATION_CREATES_LINKED_PROCESSING_ATTESTED_EVIDENCE; REGIONS_DISTINCT_FROM_ENDPOINT_REGION; NO_RESIDENCY_AUTHORIZATION_YET',
       now(),'V214')
on conflict(contract_id) do nothing;
