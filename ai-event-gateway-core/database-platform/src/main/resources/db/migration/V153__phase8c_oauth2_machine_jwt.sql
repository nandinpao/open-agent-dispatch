-- Phase 8C: OAuth2 client_credentials + short-lived machine JWT/JWK authority.
-- Instance-level bootstrap tables deliberately do not use Tenant RLS; private signing material is AES-GCM protected by the application before persistence.

CREATE TABLE IF NOT EXISTS iam_machine_signing_keys (
    key_id varchar(128) PRIMARY KEY,
    algorithm varchar(16) NOT NULL CHECK (algorithm = 'RS256'),
    public_key_der_base64 text NOT NULL,
    protected_private_key text NOT NULL CHECK (length(protected_private_key) > 64),
    protection_key_id varchar(128) NOT NULL,
    status varchar(24) NOT NULL CHECK (status IN ('ACTIVE','VERIFY_ONLY','RETIRED')),
    activated_at timestamptz NOT NULL,
    rotate_after timestamptz NOT NULL,
    verify_until timestamptz NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 1 CHECK (version >= 1),
    CONSTRAINT ck_machine_signing_key_rotation CHECK (rotate_after > activated_at),
    CONSTRAINT ck_machine_signing_key_verify_window CHECK (
        (status='ACTIVE' AND verify_until IS NULL) OR
        (status='VERIFY_ONLY' AND verify_until IS NOT NULL) OR
        status='RETIRED'
    )
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_machine_signing_key_single_active ON iam_machine_signing_keys ((1)) WHERE status='ACTIVE';
CREATE INDEX IF NOT EXISTS ix_machine_signing_key_publishable ON iam_machine_signing_keys(status,verify_until,activated_at DESC);

CREATE OR REPLACE FUNCTION phase8c_activate_machine_signing_key(
    p_key_id varchar,
    p_algorithm varchar,
    p_public_key_der_base64 text,
    p_protected_private_key text,
    p_protection_key_id varchar,
    p_activated_at timestamptz,
    p_rotate_after timestamptz,
    p_at timestamptz,
    p_previous_verify_until timestamptz
) RETURNS SETOF iam_machine_signing_keys
LANGUAGE plpgsql
AS $$
DECLARE
    v_active iam_machine_signing_keys%ROWTYPE;
BEGIN
    PERFORM pg_advisory_xact_lock(hashtextextended('opendispatch:iam:machine-signing-key', 0));

    UPDATE iam_machine_signing_keys
       SET status='RETIRED',updated_at=p_at,version=version+1
     WHERE status='VERIFY_ONLY' AND verify_until <= p_at;

    SELECT * INTO v_active FROM iam_machine_signing_keys WHERE status='ACTIVE' FOR UPDATE;
    IF FOUND AND v_active.rotate_after > p_at THEN
        RETURN NEXT v_active;
        RETURN;
    END IF;

    IF FOUND THEN
        UPDATE iam_machine_signing_keys
           SET status='VERIFY_ONLY',verify_until=p_previous_verify_until,updated_at=p_at,version=version+1
         WHERE key_id=v_active.key_id;
    END IF;

    INSERT INTO iam_machine_signing_keys(
        key_id,algorithm,public_key_der_base64,protected_private_key,protection_key_id,status,
        activated_at,rotate_after,verify_until,created_at,updated_at,version)
    VALUES(
        p_key_id,p_algorithm,p_public_key_der_base64,p_protected_private_key,p_protection_key_id,'ACTIVE',
        p_activated_at,p_rotate_after,NULL,p_at,p_at,1);

    RETURN QUERY SELECT * FROM iam_machine_signing_keys WHERE key_id=p_key_id;
END $$;

CREATE TABLE IF NOT EXISTS iam_machine_oauth_rate_limit_windows (
    rate_key varchar(256) NOT NULL,
    window_start timestamptz NOT NULL,
    request_count integer NOT NULL CHECK (request_count >= 1),
    PRIMARY KEY(rate_key,window_start)
);
CREATE INDEX IF NOT EXISTS ix_machine_oauth_rate_window ON iam_machine_oauth_rate_limit_windows(window_start);

CREATE TABLE IF NOT EXISTS iam_machine_token_issuance_events (
    event_id varchar(128) PRIMARY KEY,
    outcome varchar(16) NOT NULL CHECK (outcome IN ('ALLOW','DENY')),
    reason_code varchar(128) NOT NULL,
    tenant_id varchar(128),
    service_account_id varchar(128),
    credential_id varchar(128),
    client_id varchar(96),
    jwt_id varchar(128),
    scopes varchar(160)[] NOT NULL DEFAULT ARRAY[]::varchar[],
    audience varchar(256),
    source_ip varchar(128),
    correlation_id varchar(128) NOT NULL,
    occurred_at timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_machine_token_audit_time ON iam_machine_token_issuance_events(occurred_at DESC);
CREATE INDEX IF NOT EXISTS ix_machine_token_audit_tenant ON iam_machine_token_issuance_events(tenant_id,service_account_id,occurred_at DESC);

-- Canonical machine OAuth failures. Do not expose whether a client_id or secret was the failing credential component.
INSERT INTO reason_code_catalog(reason_code,http_status,category,retryable,message_template)
VALUES
 ('MACHINE_OAUTH_INVALID_CLIENT',401,'AUTHENTICATION',false,'Machine client authentication failed.'),
 ('MACHINE_OAUTH_UNSUPPORTED_GRANT_TYPE',400,'VALIDATION',false,'The OAuth grant type is not supported.'),
 ('MACHINE_OAUTH_INVALID_SCOPE',400,'AUTHORIZATION',false,'Requested machine scope exceeds the Service Account boundary.'),
 ('MACHINE_OAUTH_INVALID_TARGET',400,'AUTHORIZATION',false,'Requested machine audience is not allowed.'),
 ('MACHINE_OAUTH_RATE_LIMITED',429,'SECURITY',true,'Machine token exchange rate limit exceeded.'),
 ('MACHINE_OAUTH_SERVER_ERROR',503,'SYSTEM',true,'Machine token service is temporarily unavailable.'),
 ('MACHINE_OAUTH_TOKEN_INVALID',401,'AUTHENTICATION',false,'Machine access token validation failed.')
ON CONFLICT (reason_code) DO UPDATE SET http_status=excluded.http_status,category=excluded.category,retryable=excluded.retryable,message_template=excluded.message_template,active=true;
