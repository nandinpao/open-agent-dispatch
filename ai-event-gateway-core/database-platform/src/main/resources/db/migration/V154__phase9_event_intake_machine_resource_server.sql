-- Phase 9: External Event Intake JWT cutover and machine resource authorization.
-- Human R3 is deliberately not involved. This migration adds only machine-resource audit evidence and canonical reason codes.

CREATE TABLE IF NOT EXISTS iam_machine_resource_access_events (
    event_id varchar(128) PRIMARY KEY,
    resource varchar(256) NOT NULL,
    http_method varchar(16) NOT NULL,
    cutover_mode varchar(32) NOT NULL CHECK (cutover_mode IN ('LEGACY_ONLY','SHADOW','DUAL_ACCEPT','JWT_REQUIRED')),
    auth_method varchar(32) NOT NULL,
    outcome varchar(16) NOT NULL CHECK (outcome IN ('ALLOW','DENY')),
    reason_code varchar(128) NOT NULL,
    tenant_id varchar(128),
    service_account_id varchar(128),
    credential_id varchar(128),
    jwt_id varchar(128),
    source_system varchar(160),
    source_ip varchar(128),
    correlation_id varchar(128) NOT NULL,
    occurred_at timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_machine_resource_access_time ON iam_machine_resource_access_events(occurred_at DESC);
CREATE INDEX IF NOT EXISTS ix_machine_resource_access_tenant ON iam_machine_resource_access_events(tenant_id,service_account_id,occurred_at DESC);
CREATE INDEX IF NOT EXISTS ix_machine_resource_access_cutover ON iam_machine_resource_access_events(resource,cutover_mode,auth_method,outcome,occurred_at DESC);

INSERT INTO reason_code_catalog(reason_code,http_status,category,retryable,message_template)
VALUES
 ('MACHINE_EVENT_INTAKE_AUTHENTICATION_REQUIRED',401,'AUTHENTICATION',false,'A machine Bearer access token is required for Event Intake.'),
 ('MACHINE_EVENT_INTAKE_TOKEN_INVALID',401,'AUTHENTICATION',false,'Machine Event Intake access token validation failed.'),
 ('MACHINE_EVENT_INTAKE_TOKEN_STALE',401,'AUTHENTICATION',false,'Machine Event Intake access token security epoch is stale.'),
 ('MACHINE_EVENT_INTAKE_SCOPE_DENIED',403,'AUTHORIZATION',false,'Machine token does not grant the Event Intake scope.'),
 ('MACHINE_EVENT_INTAKE_AUDIENCE_DENIED',403,'AUTHORIZATION',false,'Machine token audience is not valid for Event Intake.'),
 ('MACHINE_EVENT_INTAKE_API_PREFIX_DENIED',403,'AUTHORIZATION',false,'Machine token API boundary does not allow Event Intake.'),
 ('MACHINE_EVENT_INTAKE_CIDR_DENIED',403,'AUTHORIZATION',false,'Machine token CIDR boundary denied the caller.'),
 ('MACHINE_EVENT_INTAKE_RBAC_DENIED',403,'AUTHORIZATION',false,'Service Account has no current RBAC authority.'),
 ('MACHINE_TENANT_CONTEXT_MISMATCH',403,'AUTHORIZATION',false,'Event Tenant does not match the authenticated machine Tenant.'),
 ('MACHINE_SOURCE_SYSTEM_DENIED',403,'AUTHORIZATION',false,'Source System is outside the authenticated machine boundary.'),
 ('MACHINE_EVENT_INTAKE_RATE_LIMITED',429,'SECURITY',true,'Event Intake rate limit exceeded.'),
 ('MACHINE_EVENT_INTAKE_ALLOWED',200,'SECURITY',false,'Machine JWT Event Intake authorization succeeded.'),
 ('MACHINE_EVENT_INTAKE_LEGACY_ACCEPTED',200,'SECURITY',false,'Legacy Event Intake credential was accepted during cutover.'),
 ('MACHINE_EVENT_INTAKE_SHADOW_MATCH',200,'SECURITY',false,'Shadow JWT authorization matched the legacy Event Intake request.')
ON CONFLICT (reason_code) DO UPDATE SET
 http_status=excluded.http_status,category=excluded.category,retryable=excluded.retryable,
 message_template=excluded.message_template,active=true;
