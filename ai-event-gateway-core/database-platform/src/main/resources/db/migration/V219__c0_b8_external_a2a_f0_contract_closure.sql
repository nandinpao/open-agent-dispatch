-- C0-B8 — External A2A F0 Contract Closure.
-- Completes governed interface credential binding and tenant-scoped outbound destination policy.
-- Runtime execution snapshots the exact protocol version, credential binding and outbound policy selected at admission.
-- This migration does NOT implement residency authorization, mTLS client identity, certificate pinning or authoritative async stream ownership.

create table if not exists a2a_interface_credential_bindings (
  tenant_id varchar(64) not null,
  binding_id varchar(180) not null,
  interface_id varchar(180) not null,
  security_scheme_ref varchar(180) not null,
  credential_type varchar(40) not null,
  secret_ref varchar(512) not null,
  header_name varchar(160),
  auth_scheme varchar(80),
  binding_status varchar(24) not null default 'DRAFT',
  validation_status varchar(24) not null default 'UNKNOWN',
  validation_message text,
  validated_at timestamptz,
  valid_until timestamptz,
  binding_version bigint not null default 1,
  created_by varchar(160),
  activated_at timestamptz,
  retired_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id,binding_id),
  constraint fk_a2a_credential_binding_interface_c0b8 foreign key (tenant_id,interface_id)
    references a2a_peer_interfaces(tenant_id,interface_id) on delete cascade,
  constraint ck_a2a_credential_type_c0b8 check (credential_type in ('BEARER_TOKEN','API_KEY_HEADER','MTLS')),
  constraint ck_a2a_credential_binding_status_c0b8 check (binding_status in ('DRAFT','ACTIVE','RETIRED')),
  constraint ck_a2a_credential_validation_status_c0b8 check (validation_status in ('UNKNOWN','VALID','INVALID')),
  constraint ck_a2a_credential_binding_version_c0b8 check (binding_version>0),
  constraint ck_a2a_credential_secret_ref_c0b8 check (length(btrim(secret_ref))>6),
  constraint ck_a2a_credential_api_key_header_c0b8 check (credential_type<>'API_KEY_HEADER' or nullif(btrim(header_name),'') is not null)
);
create unique index if not exists uq_a2a_interface_active_credential_c0b8
  on a2a_interface_credential_bindings(tenant_id,interface_id) where binding_status='ACTIVE';
create index if not exists idx_a2a_interface_credential_lookup_c0b8
  on a2a_interface_credential_bindings(tenant_id,interface_id,binding_status,validation_status);

create table if not exists a2a_outbound_destination_policies (
  tenant_id varchar(64) not null,
  policy_id varchar(180) not null,
  display_name varchar(255) not null,
  description text,
  allowed_schemes_json jsonb not null default '["https"]'::jsonb,
  allowlist_host_suffixes_json jsonb not null default '[]'::jsonb,
  allowlist_cidrs_json jsonb not null default '[]'::jsonb,
  deny_private_addresses boolean not null default true,
  deny_loopback_addresses boolean not null default true,
  deny_link_local_addresses boolean not null default true,
  deny_cloud_metadata_endpoints boolean not null default true,
  max_redirects integer not null default 0,
  follow_redirect_same_origin_only boolean not null default true,
  dns_rebinding_protection boolean not null default false,
  connect_timeout_ms integer not null default 10000,
  read_timeout_ms integer not null default 45000,
  required_tls_version varchar(40),
  certificate_pins_json jsonb not null default '[]'::jsonb,
  policy_status varchar(24) not null default 'DRAFT',
  runtime_status varchar(32) not null default 'RUNTIME_READY',
  runtime_status_reason text,
  policy_version bigint not null default 1,
  created_by varchar(160),
  activated_at timestamptz,
  retired_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id,policy_id),
  constraint ck_a2a_outbound_allowed_schemes_c0b8 check (a2a_jsonb_string_array(allowed_schemes_json)),
  constraint ck_a2a_outbound_hosts_c0b8 check (a2a_jsonb_string_array(allowlist_host_suffixes_json)),
  constraint ck_a2a_outbound_cidrs_c0b8 check (a2a_jsonb_string_array(allowlist_cidrs_json)),
  constraint ck_a2a_outbound_pins_c0b8 check (a2a_jsonb_string_array(certificate_pins_json)),
  constraint ck_a2a_outbound_redirects_c0b8 check (max_redirects between 0 and 10),
  constraint ck_a2a_outbound_connect_timeout_c0b8 check (connect_timeout_ms between 100 and 120000),
  constraint ck_a2a_outbound_read_timeout_c0b8 check (read_timeout_ms between 100 and 300000),
  constraint ck_a2a_outbound_status_c0b8 check (policy_status in ('DRAFT','ACTIVE','RETIRED')),
  constraint ck_a2a_outbound_runtime_status_c0b8 check (runtime_status in ('RUNTIME_READY','NOT_RUNTIME_SUPPORTED')),
  constraint ck_a2a_outbound_version_c0b8 check (policy_version>0)
);
create index if not exists idx_a2a_outbound_policy_status_c0b8
  on a2a_outbound_destination_policies(tenant_id,policy_status,runtime_status,policy_id);

-- Existing B4 references remain valid only after a concrete tenant policy exists.
insert into a2a_outbound_destination_policies(
  tenant_id,policy_id,display_name,description,allowed_schemes_json,
  deny_private_addresses,deny_loopback_addresses,deny_link_local_addresses,deny_cloud_metadata_endpoints,
  max_redirects,follow_redirect_same_origin_only,dns_rebinding_protection,connect_timeout_ms,read_timeout_ms,
  policy_status,runtime_status,runtime_status_reason,policy_version,created_by,activated_at,created_at,updated_at)
select t.tenant_id,'EXTERNAL_HTTP_DEFAULT','External HTTP Default',
       'C0-B8 default external A2A destination policy. Redirects are disabled; private/loopback/link-local/cloud-metadata destinations are denied.',
       '["https","http"]'::jsonb,true,true,true,true,0,true,false,10000,45000,
       'ACTIVE','RUNTIME_READY','DNS rebinding hardening and certificate pinning remain outside C0-B8 runtime certification.',1,
       'c0-b8-migration',now(),now(),now()
  from tenants t
on conflict(tenant_id,policy_id) do nothing;

create or replace function a2a_interface_current_credential_binding_id(p_tenant text,p_interface text)
returns text language sql stable as $$
  select b.binding_id
    from a2a_interface_credential_bindings b
   where b.tenant_id=p_tenant and b.interface_id=p_interface
     and b.binding_status='ACTIVE' and b.validation_status='VALID'
     and (b.valid_until is null or b.valid_until>now())
     and b.credential_type in ('BEARER_TOKEN','API_KEY_HEADER')
   order by b.activated_at desc nulls last,b.binding_id
   limit 1
$$;

create or replace function a2a_interface_current_outbound_policy_id(p_tenant text,p_interface text)
returns text language sql stable as $$
  select p.policy_id
    from a2a_peer_interfaces i
    join a2a_outbound_destination_policies p
      on p.tenant_id=i.tenant_id and p.policy_id=i.outbound_destination_policy_ref
   where i.tenant_id=p_tenant and i.interface_id=p_interface
     and p.policy_status='ACTIVE' and p.runtime_status='RUNTIME_READY'
     and p.max_redirects=0
   limit 1
$$;

-- B4/B5/B7 eligibility remains the single interface gate, now with concrete credential and outbound-policy authority.
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
  )
$$;

alter table a2a_remote_read_executions
  add column if not exists credential_binding_id varchar(180),
  add column if not exists outbound_destination_policy_id varchar(180);
create index if not exists idx_a2a_execution_security_snapshot_c0b8
  on a2a_remote_read_executions(tenant_id,credential_binding_id,outbound_destination_policy_id);
comment on column a2a_remote_read_executions.credential_binding_id is
'C0-B8 immutable-by-execution credential binding snapshot. Send/Poll/SSE/Push/Cancel MUST reuse this ID and MUST NOT silently switch to a newer binding.';
comment on column a2a_remote_read_executions.outbound_destination_policy_id is
'C0-B8 immutable-by-execution outbound destination policy snapshot. Send/Poll/SSE/Push/Cancel MUST reuse this ID.';

alter table a2a_interface_credential_bindings enable row level security;
alter table a2a_outbound_destination_policies enable row level security;
do $$ declare t text; begin
  foreach t in array array['a2a_interface_credential_bindings','a2a_outbound_destination_policies'] loop
    execute format('drop policy if exists tenant_isolation on %I',t);
    execute format('create policy tenant_isolation on %I using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id())',t);
  end loop;
end $$;

-- No provider link is grandfathered through the new credential/policy gate.
update a2a_peer_provider_links l
   set status='SUSPENDED',updated_at=now()
 where l.status='ACTIVE' and not a2a_interface_current_contract_eligible(l.tenant_id,l.interface_id);

comment on table a2a_interface_credential_bindings is
'C0-B8 governed credential binding. secret_ref is opaque; raw credentials MUST NOT be persisted. Current runtime resolves env:// only. mTLS is declared but NOT runtime-supported in C0-B8.';
comment on table a2a_outbound_destination_policies is
'C0-B8 tenant-scoped outbound destination policy authority. Policy must be ACTIVE and RUNTIME_READY before an interface is runtime eligible.';
comment on column a2a_outbound_destination_policies.dns_rebinding_protection is
'Governance declaration only in C0-B8. Full connection-level DNS pin/rebind certification is deferred and MUST NOT be represented as certified by this stage.';
comment on column a2a_outbound_destination_policies.certificate_pins_json is
'Governance declaration only in C0-B8. Certificate pin enforcement is not certified by C0-B8 runtime.';

insert into schema_contract_authority(contract_id,contract_family,authority_note,created_at,schema_version)
values('c0-b8-external-a2a-f0-contract-closure','C0_B8_EXTERNAL_A2A_F0_CONTRACT_CLOSURE',
       'CREDENTIAL_BINDING_GOVERNED_AND_VALIDATED; SECRET_REF_OPAQUE_ENV_RUNTIME; OUTBOUND_POLICY_ACTIVE_RUNTIME_READY; EXECUTION_SNAPSHOTS_PROTOCOL_CREDENTIAL_POLICY; NO_SILENT_SECURITY_SWITCH; MTLS_PINNING_DNS_REBINDING_RESIDENCY_NOT_CERTIFIED',now(),'V219')
on conflict(contract_id) do nothing;
