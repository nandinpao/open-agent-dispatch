-- P4RA-F: canonical Integration ownership, Provider write policy, verified actor binding and dual attribution.
alter table integration_connections add column if not exists owner_department_id varchar(128);
alter table integration_connections add column if not exists owner_group_id varchar(128);
alter table integration_project_mappings add column if not exists provider_write_identity_policy varchar(48) not null default 'SERVICE_ACCOUNT_ON_BEHALF_OF';

alter table integration_connections drop constraint if exists fk_integration_connection_owner_department;
alter table integration_connections add constraint fk_integration_connection_owner_department foreign key(tenant_id,owner_department_id) references departments(tenant_id,department_id) not valid;
alter table integration_connections drop constraint if exists fk_integration_connection_owner_group;
alter table integration_connections add constraint fk_integration_connection_owner_group foreign key(tenant_id,owner_group_id) references organization_groups(tenant_id,group_id) not valid;
alter table integration_project_mappings drop constraint if exists ck_integration_mapping_write_identity_policy;
alter table integration_project_mappings add constraint ck_integration_mapping_write_identity_policy check(provider_write_identity_policy in ('SERVICE_ACCOUNT_ON_BEHALF_OF','USER_DELEGATED_IF_VERIFIED','SYSTEM_AUTOMATION','WRITE_PROHIBITED')) not valid;
alter table integration_project_mappings validate constraint ck_integration_mapping_write_identity_policy;

create index if not exists idx_integration_connection_owner_department on integration_connections(tenant_id,owner_department_id,connection_id);
create index if not exists idx_integration_connection_owner_group on integration_connections(tenant_id,owner_group_id,connection_id);
create index if not exists idx_integration_mapping_write_policy on integration_project_mappings(tenant_id,provider_write_identity_policy,mapping_id);

create table if not exists integration_external_actor_bindings(
 tenant_id varchar(64) not null,binding_id varchar(128) not null,human_principal_id varchar(128) not null,
 connection_id varchar(128) not null,provider_actor_id varchar(256) not null,integration_principal_id varchar(128) not null,
 credential_id varchar(128) not null,binding_status varchar(32) not null,verified_at timestamptz,expires_at timestamptz,
 version bigint not null,created_at timestamptz not null,updated_at timestamptz not null,
 primary key(tenant_id,binding_id),unique(tenant_id,human_principal_id,connection_id),
 foreign key(tenant_id,connection_id) references integration_connections(tenant_id,connection_id),
 foreign key(tenant_id,integration_principal_id) references integration_principals(tenant_id,principal_id),
 foreign key(tenant_id,credential_id) references integration_credentials(tenant_id,credential_id),
 check(binding_status in ('PENDING_VERIFICATION','VERIFIED','SUSPENDED','EXPIRED','REVOKED')),
 check(version>0),check(expires_at is null or verified_at is null or expires_at>verified_at),
 check(binding_status<>'VERIFIED' or verified_at is not null)
);
create table if not exists integration_provider_execution_attributions(
 tenant_id varchar(64) not null,attribution_id varchar(128) not null,human_principal_id varchar(128) not null,
 authorization_decision_id varchar(128) not null,resource_type varchar(64) not null,resource_id varchar(128) not null,
 resource_action varchar(160) not null,purpose varchar(160) not null,connection_id varchar(128) not null,
 mapping_id varchar(128) not null,integration_principal_id varchar(128) not null,credential_id varchar(128) not null,
 credential_version varchar(128) not null,provider_actor_id varchar(256),write_identity_policy varchar(48) not null,
 attribution_status varchar(32) not null,correlation_id varchar(128) not null,idempotency_key varchar(256) not null,
 provider_operation_id varchar(256),failure_code varchar(128),created_at timestamptz not null,
 primary key(tenant_id,attribution_id),unique(tenant_id,idempotency_key),
 foreign key(tenant_id,connection_id) references integration_connections(tenant_id,connection_id),
 foreign key(tenant_id,mapping_id) references integration_project_mappings(tenant_id,mapping_id),
 foreign key(tenant_id,integration_principal_id) references integration_principals(tenant_id,principal_id),
 foreign key(tenant_id,credential_id) references integration_credentials(tenant_id,credential_id),
 check(write_identity_policy in ('SERVICE_ACCOUNT_ON_BEHALF_OF','USER_DELEGATED_IF_VERIFIED','SYSTEM_AUTOMATION','WRITE_PROHIBITED')),
 check(attribution_status in ('SHADOW_OBSERVED','AUTHORIZED','QUEUED','EXECUTING','SUCCEEDED','FAILED','DENIED'))
);
create index if not exists idx_external_actor_binding_human on integration_external_actor_bindings(tenant_id,human_principal_id,connection_id,binding_status);
create index if not exists idx_provider_attribution_decision on integration_provider_execution_attributions(tenant_id,authorization_decision_id,created_at desc);
create index if not exists idx_provider_attribution_mapping on integration_provider_execution_attributions(tenant_id,mapping_id,created_at desc);

alter table integration_external_actor_bindings enable row level security;
alter table integration_external_actor_bindings force row level security;
drop policy if exists integration_external_actor_binding_tenant on integration_external_actor_bindings;
create policy integration_external_actor_binding_tenant on integration_external_actor_bindings using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table integration_provider_execution_attributions enable row level security;
alter table integration_provider_execution_attributions force row level security;
drop policy if exists integration_provider_attribution_tenant on integration_provider_execution_attributions;
create policy integration_provider_attribution_tenant on integration_provider_execution_attributions using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());

drop trigger if exists trg_provider_execution_attribution_immutable on integration_provider_execution_attributions;
create trigger trg_provider_execution_attribution_immutable before update or delete on integration_provider_execution_attributions for each row execute function p4ra_reject_append_only_mutation();

-- Attribution deliberately contains no secret_ref, token, password, refresh_token or provider credential material.
