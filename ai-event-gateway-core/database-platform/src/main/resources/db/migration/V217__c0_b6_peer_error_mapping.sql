-- C0-B6 — External A2A F0 Peer Error Mapping.
-- Remote peer error codes may be mapped to canonical OpenDispatch error classes, but protected security classes can never be weakened.

create or replace function a2a_peer_error_protected_class(p_class text)
returns boolean language sql immutable as $$
  select upper(coalesce(p_class,'')) in ('AUTHENTICATION','AUTHORIZATION','TRUST','RESIDENCY','SECURITY')
$$;

create or replace function a2a_peer_error_baseline_class(p_http_status integer,p_remote_error_code text)
returns text language sql immutable as $$
  select case
    when p_http_status=401 then 'AUTHENTICATION'
    when p_http_status=403 then 'AUTHORIZATION'
    when upper(coalesce(p_remote_error_code,'')) ~ '^AUTHENTICATION([_:.\-]|$)' then 'AUTHENTICATION'
    when upper(coalesce(p_remote_error_code,'')) ~ '^AUTHORIZATION([_:.\-]|$)' then 'AUTHORIZATION'
    when upper(coalesce(p_remote_error_code,'')) ~ '^TRUST([_:.\-]|$)' then 'TRUST'
    when upper(coalesce(p_remote_error_code,'')) ~ '^RESIDENCY([_:.\-]|$)' then 'RESIDENCY'
    when upper(coalesce(p_remote_error_code,'')) ~ '^SECURITY([_:.\-]|$)' then 'SECURITY'
    when p_http_status=429 then 'THROTTLED'
    when p_http_status in (408,425) then 'TEMPORARY'
    when p_http_status=404 then 'NOT_FOUND'
    when p_http_status=409 then 'CONFLICT'
    when p_http_status in (400,405,406,415,422) then 'INVALID_REQUEST'
    when p_http_status is not null and p_http_status between 500 and 599 then 'UNAVAILABLE'
    when p_http_status is not null and p_http_status between 400 and 499 then 'REMOTE_FAILURE'
    when nullif(btrim(coalesce(p_remote_error_code,'')),'') is not null then 'REMOTE_FAILURE'
    else 'UNKNOWN'
  end
$$;

create or replace function a2a_peer_error_disposition(p_class text)
returns text language sql immutable as $$
  select case upper(coalesce(p_class,''))
    when 'AUTHENTICATION' then 'BLOCK'
    when 'AUTHORIZATION' then 'BLOCK'
    when 'TRUST' then 'BLOCK'
    when 'RESIDENCY' then 'BLOCK'
    when 'SECURITY' then 'BLOCK'
    when 'THROTTLED' then 'RETRY'
    when 'TEMPORARY' then 'RETRY'
    when 'UNAVAILABLE' then 'RETRY'
    when 'NOT_FOUND' then 'RECONCILE'
    when 'CONFLICT' then 'RECONCILE'
    else 'TERMINAL'
  end
$$;

create table if not exists a2a_peer_error_mapping_overrides (
  tenant_id varchar(64) not null,
  override_id varchar(180) not null,
  peer_id varchar(160) not null,
  interface_id varchar(180),
  operation_scope varchar(32) not null default 'ANY',
  remote_error_code varchar(180) not null,
  http_status integer,
  canonical_error_class varchar(40) not null,
  canonical_error_code varchar(180) not null,
  reason text not null,
  override_status varchar(24) not null default 'DRAFT',
  created_by varchar(160) not null,
  created_at timestamptz not null default now(),
  activated_at timestamptz,
  retired_at timestamptz,
  updated_at timestamptz not null default now(),
  primary key (tenant_id,override_id),
  constraint fk_a2a_peer_error_override_peer_c0b6 foreign key (tenant_id,peer_id) references a2a_peer_registrations(tenant_id,peer_id),
  constraint fk_a2a_peer_error_override_interface_c0b6 foreign key (tenant_id,interface_id) references a2a_peer_interfaces(tenant_id,interface_id),
  constraint ck_a2a_peer_error_override_operation_c0b6 check (operation_scope in ('ANY','SEND_MESSAGE','GET_TASK','SUBSCRIBE','PUSH_CONFIG','CANCEL','TASK_TERMINAL')),
  constraint ck_a2a_peer_error_override_http_c0b6 check (http_status is null or http_status between 100 and 599),
  constraint ck_a2a_peer_error_override_class_c0b6 check (canonical_error_class in ('AUTHENTICATION','AUTHORIZATION','TRUST','RESIDENCY','SECURITY','THROTTLED','TEMPORARY','UNAVAILABLE','NOT_FOUND','CONFLICT','INVALID_REQUEST','PROTOCOL','REMOTE_FAILURE','UNKNOWN')),
  constraint ck_a2a_peer_error_override_code_c0b6 check (canonical_error_code ~ '^[A-Z0-9_]{3,180}$'),
  constraint ck_a2a_peer_error_override_status_c0b6 check (override_status in ('DRAFT','ACTIVE','RETIRED')),
  constraint ck_a2a_peer_error_override_lifecycle_c0b6 check (
    (override_status='DRAFT' and activated_at is null and retired_at is null)
    or (override_status='ACTIVE' and activated_at is not null and retired_at is null)
    or (override_status='RETIRED' and activated_at is not null and retired_at is not null)
  )
);
create unique index if not exists uq_a2a_peer_error_override_active_c0b6
  on a2a_peer_error_mapping_overrides(tenant_id,peer_id,coalesce(interface_id,''),operation_scope,remote_error_code,coalesce(http_status,0))
  where override_status='ACTIVE';
create index if not exists idx_a2a_peer_error_override_lookup_c0b6
  on a2a_peer_error_mapping_overrides(tenant_id,peer_id,interface_id,operation_scope,remote_error_code,http_status,override_status);

create or replace function a2a_peer_error_override_guard_c0b6()
returns trigger language plpgsql as $$
declare v_baseline text;
begin
  v_baseline := a2a_peer_error_baseline_class(new.http_status,new.remote_error_code);
  if a2a_peer_error_protected_class(v_baseline) and new.canonical_error_class <> v_baseline then
    raise exception 'A2A_PROTECTED_ERROR_CLASS_REMAP_FORBIDDEN:%->%',v_baseline,new.canonical_error_class;
  end if;
  if tg_op='UPDATE' and old.override_status<>'DRAFT' and (
       new.peer_id is distinct from old.peer_id
    or new.interface_id is distinct from old.interface_id
    or new.operation_scope is distinct from old.operation_scope
    or new.remote_error_code is distinct from old.remote_error_code
    or new.http_status is distinct from old.http_status
    or new.canonical_error_class is distinct from old.canonical_error_class
    or new.canonical_error_code is distinct from old.canonical_error_code
    or new.reason is distinct from old.reason) then
    raise exception 'A2A_ACTIVE_ERROR_MAPPING_FACT_IMMUTABLE';
  end if;
  if tg_op='UPDATE' and old.override_status='RETIRED' then
    raise exception 'A2A_RETIRED_ERROR_MAPPING_IMMUTABLE';
  end if;
  return new;
end $$;
drop trigger if exists trg_a2a_peer_error_override_guard_c0b6 on a2a_peer_error_mapping_overrides;
create trigger trg_a2a_peer_error_override_guard_c0b6 before insert or update on a2a_peer_error_mapping_overrides
for each row execute function a2a_peer_error_override_guard_c0b6();

create or replace function a2a_peer_error_resolve(
  p_tenant text,p_peer text,p_interface text,p_operation text,p_http_status integer,p_remote_error_code text)
returns table(
  baseline_error_class text,resolved_error_class text,canonical_error_code text,error_disposition text,
  mapping_source text,override_id text,protected_baseline boolean)
language sql stable as $$
  with baseline as (
    select a2a_peer_error_baseline_class(p_http_status,p_remote_error_code) as error_class
  ), candidate as (
    select o.*
      from a2a_peer_error_mapping_overrides o, baseline b
     where o.tenant_id=p_tenant and o.peer_id=p_peer and o.override_status='ACTIVE'
       and o.remote_error_code=upper(btrim(coalesce(p_remote_error_code,'')))
       and (o.interface_id is null or o.interface_id=p_interface)
       and (o.operation_scope='ANY' or o.operation_scope=upper(coalesce(p_operation,'')))
       and (o.http_status is null or o.http_status=p_http_status)
       and (not a2a_peer_error_protected_class(b.error_class) or o.canonical_error_class=b.error_class)
     order by (o.interface_id is not null) desc,(o.operation_scope<>'ANY') desc,(o.http_status is not null) desc,o.activated_at desc,o.override_id
     limit 1
  ), resolved as (
    select b.error_class as baseline_class,
           coalesce(c.canonical_error_class,b.error_class) as resolved_class,
           coalesce(c.canonical_error_code,'A2A_REMOTE_'||b.error_class) as canonical_code,
           case when c.override_id is null then 'BASELINE' else 'PEER_OVERRIDE' end as source,
           c.override_id
      from baseline b left join candidate c on true
  )
  select baseline_class,resolved_class,canonical_code,a2a_peer_error_disposition(resolved_class),source,override_id,a2a_peer_error_protected_class(baseline_class)
    from resolved
$$;

create table if not exists a2a_peer_error_resolution_evidence (
  tenant_id varchar(64) not null,
  evidence_id varchar(180) not null,
  execution_id varchar(180),
  tracking_id varchar(180),
  peer_id varchar(160) not null,
  interface_id varchar(180) not null,
  operation_scope varchar(32) not null,
  http_status integer,
  remote_error_code varchar(180),
  remote_error_message text,
  baseline_error_class varchar(40) not null,
  resolved_error_class varchar(40) not null,
  canonical_error_code varchar(180) not null,
  error_disposition varchar(24) not null,
  mapping_source varchar(24) not null,
  override_id varchar(180),
  protected_baseline boolean not null,
  observed_at timestamptz not null default now(),
  primary key (tenant_id,evidence_id),
  constraint fk_a2a_peer_error_evidence_peer_c0b6 foreign key (tenant_id,peer_id) references a2a_peer_registrations(tenant_id,peer_id),
  constraint fk_a2a_peer_error_evidence_interface_c0b6 foreign key (tenant_id,interface_id) references a2a_peer_interfaces(tenant_id,interface_id),
  constraint ck_a2a_peer_error_evidence_disposition_c0b6 check (error_disposition in ('RETRY','RECONCILE','BLOCK','TERMINAL')),
  constraint ck_a2a_peer_error_evidence_source_c0b6 check (mapping_source in ('BASELINE','PEER_OVERRIDE'))
);
create index if not exists idx_a2a_peer_error_resolution_execution_c0b6 on a2a_peer_error_resolution_evidence(tenant_id,execution_id,observed_at desc);
create index if not exists idx_a2a_peer_error_resolution_tracking_c0b6 on a2a_peer_error_resolution_evidence(tenant_id,tracking_id,observed_at desc);

create or replace function prevent_a2a_peer_error_resolution_evidence_mutation_c0b6()
returns trigger language plpgsql as $$ begin raise exception 'A2A_PEER_ERROR_RESOLUTION_EVIDENCE_APPEND_ONLY'; end $$;
drop trigger if exists trg_a2a_peer_error_resolution_evidence_immutable_c0b6 on a2a_peer_error_resolution_evidence;
create trigger trg_a2a_peer_error_resolution_evidence_immutable_c0b6 before update or delete on a2a_peer_error_resolution_evidence
for each row execute function prevent_a2a_peer_error_resolution_evidence_mutation_c0b6();

alter table a2a_peer_error_mapping_overrides enable row level security;
alter table a2a_peer_error_resolution_evidence enable row level security;
drop policy if exists tenant_isolation on a2a_peer_error_mapping_overrides;
create policy tenant_isolation on a2a_peer_error_mapping_overrides using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id());
drop policy if exists tenant_isolation on a2a_peer_error_resolution_evidence;
create policy tenant_isolation on a2a_peer_error_resolution_evidence using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id());

-- Persist the canonical mapping decision on the remote execution/tracking record for operational diagnosis.
alter table a2a_remote_read_executions
  add column if not exists remote_error_code varchar(180),
  add column if not exists remote_error_message text,
  add column if not exists canonical_error_class varchar(40),
  add column if not exists error_disposition varchar(24),
  add column if not exists error_mapping_source varchar(24),
  add column if not exists error_mapping_override_id varchar(180);
alter table a2a_remote_read_executions drop constraint if exists a2a_remote_execution_status_check;
alter table a2a_remote_read_executions add constraint a2a_remote_execution_status_check check (status in ('QUEUED','PROCESSING','WAITING_REMOTE','SUCCEEDED','FAILED','BLOCKED','DEAD_LETTER'));
alter table a2a_remote_read_executions drop constraint if exists ck_a2a_remote_execution_error_disposition_c0b6;
alter table a2a_remote_read_executions add constraint ck_a2a_remote_execution_error_disposition_c0b6 check (error_disposition is null or error_disposition in ('RETRY','RECONCILE','BLOCK','TERMINAL'));

alter table a2a_remote_tracking_leases
  add column if not exists last_remote_error_code varchar(180),
  add column if not exists last_error_class varchar(40),
  add column if not exists last_error_disposition varchar(24),
  add column if not exists last_error_mapping_source varchar(24),
  add column if not exists last_error_mapping_override_id varchar(180);
alter table a2a_remote_tracking_leases drop constraint if exists ck_a2a_tracking_error_disposition_c0b6;
alter table a2a_remote_tracking_leases add constraint ck_a2a_tracking_error_disposition_c0b6 check (last_error_disposition is null or last_error_disposition in ('RETRY','RECONCILE','BLOCK','TERMINAL'));

comment on table a2a_peer_error_mapping_overrides is 'C0-B6 peer-specific exact error-code mapping. Protected baseline classes cannot be weakened by an override.';
comment on table a2a_peer_error_resolution_evidence is 'Append-only C0-B6 evidence of baseline/override/canonical error resolution used by External A2A runtime failure semantics.';

insert into schema_contract_authority(contract_id,contract_family,authority_note,created_at,schema_version)
values('c0-b6-peer-error-mapping','C0_B6_PEER_ERROR_MAPPING','BASELINE_CLASSIFIER; EXACT_PEER_OVERRIDE; PROTECTED_AUTHENTICATION_AUTHORIZATION_TRUST_RESIDENCY_SECURITY_NEVER_WEAKENED; CANONICAL_DISPOSITION; APPEND_ONLY_RESOLUTION_EVIDENCE',now(),'V217')
on conflict(contract_id) do nothing;
