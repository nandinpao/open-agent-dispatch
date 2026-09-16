-- OpenDispatch v24 Phase 3: durable, secret-safe evidence when a primary security audit sink fails.
-- This is intentionally instance/global because failures can occur before authoritative Tenant resolution.
create table if not exists security_audit_delivery_failures (
  failure_id varchar(128) primary key,
  component varchar(160) not null,
  audit_type varchar(96) not null,
  tenant_id varchar(128),
  principal_id varchar(160),
  correlation_id varchar(160) not null,
  reason_code varchar(128) not null,
  exception_class varchar(256) not null,
  error_fingerprint varchar(64) not null,
  safe_message varchar(1000),
  occurred_at timestamptz not null,
  created_at timestamptz not null default now()
);
create index if not exists idx_security_audit_delivery_failures_correlation
  on security_audit_delivery_failures(correlation_id,occurred_at desc);
create index if not exists idx_security_audit_delivery_failures_component
  on security_audit_delivery_failures(component,occurred_at desc);
comment on table security_audit_delivery_failures is 'Fallback evidence for primary security audit sink failures. Never stores tokens, secrets, credentials, payloads, or authorization headers.';
