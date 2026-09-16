-- PC-S4 External A2A Transport Security Closure
-- Runtime transport bounds, connection-level DNS rebind enforcement policy and opaque PUSH callback routing.

alter table a2a_outbound_destination_policies
  add column if not exists max_request_bytes integer not null default 1048576,
  add column if not exists max_response_bytes integer not null default 4194304,
  add column if not exists max_sse_event_bytes integer not null default 262144,
  add column if not exists max_sse_stream_bytes integer not null default 8388608,
  add column if not exists max_sse_events integer not null default 2048,
  add column if not exists sse_idle_timeout_ms integer not null default 30000,
  add column if not exists sse_overall_timeout_ms integer not null default 300000;

alter table a2a_outbound_destination_policies
  drop constraint if exists ck_a2a_outbound_max_request_pc_s4,
  drop constraint if exists ck_a2a_outbound_max_response_pc_s4,
  drop constraint if exists ck_a2a_outbound_max_sse_event_pc_s4,
  drop constraint if exists ck_a2a_outbound_max_sse_stream_pc_s4,
  drop constraint if exists ck_a2a_outbound_max_sse_events_pc_s4,
  drop constraint if exists ck_a2a_outbound_sse_idle_pc_s4,
  drop constraint if exists ck_a2a_outbound_sse_overall_pc_s4;

alter table a2a_outbound_destination_policies
  add constraint ck_a2a_outbound_max_request_pc_s4 check (max_request_bytes between 1024 and 16777216),
  add constraint ck_a2a_outbound_max_response_pc_s4 check (max_response_bytes between 1024 and 33554432),
  add constraint ck_a2a_outbound_max_sse_event_pc_s4 check (max_sse_event_bytes between 1024 and 4194304),
  add constraint ck_a2a_outbound_max_sse_stream_pc_s4 check (max_sse_stream_bytes between 4096 and 67108864),
  add constraint ck_a2a_outbound_max_sse_events_pc_s4 check (max_sse_events between 1 and 100000),
  add constraint ck_a2a_outbound_sse_idle_pc_s4 check (sse_idle_timeout_ms between 1000 and 300000),
  add constraint ck_a2a_outbound_sse_overall_pc_s4 check (sse_overall_timeout_ms between 1000 and 3600000);

-- PC-S4 ships connection-level DNS/IP pinning for the dedicated External A2A transport.
-- Existing policies are strengthened rather than silently retaining the pre-S4 governance-only declaration.
update a2a_outbound_destination_policies
   set dns_rebinding_protection=true,
       runtime_status=case when max_redirects=0 then 'RUNTIME_READY' else 'NOT_RUNTIME_SUPPORTED' end,
       runtime_status_reason=case when max_redirects=0
           then 'PC-S4 secure transport: validated-address socket connect; bounded HTTP/SSE; TLS hostname verification; optional SPKI pins'
           else 'REDIRECTS_NOT_RUNTIME_SUPPORTED' end,
       updated_at=now();

create table if not exists a2a_push_callback_routes (
  callback_handle varchar(160) primary key,
  tenant_id varchar(64) not null,
  tracking_id varchar(180) not null,
  token_hash varchar(128) not null,
  route_status varchar(24) not null default 'ACTIVE',
  expires_at timestamptz,
  request_window_started_at timestamptz,
  request_count integer not null default 0,
  auth_failure_count integer not null default 0,
  locked_until timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_a2a_push_route_status_pc_s4 check (route_status in ('ACTIVE','REVOKED','EXPIRED')),
  constraint ck_a2a_push_route_request_count_pc_s4 check (request_count>=0),
  constraint ck_a2a_push_route_auth_failures_pc_s4 check (auth_failure_count>=0),
  unique(tenant_id,tracking_id,callback_handle)
);
create index if not exists idx_a2a_push_callback_tracking_pc_s4
  on a2a_push_callback_routes(tenant_id,tracking_id,route_status);

comment on table a2a_push_callback_routes is
'PC-S4 narrow pre-tenant PUSH callback authentication authority. Intentionally not RLS-protected because callback_handle + bearer authentication must resolve tenant before tenant context is bound. Raw bearer credentials are never stored.';
comment on column a2a_push_callback_routes.callback_handle is
'High-entropy opaque callback routing identifier. It contains no tenant or tracking identity.';
comment on column a2a_push_callback_routes.token_hash is
'SHA-256 callback bearer hash; raw callback bearer MUST NOT be persisted.';

-- Force non-terminal work to negotiate an opaque PC-S4 callback route. Old tenant/tracking path callbacks are retired.
update a2a_remote_tracking_leases
   set push_config_id=null,push_token_hash=null,updated_at=now()
 where terminal_outcome is null and status not in ('TERMINAL','FAILED');

insert into schema_contract_authority(contract_id,contract_family,authority_note,created_at,schema_version)
values('pc-s4-external-a2a-transport-security','PC_S4_EXTERNAL_A2A_TRANSPORT_SECURITY',
       'DEDICATED_A2A_SECURE_TRANSPORT; DNS_RESOLVE_VALIDATE_CONNECT_SAME_ADDRESS; TLS_HOSTNAME_SNI; OPTIONAL_SPKI_PIN; BOUNDED_HTTP_SSE; OPAQUE_PUSH_CALLBACK_PRETENANT_AUTH; LEGACY_TENANT_PATH_PUSH_RETIRED',now(),'V223')
on conflict(contract_id) do nothing;
