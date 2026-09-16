-- C0-B7 — External A2A F0 Interface Conformance authority.
-- Admin may request a conformance run, but only the platform conformance runner may create PASS/FAIL evidence.
-- Runtime eligibility consumes current, unexpired PASS evidence under the ACTIVE profile; direct conformance PASS writes remain forbidden.

create or replace function a2a_conformance_profile_has_baseline(p_checks jsonb)
returns boolean language sql immutable as $$
  select a2a_jsonb_string_array(p_checks)
     and p_checks @> '["AGENT_CARD_INTERFACE_MATCH","PROTOCOL_BINDING","VERSION_POLICY","ENDPOINT_REGION","OUTBOUND_DESTINATION_POLICY","EXTENSIONS","SECURITY_SCHEMES"]'::jsonb
$$;

create table if not exists a2a_interface_conformance_profiles (
  tenant_id varchar(64) not null,
  profile_id varchar(180) not null,
  display_name varchar(255) not null,
  description text,
  required_checks_json jsonb not null,
  valid_for_seconds bigint not null default 86400,
  max_agent_card_age_seconds bigint not null default 86400,
  profile_status varchar(24) not null default 'DRAFT',
  profile_version bigint not null default 1,
  created_by varchar(160) not null,
  created_at timestamptz not null default now(),
  activated_at timestamptz,
  retired_at timestamptz,
  updated_at timestamptz not null default now(),
  primary key (tenant_id,profile_id),
  constraint ck_a2a_conformance_profile_checks_c0b7 check (a2a_conformance_profile_has_baseline(required_checks_json)),
  constraint ck_a2a_conformance_profile_valid_for_c0b7 check (valid_for_seconds between 60 and 604800),
  constraint ck_a2a_conformance_profile_card_age_c0b7 check (max_agent_card_age_seconds between 60 and 604800),
  constraint ck_a2a_conformance_profile_status_c0b7 check (profile_status in ('DRAFT','ACTIVE','RETIRED')),
  constraint ck_a2a_conformance_profile_version_c0b7 check (profile_version>0),
  constraint ck_a2a_conformance_profile_lifecycle_c0b7 check (
    (profile_status='DRAFT' and activated_at is null and retired_at is null)
    or (profile_status='ACTIVE' and activated_at is not null and retired_at is null)
    or (profile_status='RETIRED' and activated_at is not null and retired_at is not null)
  )
);
create unique index if not exists uq_a2a_conformance_active_profile_c0b7
  on a2a_interface_conformance_profiles(tenant_id) where profile_status='ACTIVE';

create table if not exists a2a_interface_conformance_runs (
  tenant_id varchar(64) not null,
  run_id varchar(180) not null,
  interface_id varchar(180) not null,
  peer_id varchar(160) not null,
  profile_id varchar(180) not null,
  profile_version bigint not null,
  required_checks_json jsonb not null,
  valid_for_seconds bigint not null,
  max_agent_card_age_seconds bigint not null,
  run_status varchar(24) not null default 'RUNNING',
  outcome varchar(24) not null default 'UNKNOWN',
  started_at timestamptz not null default now(),
  completed_at timestamptz,
  valid_until timestamptz,
  summary_json jsonb not null default '{}'::jsonb,
  created_by varchar(160) not null,
  primary key (tenant_id,run_id),
  constraint fk_a2a_conformance_run_interface_c0b7 foreign key (tenant_id,interface_id) references a2a_peer_interfaces(tenant_id,interface_id),
  constraint fk_a2a_conformance_run_peer_c0b7 foreign key (tenant_id,peer_id) references a2a_peer_registrations(tenant_id,peer_id),
  constraint fk_a2a_conformance_run_profile_c0b7 foreign key (tenant_id,profile_id) references a2a_interface_conformance_profiles(tenant_id,profile_id),
  constraint ck_a2a_conformance_run_checks_c0b7 check (a2a_conformance_profile_has_baseline(required_checks_json)),
  constraint ck_a2a_conformance_run_status_c0b7 check (run_status in ('RUNNING','COMPLETED')),
  constraint ck_a2a_conformance_run_outcome_c0b7 check (outcome in ('UNKNOWN','PASS','FAIL')),
  constraint ck_a2a_conformance_run_summary_c0b7 check (jsonb_typeof(summary_json)='object'),
  constraint ck_a2a_conformance_run_lifecycle_c0b7 check (
    (run_status='RUNNING' and outcome='UNKNOWN' and completed_at is null and valid_until is null)
    or (run_status='COMPLETED' and outcome='FAIL' and completed_at is not null and valid_until is null)
    or (run_status='COMPLETED' and outcome='PASS' and completed_at is not null and valid_until is not null and valid_until>completed_at)
  )
);
create unique index if not exists uq_a2a_conformance_running_interface_c0b7
  on a2a_interface_conformance_runs(tenant_id,interface_id) where run_status='RUNNING';
create index if not exists idx_a2a_conformance_runs_interface_c0b7
  on a2a_interface_conformance_runs(tenant_id,interface_id,started_at desc);

create table if not exists a2a_interface_conformance_evidence (
  tenant_id varchar(64) not null,
  evidence_id varchar(180) not null,
  run_id varchar(180) not null,
  interface_id varchar(180) not null,
  check_code varchar(96) not null,
  check_status varchar(16) not null,
  expected_json jsonb not null default '{}'::jsonb,
  observed_json jsonb not null default '{}'::jsonb,
  evidence_ref varchar(512),
  evidence_digest varchar(160),
  evidence_source varchar(64) not null default 'PLATFORM_AUTOMATED',
  created_at timestamptz not null default now(),
  primary key (tenant_id,evidence_id),
  unique (tenant_id,run_id,check_code),
  constraint fk_a2a_conformance_evidence_run_c0b7 foreign key (tenant_id,run_id) references a2a_interface_conformance_runs(tenant_id,run_id),
  constraint fk_a2a_conformance_evidence_interface_c0b7 foreign key (tenant_id,interface_id) references a2a_peer_interfaces(tenant_id,interface_id),
  constraint ck_a2a_conformance_evidence_status_c0b7 check (check_status in ('PASS','FAIL')),
  constraint ck_a2a_conformance_evidence_expected_c0b7 check (jsonb_typeof(expected_json)='object'),
  constraint ck_a2a_conformance_evidence_observed_c0b7 check (jsonb_typeof(observed_json)='object'),
  constraint ck_a2a_conformance_evidence_source_c0b7 check (evidence_source='PLATFORM_AUTOMATED')
);
create index if not exists idx_a2a_conformance_evidence_run_c0b7
  on a2a_interface_conformance_evidence(tenant_id,run_id,check_code);

alter table a2a_peer_interfaces
  add column if not exists conformance_profile_id varchar(180),
  add column if not exists conformance_run_id varchar(180),
  add column if not exists conformance_valid_until timestamptz;

alter table a2a_peer_interfaces drop constraint if exists ck_a2a_interface_conformance_c0b4;
alter table a2a_peer_interfaces add constraint ck_a2a_interface_conformance_c0b7
  check (conformance_status in ('UNKNOWN','PASS','DEGRADED','FAIL','EXPIRED'));
alter table a2a_peer_interfaces drop constraint if exists fk_a2a_interface_conformance_profile_c0b7;
alter table a2a_peer_interfaces add constraint fk_a2a_interface_conformance_profile_c0b7
  foreign key (tenant_id,conformance_profile_id) references a2a_interface_conformance_profiles(tenant_id,profile_id);
alter table a2a_peer_interfaces drop constraint if exists fk_a2a_interface_conformance_run_c0b7;
alter table a2a_peer_interfaces add constraint fk_a2a_interface_conformance_run_c0b7
  foreign key (tenant_id,conformance_run_id) references a2a_interface_conformance_runs(tenant_id,run_id);

alter table a2a_interface_conformance_profiles enable row level security;
alter table a2a_interface_conformance_runs enable row level security;
alter table a2a_interface_conformance_evidence enable row level security;
drop policy if exists tenant_isolation on a2a_interface_conformance_profiles;
create policy tenant_isolation on a2a_interface_conformance_profiles using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id());
drop policy if exists tenant_isolation on a2a_interface_conformance_runs;
create policy tenant_isolation on a2a_interface_conformance_runs using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id());
drop policy if exists tenant_isolation on a2a_interface_conformance_evidence;
create policy tenant_isolation on a2a_interface_conformance_evidence using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id());

create or replace function protect_a2a_conformance_profile_c0b7() returns trigger language plpgsql as $$
begin
  if old.profile_status='RETIRED' then
    raise exception 'C0_B7_CONFORMANCE_PROFILE_RETIRED_IMMUTABLE' using errcode='55000';
  end if;
  if old.profile_status='ACTIVE' then
    if old.tenant_id is distinct from new.tenant_id
       or old.profile_id is distinct from new.profile_id
       or old.display_name is distinct from new.display_name
       or old.description is distinct from new.description
       or old.required_checks_json is distinct from new.required_checks_json
       or old.valid_for_seconds is distinct from new.valid_for_seconds
       or old.max_agent_card_age_seconds is distinct from new.max_agent_card_age_seconds
       or old.profile_version is distinct from new.profile_version
       or old.created_by is distinct from new.created_by
       or old.created_at is distinct from new.created_at
       or new.profile_status not in ('ACTIVE','RETIRED') then
      raise exception 'C0_B7_ACTIVE_CONFORMANCE_PROFILE_FACT_IMMUTABLE' using errcode='55000';
    end if;
  end if;
  return new;
end $$;
drop trigger if exists trg_a2a_conformance_profile_c0b7 on a2a_interface_conformance_profiles;
create trigger trg_a2a_conformance_profile_c0b7 before update on a2a_interface_conformance_profiles
for each row execute function protect_a2a_conformance_profile_c0b7();

create or replace function protect_a2a_conformance_run_c0b7() returns trigger language plpgsql as $$
begin
  if old.tenant_id is distinct from new.tenant_id
     or old.run_id is distinct from new.run_id
     or old.interface_id is distinct from new.interface_id
     or old.peer_id is distinct from new.peer_id
     or old.profile_id is distinct from new.profile_id
     or old.profile_version is distinct from new.profile_version
     or old.required_checks_json is distinct from new.required_checks_json
     or old.valid_for_seconds is distinct from new.valid_for_seconds
     or old.max_agent_card_age_seconds is distinct from new.max_agent_card_age_seconds
     or old.started_at is distinct from new.started_at
     or old.created_by is distinct from new.created_by then
    raise exception 'C0_B7_CONFORMANCE_RUN_FACT_IMMUTABLE' using errcode='55000';
  end if;
  if old.run_status='COMPLETED' then
    raise exception 'C0_B7_CONFORMANCE_RUN_COMPLETED_IMMUTABLE' using errcode='55000';
  end if;
  if old.run_status='RUNNING' and new.run_status<>'COMPLETED' then
    raise exception 'C0_B7_CONFORMANCE_RUN_INVALID_TRANSITION' using errcode='55000';
  end if;
  return new;
end $$;
drop trigger if exists trg_a2a_conformance_run_c0b7 on a2a_interface_conformance_runs;
create trigger trg_a2a_conformance_run_c0b7 before update on a2a_interface_conformance_runs
for each row execute function protect_a2a_conformance_run_c0b7();

create or replace function prevent_a2a_conformance_evidence_mutation_c0b7() returns trigger language plpgsql as $$
begin raise exception 'C0_B7_CONFORMANCE_EVIDENCE_APPEND_ONLY' using errcode='55000'; end $$;
drop trigger if exists trg_a2a_conformance_evidence_append_only_c0b7 on a2a_interface_conformance_evidence;
create trigger trg_a2a_conformance_evidence_append_only_c0b7 before update or delete on a2a_interface_conformance_evidence
for each row execute function prevent_a2a_conformance_evidence_mutation_c0b7();

create or replace function a2a_interface_conformance_effective_status(p_tenant text,p_interface text)
returns text language sql stable as $$
  select coalesce((
    select case
      when r.run_status='COMPLETED' and r.outcome='PASS' and p.profile_status='ACTIVE' and r.valid_until>now() then 'PASS'
      when r.run_status='COMPLETED' and r.outcome='PASS' then 'EXPIRED'
      when r.run_status='COMPLETED' and r.outcome='FAIL' then 'FAIL'
      else 'UNKNOWN' end
      from a2a_peer_interfaces i
      left join a2a_interface_conformance_runs r
        on r.tenant_id=i.tenant_id and r.run_id=i.conformance_run_id and r.interface_id=i.interface_id
      left join a2a_interface_conformance_profiles p
        on p.tenant_id=i.tenant_id and p.profile_id=r.profile_id
     where i.tenant_id=p_tenant and i.interface_id=p_interface
  ),'UNKNOWN')
$$;

create or replace function a2a_interface_conformance_current_pass(p_tenant text,p_interface text)
returns boolean language sql stable as $$
  select a2a_interface_conformance_effective_status(p_tenant,p_interface)='PASS'
$$;

create or replace function protect_a2a_interface_conformance_projection_c0b7() returns trigger language plpgsql as $$
declare v_outcome text; v_profile text; v_until timestamptz;
begin
  if old.conformance_status is not distinct from new.conformance_status
     and old.conformance_profile_id is not distinct from new.conformance_profile_id
     and old.conformance_run_id is not distinct from new.conformance_run_id
     and old.conformance_valid_until is not distinct from new.conformance_valid_until then
    return new;
  end if;
  if new.conformance_status in ('PASS','FAIL') then
    if new.conformance_run_id is null or new.conformance_profile_id is null then
      raise exception 'C0_B7_CONFORMANCE_PROJECTION_REQUIRES_GOVERNED_RUN' using errcode='55000';
    end if;
    select r.outcome,r.profile_id,r.valid_until into v_outcome,v_profile,v_until
      from a2a_interface_conformance_runs r
     where r.tenant_id=new.tenant_id and r.run_id=new.conformance_run_id
       and r.interface_id=new.interface_id and r.run_status='COMPLETED';
    if v_outcome is null or v_outcome<>new.conformance_status or v_profile<>new.conformance_profile_id then
      raise exception 'C0_B7_CONFORMANCE_PROJECTION_RUN_MISMATCH' using errcode='55000';
    end if;
    if new.conformance_status='PASS' and (v_until is null or v_until<=now() or new.conformance_valid_until is distinct from v_until) then
      raise exception 'C0_B7_CONFORMANCE_PASS_REQUIRES_CURRENT_VALID_RUN' using errcode='55000';
    end if;
  elsif new.conformance_status='UNKNOWN' then
    if new.conformance_run_id is not null or new.conformance_profile_id is not null or new.conformance_valid_until is not null then
      raise exception 'C0_B7_UNKNOWN_CONFORMANCE_PROJECTION_MUST_CLEAR_RUN' using errcode='55000';
    end if;
  elsif new.conformance_status='EXPIRED' then
    if new.conformance_run_id is null then raise exception 'C0_B7_EXPIRED_CONFORMANCE_REQUIRES_RUN' using errcode='55000'; end if;
  else
    raise exception 'C0_B7_CONFORMANCE_PROJECTION_WRITE_NOT_GOVERNED' using errcode='55000';
  end if;
  return new;
end $$;
drop trigger if exists trg_a2a_interface_conformance_projection_c0b7 on a2a_peer_interfaces;
create trigger trg_a2a_interface_conformance_projection_c0b7
before update of conformance_status,conformance_profile_id,conformance_run_id,conformance_valid_until on a2a_peer_interfaces
for each row execute function protect_a2a_interface_conformance_projection_c0b7();

-- B4/B5 current interface eligibility now consumes governed, unexpired C0-B7 conformance evidence.
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
       and a2a_interface_conformance_current_pass(i.tenant_id,i.interface_id)
       and i.circuit_state='CLOSED'
       and i.supported_extensions_json @> i.required_extensions_json
  )
$$;

-- Any pre-B7 PASS lacked governed conformance evidence and is therefore retired fail-closed.
update a2a_peer_interfaces
   set conformance_status='UNKNOWN',conformance_profile_id=null,conformance_run_id=null,conformance_valid_until=null,
       contract_version=contract_version+1,updated_at=now()
 where conformance_status<>'UNKNOWN' or conformance_profile_id is not null or conformance_run_id is not null or conformance_valid_until is not null;
update a2a_peer_provider_links l
   set status='SUSPENDED',updated_at=now()
 where l.status='ACTIVE' and not a2a_interface_current_contract_eligible(l.tenant_id,l.interface_id);

comment on table a2a_interface_conformance_profiles is
'C0-B7 governed Interface Conformance Profile. Baseline checks are mandatory and cannot be weakened by Admin; ACTIVE profile facts are immutable.';
comment on table a2a_interface_conformance_runs is
'C0-B7 platform-generated conformance run. Admin may request a run but cannot write PASS/FAIL evidence or outcome directly.';
comment on table a2a_interface_conformance_evidence is
'C0-B7 append-only platform-automated evidence. There is no Human Admin API to write evidence.';
comment on column a2a_peer_interfaces.conformance_status is
'C0-B7 compatibility projection of the latest governed run. Runtime eligibility uses a2a_interface_conformance_current_pass(), so expired/retired-profile PASS cannot remain eligible.';

insert into schema_contract_authority(contract_id,contract_family,authority_note,created_at,schema_version)
values('c0-b7-interface-conformance','C0_B7_A2A_INTERFACE_CONFORMANCE',
       'ADMIN_REQUESTS_RUN_ONLY; PLATFORM_AUTOMATED_EVIDENCE; BASELINE_PROFILE_CANNOT_BE_WEAKENED; PASS_REQUIRES_COMPLETED_UNEXPIRED_RUN_UNDER_ACTIVE_PROFILE; NO_DIRECT_CONFORMANCE_PASS_WRITE',now(),'V218')
on conflict(contract_id) do nothing;
