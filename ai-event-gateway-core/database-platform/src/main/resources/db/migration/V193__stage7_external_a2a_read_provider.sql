-- Stage 7 — External A2A F0/F1 READ Provider.
-- A2A v1.0 HTTP+JSON, provider-neutral assignment, synchronous terminal READ only.

create table if not exists a2a_peer_registrations (
  tenant_id varchar(64) not null,
  peer_id varchar(160) not null,
  display_name varchar(255) not null,
  agent_card_url varchar(1024) not null,
  trust_status varchar(32) not null default 'PROPOSED',
  status varchar(24) not null default 'DRAFT',
  last_card_refresh_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id,peer_id),
  constraint a2a_peer_trust_status_check check (trust_status in ('PROPOSED','CARD_VERIFIED','MANUAL_TRUSTED','REVOKED')),
  constraint a2a_peer_status_check check (status in ('DRAFT','ACTIVE','SUSPENDED','RETIRED'))
);

create table if not exists a2a_agent_card_snapshots (
  tenant_id varchar(64) not null,
  snapshot_id varchar(180) not null,
  peer_id varchar(160) not null,
  card_json jsonb not null,
  card_sha256 varchar(128) not null,
  protocol_version varchar(40),
  fetched_at timestamptz not null default now(),
  primary key (tenant_id,snapshot_id),
  constraint fk_a2a_card_peer foreign key (tenant_id,peer_id) references a2a_peer_registrations(tenant_id,peer_id),
  constraint a2a_card_json_object check (jsonb_typeof(card_json)='object')
);
create index if not exists idx_a2a_agent_card_snapshots_peer on a2a_agent_card_snapshots(tenant_id,peer_id,fetched_at desc);

create table if not exists a2a_peer_interfaces (
  tenant_id varchar(64) not null,
  interface_id varchar(180) not null,
  peer_id varchar(160) not null,
  url varchar(1024) not null,
  protocol_binding varchar(40) not null,
  protocol_version varchar(40) not null,
  interface_tenant varchar(255),
  streaming_supported boolean not null default false,
  push_notifications_supported boolean not null default false,
  status varchar(24) not null default 'PROPOSED',
  trust_status varchar(32) not null default 'PROPOSED',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id,interface_id),
  unique (tenant_id,peer_id,url,protocol_binding,protocol_version),
  constraint fk_a2a_interface_peer foreign key (tenant_id,peer_id) references a2a_peer_registrations(tenant_id,peer_id),
  constraint a2a_interface_binding_check check (protocol_binding in ('HTTP+JSON','JSONRPC')),
  constraint a2a_interface_version_check check (protocol_version='1.0'),
  constraint a2a_interface_status_check check (status in ('PROPOSED','APPROVED','SUSPENDED','RETIRED')),
  constraint a2a_interface_trust_check check (trust_status in ('PROPOSED','CARD_VERIFIED','MANUAL_TRUSTED','REVOKED'))
);

create table if not exists a2a_peer_provider_links (
  tenant_id varchar(64) not null,
  provider_id varchar(160) not null,
  peer_id varchar(160) not null,
  interface_id varchar(180) not null,
  status varchar(24) not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id,provider_id),
  constraint fk_a2a_provider_link_provider foreign key (tenant_id,provider_id) references capability_providers(tenant_id,provider_id),
  constraint fk_a2a_provider_link_peer foreign key (tenant_id,peer_id) references a2a_peer_registrations(tenant_id,peer_id),
  constraint fk_a2a_provider_link_interface foreign key (tenant_id,interface_id) references a2a_peer_interfaces(tenant_id,interface_id),
  constraint a2a_provider_link_status_check check (status in ('ACTIVE','SUSPENDED','RETIRED'))
);

create table if not exists a2a_remote_read_executions (
  tenant_id varchar(64) not null,
  execution_id varchar(180) not null,
  delegation_id varchar(160) not null,
  task_id varchar(128) not null,
  assignment_id varchar(128) not null,
  provider_id varchar(160) not null,
  peer_id varchar(160) not null,
  interface_id varchar(180) not null,
  endpoint_url varchar(1024) not null,
  interface_tenant varchar(255),
  request_message_id varchar(180) not null,
  request_json jsonb not null,
  remote_task_id varchar(255),
  remote_context_id varchar(255),
  remote_state varchar(64),
  status varchar(32) not null default 'QUEUED',
  attempts int not null default 0,
  max_attempts int not null default 4,
  next_attempt_at timestamptz,
  claimed_by varchar(160),
  claim_until timestamptz,
  http_status int,
  response_json jsonb,
  error_code varchar(128),
  error_message text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  completed_at timestamptz,
  primary key (tenant_id,execution_id),
  unique (tenant_id,delegation_id),
  constraint fk_a2a_remote_execution_delegation foreign key (tenant_id,delegation_id) references capability_delegation_requests(tenant_id,delegation_id) on delete cascade,
  constraint a2a_remote_execution_status_check check (status in ('QUEUED','PROCESSING','WAITING_REMOTE','SUCCEEDED','FAILED','DEAD_LETTER')),
  constraint a2a_remote_execution_attempts_check check (attempts>=0 and max_attempts between 1 and 20),
  constraint a2a_remote_execution_request_object check (jsonb_typeof(request_json)='object')
);
create index if not exists idx_a2a_remote_read_executions_due on a2a_remote_read_executions(tenant_id,status,next_attempt_at,created_at)
 where status in ('QUEUED','FAILED','WAITING_REMOTE');

alter table a2a_peer_registrations enable row level security;
alter table a2a_agent_card_snapshots enable row level security;
alter table a2a_peer_interfaces enable row level security;
alter table a2a_peer_provider_links enable row level security;
alter table a2a_remote_read_executions enable row level security;
do $$ declare t text; begin foreach t in array array['a2a_peer_registrations','a2a_agent_card_snapshots','a2a_peer_interfaces','a2a_peer_provider_links','a2a_remote_read_executions'] loop execute format('drop policy if exists tenant_isolation on %I',t); execute format('create policy tenant_isolation on %I using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id())',t); end loop; end $$;

insert into schema_contract_authority(contract_id,contract_family,authority_note,created_at,schema_version)
values('stage7-a2a-read-provider-v1','STAGE7_EXTERNAL_A2A_READ_PROVIDER','A2A_V1_HTTP_JSON_READ_ONLY; CALLER_CANNOT_SELECT_PEER; REMOTE_PROVIDER_USES_PROVIDER_NEUTRAL_ASSIGNMENT; NO_AGENT_DISPATCH_REQUEST; NONTERMINAL_REMOTE_TASK_REQUIRES_STAGE8','now'::timestamptz,'V193')
on conflict(contract_id) do nothing;
