-- C0-B4 — A2APeerInterface current-contract completion.
-- Interface-owned endpoint/operational facts are explicit eligibility inputs.
-- This migration does NOT implement residency authorization and MUST NOT infer endpoint_region from URL/DNS/network location.

alter table a2a_peer_interfaces
  add column if not exists endpoint_region varchar(96),
  add column if not exists security_scheme_refs_json jsonb not null default '[]'::jsonb,
  add column if not exists required_extensions_json jsonb not null default '[]'::jsonb,
  add column if not exists supported_extensions_json jsonb not null default '[]'::jsonb,
  add column if not exists outbound_destination_policy_ref varchar(160),
  add column if not exists priority integer not null default 100,
  add column if not exists health_status varchar(24) not null default 'UNKNOWN',
  add column if not exists conformance_status varchar(24) not null default 'UNKNOWN',
  add column if not exists circuit_state varchar(24) not null default 'CLOSED',
  add column if not exists contract_version bigint not null default 1;

create or replace function a2a_jsonb_string_array(p_value jsonb)
returns boolean language sql immutable as $$
  select jsonb_typeof(p_value)='array'
     and not exists(select 1 from jsonb_array_elements(p_value) v where jsonb_typeof(v)<>'string')
$$;

alter table a2a_peer_interfaces drop constraint if exists ck_a2a_interface_security_scheme_refs_c0b4;
alter table a2a_peer_interfaces add constraint ck_a2a_interface_security_scheme_refs_c0b4 check (a2a_jsonb_string_array(security_scheme_refs_json));
alter table a2a_peer_interfaces drop constraint if exists ck_a2a_interface_required_extensions_c0b4;
alter table a2a_peer_interfaces add constraint ck_a2a_interface_required_extensions_c0b4 check (a2a_jsonb_string_array(required_extensions_json));
alter table a2a_peer_interfaces drop constraint if exists ck_a2a_interface_supported_extensions_c0b4;
alter table a2a_peer_interfaces add constraint ck_a2a_interface_supported_extensions_c0b4 check (a2a_jsonb_string_array(supported_extensions_json));
alter table a2a_peer_interfaces drop constraint if exists ck_a2a_interface_priority_c0b4;
alter table a2a_peer_interfaces add constraint ck_a2a_interface_priority_c0b4 check (priority between 0 and 10000);
alter table a2a_peer_interfaces drop constraint if exists ck_a2a_interface_health_c0b4;
alter table a2a_peer_interfaces add constraint ck_a2a_interface_health_c0b4 check (health_status in ('UNKNOWN','HEALTHY','DEGRADED','UNHEALTHY'));
alter table a2a_peer_interfaces drop constraint if exists ck_a2a_interface_conformance_c0b4;
alter table a2a_peer_interfaces add constraint ck_a2a_interface_conformance_c0b4 check (conformance_status in ('UNKNOWN','PASS','DEGRADED','FAIL'));
alter table a2a_peer_interfaces drop constraint if exists ck_a2a_interface_circuit_c0b4;
alter table a2a_peer_interfaces add constraint ck_a2a_interface_circuit_c0b4 check (circuit_state in ('CLOSED','OPEN','HALF_OPEN'));
alter table a2a_peer_interfaces drop constraint if exists ck_a2a_interface_contract_version_c0b4;
alter table a2a_peer_interfaces add constraint ck_a2a_interface_contract_version_c0b4 check (contract_version>0);

-- Current runtime eligibility intentionally fails closed until governance supplies explicit interface facts.
-- endpoint_region is a fact input only; residency authorization is a later gate.
create or replace function a2a_interface_current_contract_eligible(p_tenant text,p_interface text)
returns boolean language sql stable as $$
  select exists(
    select 1
      from a2a_peer_interfaces i
     where i.tenant_id=p_tenant and i.interface_id=p_interface
       and i.status='APPROVED'
       and i.protocol_binding='HTTP+JSON' and i.protocol_version='1.0'
       and nullif(btrim(i.endpoint_region),'') is not null
       and i.outbound_destination_policy_ref='EXTERNAL_HTTP_DEFAULT'
       and i.health_status='HEALTHY'
       and i.conformance_status='PASS'
       and i.circuit_state='CLOSED'
       and i.supported_extensions_json @> i.required_extensions_json
  )
$$;

-- Existing provider links are not grandfathered into current eligibility with UNKNOWN interface facts.
update a2a_peer_provider_links l
   set status='SUSPENDED',updated_at=now()
 where l.status='ACTIVE'
   and not a2a_interface_current_contract_eligible(l.tenant_id,l.interface_id);

comment on column a2a_peer_interfaces.endpoint_region is
'A2APeerInterface-owned endpointRegion fact. C0-B4 forbids inference from URL, DNS, IP or PeerProcessingAttestation. Residency authorization is NOT decided here.';
comment on column a2a_peer_interfaces.security_scheme_refs_json is
'Current interface securitySchemeRefs[] contract. Presence is descriptive/governed input; credential binding remains a separate authority.';
comment on column a2a_peer_interfaces.required_extensions_json is
'Extensions required by this interface. Current runtime eligibility requires requiredExtensions subset of supportedExtensions; unsupported requirements fail closed.';
comment on column a2a_peer_interfaces.supported_extensions_json is
'Extensions currently supported/accepted for this interface.';
comment on column a2a_peer_interfaces.outbound_destination_policy_ref is
'Outbound destination policy reference. C0-B4 current runtime accepts EXTERNAL_HTTP_DEFAULT only; richer policy resolution is security-hardening work.';
comment on column a2a_peer_interfaces.health_status is 'Interface-scoped operational health. UNHEALTHY/DEGRADED/UNKNOWN are not current runtime eligible in C0-B4.';
comment on column a2a_peer_interfaces.conformance_status is 'Per-interface conformance state. Only PASS is current runtime eligible in C0-B4.';
comment on column a2a_peer_interfaces.circuit_state is 'Per-interface circuit state. OPEN/HALF_OPEN are not current runtime eligible in C0-B4; they do not disable the whole peer.';

insert into schema_contract_authority(contract_id,contract_family,authority_note,created_at,schema_version)
values('c0-b4-a2a-peer-interface-current-contract','C0_B4_A2A_PEER_INTERFACE','ENDPOINT_REGION_INTERFACE_OWNED; EXTENSIONS_FAIL_CLOSED; INTERFACE_HEALTH_CONFORMANCE_CIRCUIT_SCOPED; OUTBOUND_POLICY_REF_REQUIRED; NO_RESIDENCY_AUTHORIZATION_YET',now(),'V215')
on conflict(contract_id) do nothing;

comment on column a2a_peer_interfaces.conformance_status is
'C0-B4 stores the field and fails closed on UNKNOWN. Generic Admin MUST NOT self-certify PASS; governed conformance write authority is deferred to C0-B7.';
