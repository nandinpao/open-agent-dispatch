-- I0-G: reliability, attribution audit and credential-use evidence.
-- Redmine remains the authority for Issue permissions; these columns are operational evidence only.
alter table task_issue_links add column if not exists provider_outcome_certainty varchar(32);
alter table task_issue_links add column if not exists operation_fingerprint varchar(128);
alter table task_issue_links add column if not exists correlation_id varchar(128);
alter table task_issue_links add column if not exists a2a_request_id varchar(128);
alter table task_issue_links add column if not exists source_system_id varchar(128);
alter table task_issue_links add column if not exists technical_principal_id varchar(128);
alter table task_issue_links add column if not exists credential_id varchar(128);
alter table task_issue_links add column if not exists credential_version varchar(128);

create index if not exists idx_task_issue_links_correlation on task_issue_links(tenant_id, correlation_id, updated_at desc) where correlation_id is not null;
create index if not exists idx_task_issue_links_outcome_certainty on task_issue_links(tenant_id, provider_outcome_certainty, updated_at desc) where provider_outcome_certainty is not null;
create index if not exists idx_task_issue_links_operation_fingerprint on task_issue_links(tenant_id, operation_fingerprint) where operation_fingerprint is not null;

alter table adapter_executor_audit add column if not exists tenant_id varchar(64);
alter table adapter_executor_audit add column if not exists correlation_id varchar(128);
alter table adapter_executor_audit add column if not exists a2a_request_id varchar(128);
alter table adapter_executor_audit add column if not exists dispatch_request_id varchar(128);
alter table adapter_executor_audit add column if not exists assignment_id varchar(128);
alter table adapter_executor_audit add column if not exists agent_id varchar(128);
alter table adapter_executor_audit add column if not exists source_system_id varchar(128);
alter table adapter_executor_audit add column if not exists connection_id varchar(128);
alter table adapter_executor_audit add column if not exists project_mapping_id varchar(128);
alter table adapter_executor_audit add column if not exists external_project_id varchar(128);
alter table adapter_executor_audit add column if not exists external_issue_id varchar(128);
alter table adapter_executor_audit add column if not exists provider_status_code integer;
alter table adapter_executor_audit add column if not exists provider_failure_code varchar(128);
alter table adapter_executor_audit add column if not exists provider_health_impact varchar(32);
alter table adapter_executor_audit add column if not exists provider_outcome_certainty varchar(32);
alter table adapter_executor_audit add column if not exists idempotency_key varchar(256);
alter table adapter_executor_audit add column if not exists operation_fingerprint varchar(128);
alter table adapter_executor_audit add column if not exists technical_principal_id varchar(128);
alter table adapter_executor_audit add column if not exists credential_id varchar(128);
alter table adapter_executor_audit add column if not exists credential_version varchar(128);

create index if not exists idx_adapter_executor_audit_correlation on adapter_executor_audit(tenant_id, correlation_id, created_at desc) where correlation_id is not null;
create index if not exists idx_adapter_executor_audit_external_issue on adapter_executor_audit(tenant_id, external_issue_id, created_at desc) where external_issue_id is not null;
