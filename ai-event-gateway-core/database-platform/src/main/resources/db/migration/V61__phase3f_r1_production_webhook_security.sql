-- Phase 3F-R1: production Webhook machine identity, Tenant binding, per-Principal credential and replay protection.
create table if not exists integration_webhook_endpoints (
 tenant_id varchar(64) not null,
 endpoint_id varchar(128) not null,
 endpoint_token_hash char(64) not null,
 connection_id varchar(128) not null,
 principal_id varchar(128) not null,
 provider_type varchar(32) not null,
 status varchar(32) not null default 'DRAFT',
 signature_algorithm varchar(32) not null default 'HMAC_SHA256_V1',
 max_body_bytes integer not null default 1048576,
 rate_limit_per_minute integer not null default 120,
 replay_window_seconds bigint not null default 300,
 version bigint not null default 1,
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(),
 primary key(tenant_id,endpoint_id),
 unique(endpoint_token_hash),
 constraint uq_webhook_endpoint_connection_p3fr1 unique(tenant_id,endpoint_id,connection_id),
 constraint fk_webhook_endpoint_connection_p3fr1 foreign key(tenant_id,connection_id) references integration_connections(tenant_id,connection_id),
 constraint fk_webhook_endpoint_principal_p3fr1 foreign key(tenant_id,principal_id) references integration_principals(tenant_id,principal_id),
 constraint ck_webhook_endpoint_hash_p3fr1 check(endpoint_token_hash ~ '^[0-9a-f]{64}$'),
 constraint ck_webhook_endpoint_status_p3fr1 check(status in('DRAFT','ACTIVE','DISABLED','REVOKED')),
 constraint ck_webhook_endpoint_algorithm_p3fr1 check(signature_algorithm='HMAC_SHA256_V1'),
 constraint ck_webhook_endpoint_limits_p3fr1 check(max_body_bytes between 1024 and 10485760 and rate_limit_per_minute between 1 and 100000 and replay_window_seconds between 30 and 3600)
);
create index if not exists idx_webhook_endpoint_connection_p3fr1 on integration_webhook_endpoints(tenant_id,connection_id,status);
create table if not exists integration_webhook_nonce_reservations (
 tenant_id varchar(64) not null,
 connection_id varchar(128) not null,
 endpoint_id varchar(128) not null,
 nonce_hash char(64) not null,
 expires_at timestamptz not null,
 correlation_id varchar(128),
 created_at timestamptz not null default now(),
 primary key(tenant_id,connection_id,nonce_hash),
 constraint fk_webhook_nonce_endpoint_p3fr1 foreign key(tenant_id,endpoint_id,connection_id) references integration_webhook_endpoints(tenant_id,endpoint_id,connection_id),
 constraint ck_webhook_nonce_hash_p3fr1 check(nonce_hash ~ '^[0-9a-f]{64}$')
);
create index if not exists idx_webhook_nonce_expiry_p3fr1 on integration_webhook_nonce_reservations(expires_at);
create or replace function phase3fr1_reject_endpoint_reassignment() returns trigger language plpgsql as $$
begin
 if new.tenant_id<>old.tenant_id or new.endpoint_id<>old.endpoint_id or new.endpoint_token_hash<>old.endpoint_token_hash or new.connection_id<>old.connection_id or new.principal_id<>old.principal_id or new.provider_type<>old.provider_type then raise exception 'Phase 3F-R1 Webhook Endpoint identity is immutable'; end if;
 return new;
end $$;
drop trigger if exists trg_phase3fr1_endpoint_identity on integration_webhook_endpoints;
create trigger trg_phase3fr1_endpoint_identity before update on integration_webhook_endpoints for each row execute function phase3fr1_reject_endpoint_reassignment();
