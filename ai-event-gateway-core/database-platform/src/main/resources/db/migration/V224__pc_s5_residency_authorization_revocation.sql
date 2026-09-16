-- PC-S5 — Residency Authorization & Cross-Authority Revocation Closure.
-- Existing region facts remain authoritative at their original owners:
--   endpoint_region -> A2APeerInterface
--   processing/storage_region -> VERIFIED PeerProcessingAttestation
-- This migration adds only policy/decision authority and revocation propagation.

create table if not exists a2a_residency_policies (
  tenant_id varchar(64) not null,
  policy_id varchar(180) not null,
  display_name varchar(255) not null,
  description text,
  allowed_endpoint_regions_json jsonb not null default '[]'::jsonb,
  allowed_processing_regions_json jsonb not null default '[]'::jsonb,
  allowed_storage_regions_json jsonb not null default '[]'::jsonb,
  unknown_fact_policy varchar(16) not null default 'DENY',
  emergency_kill_switch boolean not null default false,
  policy_status varchar(24) not null default 'DRAFT',
  policy_version bigint not null default 1,
  created_by varchar(160) not null,
  activated_at timestamptz,
  retired_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key(tenant_id,policy_id),
  constraint ck_a2a_residency_endpoint_regions_pc_s5 check(a2a_jsonb_string_array(allowed_endpoint_regions_json)),
  constraint ck_a2a_residency_processing_regions_pc_s5 check(a2a_jsonb_string_array(allowed_processing_regions_json)),
  constraint ck_a2a_residency_storage_regions_pc_s5 check(a2a_jsonb_string_array(allowed_storage_regions_json)),
  constraint ck_a2a_residency_unknown_pc_s5 check(unknown_fact_policy='DENY'),
  constraint ck_a2a_residency_status_pc_s5 check(policy_status in ('DRAFT','ACTIVE','RETIRED')),
  constraint ck_a2a_residency_version_pc_s5 check(policy_version>0)
);
create unique index if not exists uq_a2a_residency_active_policy_pc_s5
  on a2a_residency_policies(tenant_id) where policy_status='ACTIVE';

create or replace function protect_a2a_residency_policy_pc_s5() returns trigger language plpgsql as $$
begin
  if old.tenant_id is distinct from new.tenant_id or old.policy_id is distinct from new.policy_id
     or old.policy_version is distinct from new.policy_version or old.created_by is distinct from new.created_by
     or old.created_at is distinct from new.created_at then
    raise exception 'PC_S5_RESIDENCY_POLICY_IDENTITY_IMMUTABLE' using errcode='55000';
  end if;
  if old.policy_status<>'DRAFT' and (
       old.display_name is distinct from new.display_name or old.description is distinct from new.description
       or old.allowed_endpoint_regions_json is distinct from new.allowed_endpoint_regions_json
       or old.allowed_processing_regions_json is distinct from new.allowed_processing_regions_json
       or old.allowed_storage_regions_json is distinct from new.allowed_storage_regions_json
       or old.unknown_fact_policy is distinct from new.unknown_fact_policy) then
    raise exception 'PC_S5_ACTIVE_RESIDENCY_POLICY_CONTENT_IMMUTABLE' using errcode='55000';
  end if;
  if old.policy_status='ACTIVE' and new.policy_status not in ('ACTIVE','RETIRED') then
    raise exception 'PC_S5_ACTIVE_RESIDENCY_POLICY_CANNOT_RETURN_TO_DRAFT' using errcode='55000';
  end if;
  if old.policy_status='RETIRED' and new.policy_status<>'RETIRED' then
    raise exception 'PC_S5_RETIRED_RESIDENCY_POLICY_IMMUTABLE' using errcode='55000';
  end if;
  return new;
end $$;
drop trigger if exists trg_a2a_residency_policy_protect_pc_s5 on a2a_residency_policies;
create trigger trg_a2a_residency_policy_protect_pc_s5 before update on a2a_residency_policies
for each row execute function protect_a2a_residency_policy_pc_s5();

create table if not exists a2a_residency_authorization_decisions (
  tenant_id varchar(64) not null,
  decision_id varchar(180) not null,
  peer_id varchar(160) not null,
  interface_id varchar(180) not null,
  policy_id varchar(180),
  policy_version bigint,
  processing_attestation_id varchar(180),
  endpoint_region varchar(128),
  processing_region varchar(128),
  storage_region varchar(128),
  decision varchar(16) not null,
  reason_codes_json jsonb not null default '[]'::jsonb,
  decided_at timestamptz not null default now(),
  primary key(tenant_id,decision_id),
  constraint ck_a2a_residency_decision_pc_s5 check(decision in ('ALLOWED','DENIED','UNKNOWN')),
  constraint ck_a2a_residency_reason_array_pc_s5 check(jsonb_typeof(reason_codes_json)='array')
);
create index if not exists idx_a2a_residency_decision_interface_pc_s5
  on a2a_residency_authorization_decisions(tenant_id,interface_id,decided_at desc);

alter table a2a_remote_read_executions add column if not exists residency_decision_id varchar(180);
create index if not exists idx_a2a_execution_residency_pc_s5
  on a2a_remote_read_executions(tenant_id,residency_decision_id);

create table if not exists a2a_revocation_propagation_events (
  tenant_id varchar(64) not null,
  event_id varchar(180) not null,
  interface_id varchar(180) not null,
  reason_code varchar(160) not null,
  provider_links_suspended integer not null default 0,
  binding_envelopes_revoked integer not null default 0,
  runtime_envelopes_revoked integer not null default 0,
  execution_leases_revoked integer not null default 0,
  remote_executions_failed integer not null default 0,
  occurred_at timestamptz not null default now(),
  primary key(tenant_id,event_id)
);

alter table a2a_residency_policies enable row level security;
alter table a2a_residency_authorization_decisions enable row level security;
alter table a2a_revocation_propagation_events enable row level security;
do $$ declare t text; begin
  foreach t in array array['a2a_residency_policies','a2a_residency_authorization_decisions','a2a_revocation_propagation_events'] loop
    execute format('drop policy if exists tenant_isolation on %I',t);
    execute format('create policy tenant_isolation on %I using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id())',t);
  end loop;
end $$;

create or replace function prevent_a2a_residency_decision_mutation_pc_s5() returns trigger language plpgsql as $$
begin raise exception 'PC_S5_RESIDENCY_DECISION_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_a2a_residency_decision_immutable_pc_s5 on a2a_residency_authorization_decisions;
create trigger trg_a2a_residency_decision_immutable_pc_s5 before update or delete on a2a_residency_authorization_decisions
for each row execute function prevent_a2a_residency_decision_mutation_pc_s5();

drop trigger if exists trg_a2a_revocation_event_immutable_pc_s5 on a2a_revocation_propagation_events;
create trigger trg_a2a_revocation_event_immutable_pc_s5 before update or delete on a2a_revocation_propagation_events
for each row execute function prevent_a2a_residency_decision_mutation_pc_s5();

-- Current decision is intentionally fail-closed. Empty allowlists are NOT wildcards.
create or replace function a2a_interface_current_residency_decision(p_tenant text,p_interface text)
returns text language sql stable as $$
  with x as (
    select i.peer_id,i.endpoint_region,p.policy_id,p.emergency_kill_switch,
           p.allowed_endpoint_regions_json,p.allowed_processing_regions_json,p.allowed_storage_regions_json,
           a.attestation_id,a.processing_region,a.storage_region
      from a2a_peer_interfaces i
      left join a2a_residency_policies p on p.tenant_id=i.tenant_id and p.policy_status='ACTIVE'
      left join lateral (
        select a.attestation_id,a.processing_region,a.storage_region
          from a2a_peer_processing_attestations a
         where a.tenant_id=i.tenant_id and a.peer_id=i.peer_id and a.attestation_status='VERIFIED'
           and a.valid_from<=now() and (a.valid_until is null or a.valid_until>now())
         order by a.verified_at desc nulls last,a.attestation_id
         limit 1
      ) a on true
     where i.tenant_id=p_tenant and i.interface_id=p_interface
  )
  select case
    when not exists(select 1 from x) then 'UNKNOWN'
    when exists(select 1 from x where policy_id is null or endpoint_region is null or btrim(endpoint_region)='' or attestation_id is null) then 'UNKNOWN'
    when exists(select 1 from x where emergency_kill_switch) then 'DENIED'
    when exists(select 1 from x where allowed_endpoint_regions_json ? endpoint_region
                                  and allowed_processing_regions_json ? processing_region
                                  and allowed_storage_regions_json ? storage_region) then 'ALLOWED'
    else 'DENIED' end
$$;

create or replace function a2a_interface_current_residency_allowed(p_tenant text,p_interface text)
returns boolean language sql stable as $$
  select a2a_interface_current_residency_decision(p_tenant,p_interface)='ALLOWED'
$$;

-- Extend, do not replace, the C0-B4/B5/B7/B8 current interface gate.
create or replace function a2a_interface_current_contract_eligible(p_tenant text,p_interface text)
returns boolean language sql stable as $$
  select exists(
    select 1
      from a2a_peer_interfaces i
     where i.tenant_id=p_tenant and i.interface_id=p_interface
       and i.status='APPROVED'
       and i.protocol_binding='HTTP+JSON'
       and a2a_version_policy_allowed(i.tenant_id,i.peer_id,i.interface_id,i.protocol_version)
       and nullif(btrim(i.endpoint_region),'') is not null
       and a2a_interface_current_outbound_policy_id(i.tenant_id,i.interface_id) is not null
       and a2a_interface_current_credential_binding_id(i.tenant_id,i.interface_id) is not null
       and i.health_status='HEALTHY'
       and a2a_interface_conformance_current_pass(i.tenant_id,i.interface_id)
       and i.circuit_state='CLOSED'
       and i.supported_extensions_json @> i.required_extensions_json
       and a2a_interface_current_residency_allowed(i.tenant_id,i.interface_id)
  )
$$;

-- One propagation primitive owns the fail-closed effect of a material authority loss.
create or replace function pc_s5_revoke_a2a_authority(p_tenant text,p_interface text,p_reason text)
returns void language plpgsql as $$
declare n_links int:=0; n_binding int:=0; n_runtime int:=0; n_leases int:=0; n_remote int:=0;
begin
  update a2a_peer_provider_links set status='SUSPENDED',updated_at=now()
   where tenant_id=p_tenant and interface_id=p_interface and status='ACTIVE';
  get diagnostics n_links=row_count;

  update binding_authorization_envelopes e
     set status='REVOKED',revocation_version=revocation_version+1,authorization_epoch=authorization_epoch+1,
         revoked_at=now(),revocation_reason=p_reason
   where e.tenant_id=p_tenant and e.status='ACTIVE'
     and exists(
       select 1 from capability_bindings b join a2a_peer_provider_links l
         on l.tenant_id=b.tenant_id and l.provider_id=b.provider_id
        where b.tenant_id=e.tenant_id and l.interface_id=p_interface
          and jsonb_exists(e.admitted_binding_ids_json,b.binding_id));
  get diagnostics n_binding=row_count;

  update capability_runtime_authorization_envelopes r
     set status='REVOKED',revocation_version=revocation_version+1,authorization_epoch=authorization_epoch+1,
         revoked_at=now(),revocation_reason=p_reason,updated_at=now()
   where r.tenant_id=p_tenant and r.status='ACTIVE'
     and exists(select 1 from capability_bindings b join a2a_peer_provider_links l
       on l.tenant_id=b.tenant_id and l.provider_id=b.provider_id
       where b.tenant_id=r.tenant_id and b.binding_id=r.binding_id and l.interface_id=p_interface);
  get diagnostics n_runtime=row_count;

  update execution_leases_v206 l
     set status='REVOKED',released_at=now(),release_reason=p_reason,version=version+1
   where l.tenant_id=p_tenant and l.status='ACTIVE'
     and exists(select 1 from execution_assignments_v206 a where a.tenant_id=l.tenant_id and a.assignment_id=l.assignment_id and a.selected_peer_interface_id=p_interface);
  get diagnostics n_leases=row_count;

  update a2a_remote_tracking_leases t set status='FAILED',owner_instance_id=null,lease_token=null,lease_until=null,next_poll_at=null,
         last_error=p_reason,updated_at=now()
   where t.tenant_id=p_tenant and t.interface_id=p_interface and t.status not in ('TERMINAL','FAILED');
  update a2a_remote_read_executions e set status='FAILED',tracking_status='FAILED',error_code='A2A_AUTHORITY_REVOKED',error_message=p_reason,updated_at=now()
   where e.tenant_id=p_tenant and e.interface_id=p_interface and e.status not in ('SUCCEEDED','FAILED','DEAD_LETTER');
  get diagnostics n_remote=row_count;

  insert into a2a_revocation_propagation_events(tenant_id,event_id,interface_id,reason_code,provider_links_suspended,binding_envelopes_revoked,runtime_envelopes_revoked,execution_leases_revoked,remote_executions_failed,occurred_at)
  values(p_tenant,'a2a-revoke-'||substr(md5(p_interface||':'||clock_timestamp()::text||':'||random()::text),1,28),p_interface,p_reason,n_links,n_binding,n_runtime,n_leases,n_remote,now());
end $$;

create or replace function pc_s5_interface_authority_change_trigger() returns trigger language plpgsql as $$
begin
  if not a2a_interface_current_contract_eligible(new.tenant_id,new.interface_id) then
    perform pc_s5_revoke_a2a_authority(new.tenant_id,new.interface_id,'PC_S5_INTERFACE_AUTHORITY_INVALIDATED');
  end if;
  return new;
end $$;
drop trigger if exists trg_pc_s5_interface_authority_change on a2a_peer_interfaces;
create trigger trg_pc_s5_interface_authority_change after update of endpoint_region,status,health_status,conformance_status,circuit_state on a2a_peer_interfaces
for each row execute function pc_s5_interface_authority_change_trigger();

create or replace function pc_s5_peer_authority_change_trigger() returns trigger language plpgsql as $$
declare r record;
begin
  for r in select interface_id from a2a_peer_interfaces where tenant_id=new.tenant_id and peer_id=new.peer_id loop
    if not a2a_interface_current_contract_eligible(new.tenant_id,r.interface_id)
       or not (a2a_has_assurance_grant(new.tenant_id,new.peer_id,r.interface_id,'PEER','READ_ALLOWED')
               and a2a_has_assurance_grant(new.tenant_id,new.peer_id,r.interface_id,'INTERFACE','READ_ALLOWED')) then
      perform pc_s5_revoke_a2a_authority(new.tenant_id,r.interface_id,'PC_S5_PEER_AUTHORITY_INVALIDATED');
    end if;
  end loop;
  return new;
end $$;

drop trigger if exists trg_pc_s5_trust_evidence_revoke on a2a_peer_trust_evidence;
create trigger trg_pc_s5_trust_evidence_revoke after update of evidence_status on a2a_peer_trust_evidence
for each row when (new.evidence_status='REVOKED') execute function pc_s5_peer_authority_change_trigger();

drop trigger if exists trg_pc_s5_processing_attestation_revoke on a2a_peer_processing_attestations;
create trigger trg_pc_s5_processing_attestation_revoke after update of attestation_status on a2a_peer_processing_attestations
for each row when (new.attestation_status='REVOKED') execute function pc_s5_peer_authority_change_trigger();

create or replace function pc_s5_credential_authority_change_trigger() returns trigger language plpgsql as $$
begin
  if a2a_interface_current_credential_binding_id(new.tenant_id,new.interface_id) is null then
    perform pc_s5_revoke_a2a_authority(new.tenant_id,new.interface_id,'PC_S5_CREDENTIAL_AUTHORITY_INVALIDATED');
  end if; return new;
end $$;
drop trigger if exists trg_pc_s5_credential_authority_change on a2a_interface_credential_bindings;
create trigger trg_pc_s5_credential_authority_change after update of binding_status,validation_status,valid_until on a2a_interface_credential_bindings
for each row execute function pc_s5_credential_authority_change_trigger();

create or replace function pc_s5_outbound_policy_change_trigger() returns trigger language plpgsql as $$
declare r record;
begin
  for r in select interface_id from a2a_peer_interfaces where tenant_id=new.tenant_id and outbound_destination_policy_ref=new.policy_id loop
    if a2a_interface_current_outbound_policy_id(new.tenant_id,r.interface_id) is null then
      perform pc_s5_revoke_a2a_authority(new.tenant_id,r.interface_id,'PC_S5_OUTBOUND_POLICY_INVALIDATED');
    end if;
  end loop; return new;
end $$;
drop trigger if exists trg_pc_s5_outbound_policy_change on a2a_outbound_destination_policies;
create trigger trg_pc_s5_outbound_policy_change after update of policy_status,runtime_status on a2a_outbound_destination_policies
for each row execute function pc_s5_outbound_policy_change_trigger();

create or replace function pc_s5_residency_policy_change_trigger() returns trigger language plpgsql as $$
declare r record;
begin
  for r in select interface_id from a2a_peer_interfaces where tenant_id=new.tenant_id loop
    if not a2a_interface_current_residency_allowed(new.tenant_id,r.interface_id) then
      perform pc_s5_revoke_a2a_authority(new.tenant_id,r.interface_id,
        case when new.emergency_kill_switch then 'PC_S5_RESIDENCY_KILL_SWITCH' else 'PC_S5_RESIDENCY_POLICY_INVALIDATED' end);
    end if;
  end loop; return new;
end $$;
drop trigger if exists trg_pc_s5_residency_policy_change on a2a_residency_policies;
create trigger trg_pc_s5_residency_policy_change after insert or update of policy_status,emergency_kill_switch on a2a_residency_policies
for each row execute function pc_s5_residency_policy_change_trigger();

-- Fail closed after upgrade: no historical link is grandfathered without an explicit active Residency Policy + current attestation.
update a2a_peer_provider_links l set status='SUSPENDED',updated_at=now()
 where l.status='ACTIVE' and not a2a_interface_current_contract_eligible(l.tenant_id,l.interface_id);

comment on table a2a_residency_policies is 'PC-S5 Residency Policy authority. It consumes explicit endpoint/processing/storage region facts; it does not own or infer those facts.';
comment on table a2a_residency_authorization_decisions is 'PC-S5 append-only Residency Authorization evidence. UNKNOWN is fail-closed and never execution eligible.';
comment on table a2a_revocation_propagation_events is 'PC-S5 append-only evidence that an authority loss propagated to provider links, authorization envelopes, execution leases and remote execution state.';
comment on column a2a_remote_read_executions.residency_decision_id is 'Admission-time PC-S5 residency decision snapshot. Runtime also re-evaluates current Residency Authority before every external operation.';

insert into schema_contract_authority(contract_id,contract_family,authority_note,created_at,schema_version)
values('pc-s5-residency-revocation-closure','PC_S5_RESIDENCY_AUTHORIZATION_REVOCATION',
       'REGION_FACTS_NOT_INFERRED; ACTIVE_POLICY_REQUIRED; ENDPOINT_PROCESSING_STORAGE_ALL_ALLOWED; UNKNOWN_FAIL_CLOSED; KILL_SWITCH_DENIES; AUTHORITY_LOSS_PROPAGATES_TO_LINK_ENVELOPE_LEASE_AND_REMOTE_EXECUTION; RUNTIME_REEVALUATES_CURRENT_RESIDENCY',now(),'V224')
on conflict(contract_id) do nothing;
