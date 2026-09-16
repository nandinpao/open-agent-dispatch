-- Phase 2: Provider / Capability Binding Registry (WHO CAN).
--
-- This phase answers only WHO CAN provide a Canonical Capability. It intentionally
-- does not authorize execution (WHO MAY), rank/select a provider (WHO SHOULD), or
-- choose Netty/A2A/MCP/HTTP execution transport (HOW).

create table if not exists capability_providers (
  tenant_id varchar(64) not null,
  provider_id varchar(160) not null,
  provider_type varchar(40) not null,
  display_name varchar(255) not null,
  provider_ref varchar(255) not null,
  registration_source varchar(40) not null default 'MANUAL',
  catalog_status varchar(32) not null default 'REGISTERED',
  metadata_json jsonb not null default '{}'::jsonb,
  observed_at timestamptz,
  last_verified_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, provider_id),
  unique (tenant_id, provider_type, provider_ref),
  constraint capability_provider_type_check
    check (provider_type in ('MANAGED_AGENT','REMOTE_A2A_AGENT','MCP_TOOL','INTERNAL_SERVICE')),
  constraint capability_provider_registration_source_check
    check (registration_source in ('MANUAL','MANAGED_REGISTRY','AGENT_CARD','MCP_CATALOG','INTERNAL_CATALOG')),
  constraint capability_provider_status_check
    check (catalog_status in ('OBSERVED','REGISTERED','DISABLED','RETIRED')),
  constraint capability_provider_metadata_object_check
    check (jsonb_typeof(metadata_json)='object')
);

create index if not exists idx_capability_providers_lookup
  on capability_providers(tenant_id,provider_type,catalog_status,display_name);

comment on table capability_providers is
  'Phase 2 provider identity registry for WHO CAN discovery. A provider record is not execution authorization, routing selection, or transport configuration.';
comment on column capability_providers.provider_ref is
  'Opaque identity/reference in the provider-specific registry. It must not contain transport endpoint or credentials.';
comment on column capability_providers.provider_type is
  'Provider class only. Execution adapter selection belongs to a later HOW phase.';

create table if not exists capability_bindings (
  tenant_id varchar(64) not null,
  binding_id varchar(160) not null,
  capability_code varchar(160) not null,
  provider_id varchar(160) not null,
  supported_operations_json jsonb not null default '[]'::jsonb,
  trust_status varchar(32) not null default 'DISCOVERED',
  source_revision varchar(160),
  verification_method varchar(80),
  verification_evidence_ref varchar(512),
  observed_at timestamptz,
  verified_at timestamptz,
  approved_at timestamptz,
  stale_after timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, binding_id),
  unique (tenant_id, capability_code, provider_id),
  constraint fk_capability_binding_definition
    foreign key (tenant_id,capability_code)
    references capability_definitions(tenant_id,capability_code)
    on update cascade on delete restrict,
  constraint fk_capability_binding_provider
    foreign key (tenant_id,provider_id)
    references capability_providers(tenant_id,provider_id)
    on update cascade on delete restrict,
  constraint capability_binding_operations_array_check
    check (jsonb_typeof(supported_operations_json)='array'),
  constraint capability_binding_trust_status_check
    check (trust_status in ('DISCOVERED','PROPOSED','VERIFIED','APPROVED','SUSPENDED','REVOKED','STALE'))
);

create index if not exists idx_capability_bindings_capability
  on capability_bindings(tenant_id,capability_code,trust_status,provider_id);
create index if not exists idx_capability_bindings_provider
  on capability_bindings(tenant_id,provider_id,trust_status,capability_code);

comment on table capability_bindings is
  'Phase 2 mapping from Canonical Capability to a provider that CAN provide it. APPROVED means catalog trust approval only; it is not WHO MAY execution authorization and is not workload eligibility.';
comment on column capability_bindings.trust_status is
  'Discovery/trust lifecycle only: DISCOVERED, PROPOSED, VERIFIED, APPROVED, SUSPENDED, REVOKED, STALE.';

create table if not exists capability_binding_trust_events (
  tenant_id varchar(64) not null,
  event_id varchar(160) not null,
  binding_id varchar(160) not null,
  from_status varchar(32),
  to_status varchar(32) not null,
  reason varchar(512),
  actor_ref varchar(255),
  occurred_at timestamptz not null default now(),
  primary key (tenant_id,event_id),
  constraint fk_capability_binding_trust_event
    foreign key (tenant_id,binding_id)
    references capability_bindings(tenant_id,binding_id)
    on delete cascade
);

create index if not exists idx_capability_binding_trust_events
  on capability_binding_trust_events(tenant_id,binding_id,occurred_at desc);

comment on table capability_binding_trust_events is
  'Append-only evidence for Capability Binding trust-state transitions. It does not grant runtime execution authorization.';

alter table capability_providers enable row level security;
drop policy if exists tenant_isolation on capability_providers;
create policy tenant_isolation on capability_providers
  using (tenant_id=iam_current_tenant_id())
  with check (tenant_id=iam_current_tenant_id());

alter table capability_bindings enable row level security;
drop policy if exists tenant_isolation on capability_bindings;
create policy tenant_isolation on capability_bindings
  using (tenant_id=iam_current_tenant_id())
  with check (tenant_id=iam_current_tenant_id());

alter table capability_binding_trust_events enable row level security;
drop policy if exists tenant_isolation on capability_binding_trust_events;
create policy tenant_isolation on capability_binding_trust_events
  using (tenant_id=iam_current_tenant_id())
  with check (tenant_id=iam_current_tenant_id());

create or replace function prevent_capability_binding_trust_event_mutation() returns trigger language plpgsql as $$
begin raise exception 'CAPABILITY_BINDING_TRUST_EVENT_IS_APPEND_ONLY'; end $$;

drop trigger if exists trg_capability_binding_trust_event_immutable on capability_binding_trust_events;
create trigger trg_capability_binding_trust_event_immutable
before update or delete on capability_binding_trust_events
for each row execute function prevent_capability_binding_trust_event_mutation();

insert into capability_architecture_evidence(evidence_id,architecture_version,principle,effective_at,created_by)
values(
  'phase2-provider-binding-authority',
  'PHASE2_PROVIDER_CAPABILITY_BINDING_REGISTRY',
  'WHO_CAN_IS_PROVIDER_DISCOVERY_ONLY_AND_MUST_NOT_AUTHORIZE_RANK_SELECT_OR_CHOOSE_EXECUTION_PROTOCOL',
  now(),
  'V177')
on conflict(evidence_id) do nothing;
