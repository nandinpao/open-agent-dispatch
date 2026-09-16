-- Phase 0D enforce: pool-first dispatch and tenant-aware directional A2A.

do $$ begin
  if not exists (select 1 from pg_constraint where conname='ck_dispatch_flows_candidate_source_mode') then
    alter table dispatch_flows add constraint ck_dispatch_flows_candidate_source_mode check (candidate_source_mode='AGENT_POOL_ONLY');
  end if;
  if not exists (select 1 from pg_constraint where conname='ck_dispatch_flows_capability_policy_mode') then
    alter table dispatch_flows add constraint ck_dispatch_flows_capability_policy_mode check (capability_policy_mode in ('METADATA_ONLY','EXPLICIT_POOL_POLICY'));
  end if;
  if not exists (select 1 from pg_constraint where conname='ck_dispatch_policies_candidate_source_mode') then
    alter table dispatch_policies add constraint ck_dispatch_policies_candidate_source_mode check (candidate_source_mode='AGENT_POOL_ONLY');
  end if;
  if not exists (select 1 from pg_constraint where conname='ck_dispatch_policies_capability_policy_mode') then
    alter table dispatch_policies add constraint ck_dispatch_policies_capability_policy_mode check (capability_policy_mode in ('METADATA_ONLY','EXPLICIT_POOL_POLICY'));
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_pool_capability_policy_pool') then
    alter table pool_capability_policies add constraint fk_pool_capability_policy_pool foreign key (tenant_id,pool_id) references agent_pools(tenant_id,pool_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_a2a_policy_tenant') then
    alter table a2a_policies add constraint fk_a2a_policy_tenant foreign key (tenant_id) references tenants(tenant_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_a2a_policy_source_domain') then
    alter table a2a_policies add constraint fk_a2a_policy_source_domain foreign key (tenant_id,source_domain_id) references service_domains(tenant_id,service_domain_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_a2a_policy_target_domain') then
    alter table a2a_policies add constraint fk_a2a_policy_target_domain foreign key (tenant_id,target_domain_id) references service_domains(tenant_id,service_domain_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_a2a_policy_target_pool') then
    alter table a2a_policies add constraint fk_a2a_policy_target_pool foreign key (tenant_id,target_agent_pool_id) references agent_pools(tenant_id,pool_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='ck_pool_capability_policy_enforcement') then
    alter table pool_capability_policies add constraint ck_pool_capability_policy_enforcement check (enforcement_mode in ('ADVISORY','REQUIRED'));
  end if;
  if not exists (select 1 from pg_constraint where conname='ck_pool_capability_policy_match') then
    alter table pool_capability_policies add constraint ck_pool_capability_policy_match check (match_mode in ('ANY','ALL'));
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_a2a_policy_source_department') then
    alter table a2a_policies add constraint fk_a2a_policy_source_department foreign key (tenant_id,source_department_id) references departments(tenant_id,department_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_a2a_policy_target_department') then
    alter table a2a_policies add constraint fk_a2a_policy_target_department foreign key (tenant_id,target_department_id) references departments(tenant_id,department_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_a2a_policy_source_group') then
    alter table a2a_policies add constraint fk_a2a_policy_source_group foreign key (tenant_id,source_group_id) references organization_groups(tenant_id,group_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_a2a_policy_target_group') then
    alter table a2a_policies add constraint fk_a2a_policy_target_group foreign key (tenant_id,target_group_id) references organization_groups(tenant_id,group_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_a2a_policy_source_pool') then
    alter table a2a_policies add constraint fk_a2a_policy_source_pool foreign key (tenant_id,source_agent_pool_id) references agent_pools(tenant_id,pool_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_a2a_policy_source_agent') then
    alter table a2a_policies add constraint fk_a2a_policy_source_agent foreign key (tenant_id,source_agent_id) references agents(tenant_id,agent_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_a2a_request_policy') then
    alter table a2a_requests add constraint fk_a2a_request_policy foreign key (tenant_id,policy_id) references a2a_policies(tenant_id,policy_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_a2a_request_root_task') then
    alter table a2a_requests add constraint fk_a2a_request_root_task foreign key (tenant_id,root_task_id) references tasks(tenant_id,task_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_a2a_request_source_task') then
    alter table a2a_requests add constraint fk_a2a_request_source_task foreign key (tenant_id,source_task_id) references tasks(tenant_id,task_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_a2a_request_requesting_task') then
    alter table a2a_requests add constraint fk_a2a_request_requesting_task foreign key (tenant_id,requesting_task_id) references tasks(tenant_id,task_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_a2a_request_child_task') then
    alter table a2a_requests add constraint fk_a2a_request_child_task foreign key (tenant_id,child_task_id) references tasks(tenant_id,task_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_a2a_request_target_pool') then
    alter table a2a_requests add constraint fk_a2a_request_target_pool foreign key (tenant_id,target_agent_pool_id) references agent_pools(tenant_id,pool_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_a2a_request_source_domain') then
    alter table a2a_requests add constraint fk_a2a_request_source_domain foreign key (tenant_id,source_domain_id) references service_domains(tenant_id,service_domain_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_a2a_request_target_domain') then
    alter table a2a_requests add constraint fk_a2a_request_target_domain foreign key (tenant_id,target_domain_id) references service_domains(tenant_id,service_domain_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_a2a_request_source_department') then
    alter table a2a_requests add constraint fk_a2a_request_source_department foreign key (tenant_id,source_department_id) references departments(tenant_id,department_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_a2a_request_target_department') then
    alter table a2a_requests add constraint fk_a2a_request_target_department foreign key (tenant_id,target_department_id) references departments(tenant_id,department_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_a2a_request_source_group') then
    alter table a2a_requests add constraint fk_a2a_request_source_group foreign key (tenant_id,source_group_id) references organization_groups(tenant_id,group_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_a2a_request_target_group') then
    alter table a2a_requests add constraint fk_a2a_request_target_group foreign key (tenant_id,target_group_id) references organization_groups(tenant_id,group_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_a2a_request_requesting_agent') then
    alter table a2a_requests add constraint fk_a2a_request_requesting_agent foreign key (tenant_id,requesting_agent_id) references agents(tenant_id,agent_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_a2a_result_request') then
    alter table a2a_results add constraint fk_a2a_result_request foreign key (tenant_id,a2a_request_id) references a2a_requests(tenant_id,a2a_request_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_a2a_result_child_task') then
    alter table a2a_results add constraint fk_a2a_result_child_task foreign key (tenant_id,child_task_id) references tasks(tenant_id,task_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_a2a_result_root_task') then
    alter table a2a_results add constraint fk_a2a_result_root_task foreign key (tenant_id,root_task_id) references tasks(tenant_id,task_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_a2a_result_parent_task') then
    alter table a2a_results add constraint fk_a2a_result_parent_task foreign key (tenant_id,parent_task_id) references tasks(tenant_id,task_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_tasks_a2a_policy') then
    alter table tasks add constraint fk_tasks_a2a_policy foreign key (tenant_id,a2a_policy_id) references a2a_policies(tenant_id,policy_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_tasks_requesting_agent') then
    alter table tasks add constraint fk_tasks_requesting_agent foreign key (tenant_id,requesting_agent_id) references agents(tenant_id,agent_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_a2a_rate_limit_policy') then
    alter table a2a_rate_limit_windows add constraint fk_a2a_rate_limit_policy foreign key (tenant_id,policy_id) references a2a_policies(tenant_id,policy_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_a2a_idempotency_tenant') then
    alter table a2a_idempotency_records add constraint fk_a2a_idempotency_tenant foreign key (tenant_id) references tenants(tenant_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='fk_a2a_history_request') then
    alter table a2a_state_history add constraint fk_a2a_history_request foreign key (tenant_id,a2a_request_id) references a2a_requests(tenant_id,a2a_request_id) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname='ck_a2a_policy_hop_count') then
    alter table a2a_policies add constraint ck_a2a_policy_hop_count check (max_hop_count between 1 and 32);
  end if;
  if not exists (select 1 from pg_constraint where conname='ck_a2a_request_hop_count') then
    alter table a2a_requests add constraint ck_a2a_request_hop_count check (hop_count between 1 and 32);
  end if;
  if not exists (select 1 from pg_constraint where conname='ck_a2a_request_approval_counts') then
    alter table a2a_requests add constraint ck_a2a_request_approval_counts check (
      approval_count >= 0 and required_approval_count between 0 and 2 and approval_count <= required_approval_count
    );
  end if;
  if not exists (select 1 from pg_constraint where conname='ck_tasks_hop_count') then
    alter table tasks add constraint ck_tasks_hop_count check (hop_count between 0 and 32);
  end if;
end $$;

alter table pool_capability_policies validate constraint fk_pool_capability_policy_pool;
alter table a2a_policies validate constraint fk_a2a_policy_tenant;
alter table a2a_policies validate constraint fk_a2a_policy_source_domain;
alter table a2a_policies validate constraint fk_a2a_policy_target_domain;
alter table a2a_policies validate constraint fk_a2a_policy_target_pool;
alter table a2a_policies validate constraint fk_a2a_policy_source_department;
alter table a2a_policies validate constraint fk_a2a_policy_target_department;
alter table a2a_policies validate constraint fk_a2a_policy_source_group;
alter table a2a_policies validate constraint fk_a2a_policy_target_group;
alter table a2a_policies validate constraint fk_a2a_policy_source_pool;
alter table a2a_policies validate constraint fk_a2a_policy_source_agent;
alter table a2a_requests validate constraint fk_a2a_request_policy;
alter table a2a_requests validate constraint fk_a2a_request_root_task;
alter table a2a_requests validate constraint fk_a2a_request_source_task;
alter table a2a_requests validate constraint fk_a2a_request_requesting_task;
alter table a2a_requests validate constraint fk_a2a_request_child_task;
alter table a2a_requests validate constraint fk_a2a_request_target_pool;
alter table a2a_requests validate constraint fk_a2a_request_source_domain;
alter table a2a_requests validate constraint fk_a2a_request_target_domain;
alter table a2a_requests validate constraint fk_a2a_request_source_department;
alter table a2a_requests validate constraint fk_a2a_request_target_department;
alter table a2a_requests validate constraint fk_a2a_request_source_group;
alter table a2a_requests validate constraint fk_a2a_request_target_group;
alter table a2a_requests validate constraint fk_a2a_request_requesting_agent;
alter table a2a_results validate constraint fk_a2a_result_request;
alter table a2a_results validate constraint fk_a2a_result_child_task;
alter table a2a_results validate constraint fk_a2a_result_root_task;
alter table a2a_results validate constraint fk_a2a_result_parent_task;
alter table tasks validate constraint fk_tasks_a2a_policy;
alter table tasks validate constraint fk_tasks_requesting_agent;
alter table a2a_rate_limit_windows validate constraint fk_a2a_rate_limit_policy;
alter table a2a_idempotency_records validate constraint fk_a2a_idempotency_tenant;
alter table a2a_state_history validate constraint fk_a2a_history_request;

create or replace function reject_a2a_state_history_mutation() returns trigger language plpgsql as $$
begin raise exception 'a2a_state_history is immutable'; end $$;
drop trigger if exists trg_a2a_state_history_immutable on a2a_state_history;
create trigger trg_a2a_state_history_immutable before update or delete on a2a_state_history
for each row execute function reject_a2a_state_history_mutation();

create or replace function reject_a2a_direction_change() returns trigger language plpgsql as $$
begin
  if old.tenant_id<>new.tenant_id or old.source_domain_id<>new.source_domain_id or old.target_domain_id<>new.target_domain_id then
    raise exception 'A2A policy direction and Tenant are immutable';
  end if;
  return new;
end $$;
drop trigger if exists trg_a2a_policy_direction_immutable on a2a_policies;
create trigger trg_a2a_policy_direction_immutable before update on a2a_policies
for each row execute function reject_a2a_direction_change();
