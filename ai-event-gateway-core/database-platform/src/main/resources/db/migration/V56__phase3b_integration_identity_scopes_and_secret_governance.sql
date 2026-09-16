-- Phase 3B: operation/issue-type scope and production secret governance.
alter table integration_principal_scopes
  add column if not exists allowed_operations_json jsonb not null default '["READ","CREATE","COMMENT","UPDATE","RELATION","WEBHOOK"]'::jsonb,
  add column if not exists allowed_issue_types_json jsonb not null default '["*"]'::jsonb;

alter table integration_principal_scopes drop constraint if exists ck_integration_principal_scope_operations_p3b;
alter table integration_principal_scopes add constraint ck_integration_principal_scope_operations_p3b
  check (jsonb_typeof(allowed_operations_json) = 'array' and jsonb_array_length(allowed_operations_json) > 0);

alter table integration_principal_scopes drop constraint if exists ck_integration_principal_scope_issue_types_p3b;
alter table integration_principal_scopes add constraint ck_integration_principal_scope_issue_types_p3b
  check (jsonb_typeof(allowed_issue_types_json) = 'array' and jsonb_array_length(allowed_issue_types_json) > 0);

-- Existing credential metadata remains reference-only. Raw secret-like references are rejected at the database boundary.
alter table integration_credentials drop constraint if exists ck_integration_credential_secret_reference_p3b;
alter table integration_credentials add constraint ck_integration_credential_secret_reference_p3b check (
  secret_ref ~ '^(vault|env|file)://.+'
  and secret_ref !~* '^(bearer|basic)[[:space:]]'
  and secret_ref !~ '-----BEGIN[[:space:]]+PRIVATE[[:space:]]+KEY-----'
);

create index if not exists idx_integration_principal_scope_operation_p3b
  on integration_principal_scopes using gin (allowed_operations_json);
create index if not exists idx_integration_principal_scope_issue_type_p3b
  on integration_principal_scopes using gin (allowed_issue_types_json);
