-- C0-B5 — A2AVersionPolicy.
-- Version compatibility is governed by an ACTIVE policy. No silent downgrade or hard-coded 1.0 runtime authority remains.

create or replace function a2a_protocol_version_valid(p_version text)
returns boolean language sql immutable as $$
  select coalesce(p_version ~ '^[0-9]{1,6}\.[0-9]{1,6}$',false)
$$;

create or replace function a2a_protocol_version_ord(p_version text)
returns bigint language sql immutable as $$
  select case when a2a_protocol_version_valid(p_version)
    then split_part(p_version,'.',1)::bigint * 10000000::bigint + split_part(p_version,'.',2)::bigint
    else null end
$$;

-- Retire the Stage 7 hard-coded protocol-version schema authority. Syntax remains constrained; policy decides compatibility.
alter table a2a_peer_interfaces drop constraint if exists a2a_interface_version_check;
alter table a2a_peer_interfaces drop constraint if exists ck_a2a_interface_protocol_version_c0b5;
alter table a2a_peer_interfaces add constraint ck_a2a_interface_protocol_version_c0b5 check (a2a_protocol_version_valid(protocol_version));

create table if not exists a2a_version_policies (
  tenant_id varchar(64) not null,
  policy_id varchar(180) not null,
  display_name varchar(255) not null,
  description text,
  policy_status varchar(24) not null default 'DRAFT',
  preferred_version varchar(40) not null,
  min_accepted_version varchar(40) not null,
  max_accepted_version varchar(40) not null,
  deprecated_versions_json jsonb not null default '[]'::jsonb,
  compatibility_mode varchar(40) not null default 'EXPLICIT_LEGACY_ALLOWLIST',
  policy_version bigint not null default 1,
  activated_at timestamptz,
  retired_at timestamptz,
  created_by varchar(160),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id,policy_id),
  constraint ck_a2a_version_policy_status_c0b5 check (policy_status in ('DRAFT','ACTIVE','RETIRED')),
  constraint ck_a2a_version_policy_preferred_c0b5 check (a2a_protocol_version_valid(preferred_version)),
  constraint ck_a2a_version_policy_min_c0b5 check (a2a_protocol_version_valid(min_accepted_version)),
  constraint ck_a2a_version_policy_max_c0b5 check (a2a_protocol_version_valid(max_accepted_version)),
  constraint ck_a2a_version_policy_range_c0b5 check (
    a2a_protocol_version_ord(min_accepted_version) <= a2a_protocol_version_ord(preferred_version)
    and a2a_protocol_version_ord(preferred_version) <= a2a_protocol_version_ord(max_accepted_version)),
  constraint ck_a2a_version_policy_deprecated_c0b5 check (a2a_jsonb_string_array(deprecated_versions_json)),
  constraint ck_a2a_version_policy_preferred_not_deprecated_c0b5 check (not jsonb_exists(deprecated_versions_json,preferred_version)),
  constraint ck_a2a_version_policy_mode_c0b5 check (compatibility_mode='EXPLICIT_LEGACY_ALLOWLIST'),
  constraint ck_a2a_version_policy_version_c0b5 check (policy_version>0)
);
create unique index if not exists uq_a2a_version_policy_active_c0b5 on a2a_version_policies(tenant_id) where policy_status='ACTIVE';

create table if not exists a2a_version_policy_legacy_allowlist (
  tenant_id varchar(64) not null,
  policy_id varchar(180) not null,
  allowlist_id varchar(180) not null,
  subject_type varchar(24) not null,
  peer_id varchar(160) not null,
  interface_id varchar(180),
  protocol_version varchar(40) not null,
  reason text not null,
  valid_until timestamptz,
  created_by varchar(160),
  created_at timestamptz not null default now(),
  primary key (tenant_id,policy_id,allowlist_id),
  constraint fk_a2a_version_allowlist_policy_c0b5 foreign key (tenant_id,policy_id) references a2a_version_policies(tenant_id,policy_id) on delete cascade,
  constraint fk_a2a_version_allowlist_peer_c0b5 foreign key (tenant_id,peer_id) references a2a_peer_registrations(tenant_id,peer_id),
  constraint fk_a2a_version_allowlist_interface_c0b5 foreign key (tenant_id,interface_id) references a2a_peer_interfaces(tenant_id,interface_id),
  constraint ck_a2a_version_allowlist_subject_c0b5 check ((subject_type='PEER' and interface_id is null) or (subject_type='INTERFACE' and interface_id is not null)),
  constraint ck_a2a_version_allowlist_version_c0b5 check (a2a_protocol_version_valid(protocol_version))
);
create index if not exists idx_a2a_version_allowlist_lookup_c0b5 on a2a_version_policy_legacy_allowlist(tenant_id,policy_id,peer_id,interface_id,protocol_version,valid_until);

alter table a2a_version_policies enable row level security;
alter table a2a_version_policy_legacy_allowlist enable row level security;
drop policy if exists tenant_isolation on a2a_version_policies;
create policy tenant_isolation on a2a_version_policies using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id());
drop policy if exists tenant_isolation on a2a_version_policy_legacy_allowlist;
create policy tenant_isolation on a2a_version_policy_legacy_allowlist using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id());

-- Explicit current baseline policy. This replaces the old schema hard-code; it does not grandfather arbitrary versions.
insert into a2a_version_policies(tenant_id,policy_id,display_name,description,policy_status,preferred_version,min_accepted_version,max_accepted_version,deprecated_versions_json,compatibility_mode,policy_version,activated_at,created_by,created_at,updated_at)
select t.tenant_id,'a2a-version-default-v1','A2A Version Policy v1','C0-B5 explicit current A2A 1.0 baseline','ACTIVE','1.0','1.0','1.0','[]'::jsonb,'EXPLICIT_LEGACY_ALLOWLIST',1,now(),'c0-b5-migration',now(),now()
  from tenants t
 where not exists(select 1 from a2a_version_policies p where p.tenant_id=t.tenant_id and p.policy_status='ACTIVE')
on conflict(tenant_id,policy_id) do nothing;

create or replace function a2a_version_policy_decision(p_tenant text,p_peer text,p_interface text,p_version text)
returns text language sql stable as $$
  with active_policy as (
    select p.* from a2a_version_policies p where p.tenant_id=p_tenant and p.policy_status='ACTIVE' limit 1
  ), facts as (
    select p.*,
      a2a_protocol_version_valid(p_version) as version_valid,
      a2a_protocol_version_ord(p_version) as version_ord,
      jsonb_exists(p.deprecated_versions_json,p_version) as deprecated,
      exists(
        select 1 from a2a_version_policy_legacy_allowlist l
         where l.tenant_id=p_tenant and l.policy_id=p.policy_id and l.peer_id=p_peer and l.protocol_version=p_version
           and (l.valid_until is null or l.valid_until>now())
           and ((l.subject_type='PEER' and l.interface_id is null) or (l.subject_type='INTERFACE' and l.interface_id=p_interface))
      ) as explicitly_allowlisted
    from active_policy p
  )
  select coalesce((select case
    when not version_valid then 'REJECTED'
    when version_ord between a2a_protocol_version_ord(min_accepted_version) and a2a_protocol_version_ord(max_accepted_version) and not deprecated then 'SUPPORTED'
    when explicitly_allowlisted then 'DEPRECATED_ALLOWED'
    else 'REJECTED' end from facts),'REJECTED')
$$;

create or replace function a2a_version_policy_allowed(p_tenant text,p_peer text,p_interface text,p_version text)
returns boolean language sql stable as $$
  select a2a_version_policy_decision(p_tenant,p_peer,p_interface,p_version) in ('SUPPORTED','DEPRECATED_ALLOWED')
$$;

-- B4 interface eligibility now consumes B5 version policy instead of a hard-coded 1.0 predicate.
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
       and i.outbound_destination_policy_ref='EXTERNAL_HTTP_DEFAULT'
       and i.health_status='HEALTHY'
       and i.conformance_status='PASS'
       and i.circuit_state='CLOSED'
       and i.supported_extensions_json @> i.required_extensions_json
  )
$$;

-- Existing ACTIVE provider links cannot bypass a newly rejecting version policy.
update a2a_peer_provider_links l
   set status='SUSPENDED',updated_at=now()
 where l.status='ACTIVE'
   and not a2a_interface_current_contract_eligible(l.tenant_id,l.interface_id);

-- Snapshot the exact protocol version used for an execution. Historical rows were necessarily 1.0 under the retired V193 constraint.
alter table a2a_remote_read_executions
  add column if not exists selected_protocol_version varchar(40),
  add column if not exists version_policy_id varchar(180),
  add column if not exists version_policy_decision varchar(32);
update a2a_remote_read_executions e
   set selected_protocol_version=coalesce(i.protocol_version,'1.0')
  from a2a_peer_interfaces i
 where e.tenant_id=i.tenant_id and e.interface_id=i.interface_id and e.selected_protocol_version is null;
update a2a_remote_read_executions set selected_protocol_version='1.0' where selected_protocol_version is null;
alter table a2a_remote_read_executions alter column selected_protocol_version set not null;
alter table a2a_remote_read_executions drop constraint if exists ck_a2a_execution_selected_protocol_version_c0b5;
alter table a2a_remote_read_executions add constraint ck_a2a_execution_selected_protocol_version_c0b5 check (a2a_protocol_version_valid(selected_protocol_version));
alter table a2a_remote_read_executions drop constraint if exists ck_a2a_execution_version_decision_c0b5;
alter table a2a_remote_read_executions add constraint ck_a2a_execution_version_decision_c0b5 check (version_policy_decision is null or version_policy_decision in ('SUPPORTED','DEPRECATED_ALLOWED'));

comment on table a2a_version_policies is 'C0-B5 A2AVersionPolicy authority. One ACTIVE policy per tenant computes protocol compatibility; no silent downgrade is permitted.';
comment on table a2a_version_policy_legacy_allowlist is 'Explicit scoped exception for deprecated/out-of-range versions. It never changes the interface-declared version.';
comment on column a2a_remote_read_executions.selected_protocol_version is 'Immutable-by-execution semantic snapshot of the exact A2A-Version used for outbound send/tracking; runtime must not silently switch versions mid-flight.';

insert into schema_contract_authority(contract_id,contract_family,authority_note,created_at,schema_version)
values('c0-b5-a2a-version-policy','C0_B5_A2A_VERSION_POLICY','ACTIVE_POLICY_REQUIRED; SUPPORTED_OR_EXPLICIT_DEPRECATED_ALLOWLIST; NO_SILENT_DOWNGRADE; EXACT_SELECTED_PROTOCOL_VERSION_SNAPSHOTTED',now(),'V216')
on conflict(contract_id) do nothing;
