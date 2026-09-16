-- Phase 12.1 — OAuth Authority Convergence
-- Service Account Responsibility is now backed by a canonical RBAC principal-role binding.

alter table token_service_accounts
    add column if not exists responsibility_binding_id varchar(128);

create unique index if not exists uq_token_service_account_responsibility_binding
    on token_service_accounts(tenant_id, responsibility_binding_id)
    where responsibility_binding_id is not null;

comment on column token_service_accounts.responsibility_binding_id is
    'Canonical SERVICE_ACCOUNT role binding managed atomically with the Service Account machine boundary.';
