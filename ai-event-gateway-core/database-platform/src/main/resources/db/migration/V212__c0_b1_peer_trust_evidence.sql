-- C0-B1 — External A2A F0 Peer Trust Evidence.
-- Trust is multidimensional evidence. Evidence is not an assurance grant and does not directly authorize runtime execution.

create table if not exists a2a_peer_trust_evidence (
  tenant_id varchar(64) not null,
  evidence_id varchar(180) not null,
  peer_id varchar(160) not null,
  interface_id varchar(180),
  subject_type varchar(24) not null,
  evidence_type varchar(64) not null,
  evidence_status varchar(24) not null default 'ACTIVE',
  issuer varchar(255),
  source varchar(128) not null,
  evidence_ref varchar(512),
  evidence_digest varchar(160),
  details_json jsonb not null default '{}'::jsonb,
  observed_at timestamptz not null default now(),
  valid_from timestamptz not null default now(),
  valid_until timestamptz,
  revoked_at timestamptz,
  revocation_reason text,
  created_by varchar(160) not null,
  created_at timestamptz not null default now(),
  primary key (tenant_id,evidence_id),
  constraint fk_a2a_peer_trust_evidence_peer foreign key (tenant_id,peer_id)
    references a2a_peer_registrations(tenant_id,peer_id),
  constraint fk_a2a_peer_trust_evidence_interface foreign key (tenant_id,interface_id)
    references a2a_peer_interfaces(tenant_id,interface_id),
  constraint ck_a2a_peer_trust_evidence_subject check (
    (subject_type='PEER' and interface_id is null)
    or (subject_type='INTERFACE' and interface_id is not null)
  ),
  constraint ck_a2a_peer_trust_evidence_type check (evidence_type in (
    'CARD_SIGNATURE_VERIFIED',
    'MTLS_CHANNEL_BOUND',
    'PRIVATE_NETWORK_ATTESTED',
    'MANUAL_APPROVAL',
    'CONFORMANCE_CERTIFIED',
    'PROCESSING_ATTESTED'
  )),
  constraint ck_a2a_peer_trust_evidence_status check (evidence_status in ('ACTIVE','REVOKED')),
  constraint ck_a2a_peer_trust_evidence_details check (jsonb_typeof(details_json)='object'),
  constraint ck_a2a_peer_trust_evidence_validity check (valid_until is null or valid_until>valid_from),
  constraint ck_a2a_peer_trust_evidence_revocation check (
    (evidence_status='ACTIVE' and revoked_at is null and revocation_reason is null)
    or (evidence_status='REVOKED' and revoked_at is not null and revocation_reason is not null)
  )
);

create index if not exists idx_a2a_peer_trust_evidence_peer
  on a2a_peer_trust_evidence(tenant_id,peer_id,evidence_type,observed_at desc);
create index if not exists idx_a2a_peer_trust_evidence_interface
  on a2a_peer_trust_evidence(tenant_id,interface_id,evidence_type,observed_at desc)
  where interface_id is not null;
create index if not exists idx_a2a_peer_trust_evidence_effective
  on a2a_peer_trust_evidence(tenant_id,peer_id,evidence_status,valid_until)
  where evidence_status='ACTIVE';

alter table a2a_peer_trust_evidence enable row level security;
drop policy if exists tenant_isolation on a2a_peer_trust_evidence;
create policy tenant_isolation on a2a_peer_trust_evidence
  using (tenant_id=iam_current_tenant_id())
  with check (tenant_id=iam_current_tenant_id());

-- Evidence facts are immutable. Only an ACTIVE -> REVOKED lifecycle transition is allowed.
create or replace function protect_a2a_peer_trust_evidence_c0b1() returns trigger language plpgsql as $$
begin
  if old.tenant_id is distinct from new.tenant_id
     or old.evidence_id is distinct from new.evidence_id
     or old.peer_id is distinct from new.peer_id
     or old.interface_id is distinct from new.interface_id
     or old.subject_type is distinct from new.subject_type
     or old.evidence_type is distinct from new.evidence_type
     or old.issuer is distinct from new.issuer
     or old.source is distinct from new.source
     or old.evidence_ref is distinct from new.evidence_ref
     or old.evidence_digest is distinct from new.evidence_digest
     or old.details_json is distinct from new.details_json
     or old.observed_at is distinct from new.observed_at
     or old.valid_from is distinct from new.valid_from
     or old.valid_until is distinct from new.valid_until
     or old.created_by is distinct from new.created_by
     or old.created_at is distinct from new.created_at then
    raise exception 'C0_B1_PEER_TRUST_EVIDENCE_FACT_IMMUTABLE' using errcode='55000';
  end if;
  if old.evidence_status='REVOKED' and new.evidence_status<>'REVOKED' then
    raise exception 'C0_B1_PEER_TRUST_EVIDENCE_REVOCATION_IMMUTABLE' using errcode='55000';
  end if;
  return new;
end $$;

drop trigger if exists trg_a2a_peer_trust_evidence_c0b1 on a2a_peer_trust_evidence;
create trigger trg_a2a_peer_trust_evidence_c0b1
before update on a2a_peer_trust_evidence
for each row execute function protect_a2a_peer_trust_evidence_c0b1();

comment on table a2a_peer_trust_evidence is
'C0-B1 multidimensional external-A2A trust evidence. Rows are facts/evidence inputs only; assurance grants are computed by TrustAssurancePolicy in a later authority layer.';
comment on column a2a_peer_trust_evidence.evidence_type is
'Independent evidence dimensions; no natural trust ladder or implicit ordering exists between evidence types.';

-- Do NOT backfill legacy trust_status into evidence. A historical linear trust result is not proof of a concrete evidence fact.
comment on column a2a_peer_registrations.trust_status is
'Legacy compatibility projection retained during C0-B1. Not PeerTrustEvidence and not the future TrustAssurancePolicy grant authority.';
comment on column a2a_peer_interfaces.trust_status is
'Legacy compatibility projection retained during C0-B1. Not PeerTrustEvidence and not the future TrustAssurancePolicy grant authority.';

insert into schema_contract_authority(contract_id,contract_family,authority_note,created_at,schema_version)
values('c0-b1-peer-trust-evidence-v1','C0_B1_EXTERNAL_A2A_F0_TRUST_EVIDENCE',
       'MULTIDIMENSIONAL_EVIDENCE_ONLY; NO_LINEAR_TRUST_ORDER; NO_ASSURANCE_GRANT; NO_RUNTIME_AUTHORIZATION; LEGACY_TRUST_STATUS_NOT_BACKFILLED',
       now(),'V212')
on conflict(contract_id) do nothing;
