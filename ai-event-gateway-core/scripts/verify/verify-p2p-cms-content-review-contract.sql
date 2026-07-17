\echo '==> P2-P CMS_CONTENT_REVIEW end-to-end contract verification'

with cms_task_definition as (
    select definition_id
    from dispatch_task_definitions
    where tenant_id = 'default'
      and definition_id = 'taskdef-p2p-default-cms-content-review'
      and source_system = 'CMS'
      and task_type = 'CMS_CONTENT_REVIEW'
      and status = 'ACTIVE'
), cms_policy as (
    select skill_code
    from agent_skill_definitions
    where skill_code = 'CMS_CMS_CONTENT_REVIEW_BASELINE_POLICY'
      and enabled = true
      and source_system = 'CMS'
      and task_type = 'CMS_CONTENT_REVIEW'
      and task_definition_id = 'taskdef-p2p-default-cms-content-review'
), cms_capability as (
    select capability_code
    from agent_capability_catalog
    where tenant_id = 'default'
      and capability_code = 'CMS_CONTENT_REVIEW'
      and source_system = 'CMS'
      and task_type = 'CMS_CONTENT_REVIEW'
      and task_definition_id = 'taskdef-p2p-default-cms-content-review'
      and status = 'ACTIVE'
      and is_dispatch_eligible = true
), cms_profile as (
    select profile_code
    from agent_assignment_profiles
    where tenant_id = 'default'
      and profile_code = 'CMS_CONTENT_REVIEW_REVIEWER'
      and source_system = 'CMS'
      and task_type = 'CMS_CONTENT_REVIEW'
      and task_definition_id = 'taskdef-p2p-default-cms-content-review'
      and is_active = true
      and allowed_task_types_json = '["CMS_CONTENT_REVIEW"]'::jsonb
      and allowed_issue_providers_json = '["CMS"]'::jsonb
), cms_policy_binding as (
    select pb.binding_id
    from agent_assignment_profile_policy_bindings pb
    join cms_profile p on p.profile_code = pb.profile_code
    where pb.tenant_id = 'default'
      and pb.policy_code = 'CMS_CMS_CONTENT_REVIEW_BASELINE_POLICY'
      and pb.is_active = true
      and pb.is_required = true
), cms_capability_binding as (
    select cb.binding_id
    from assignment_profile_capability_bindings cb
    join cms_profile p on p.profile_code = cb.profile_code
    where cb.tenant_id = 'default'
      and cb.capability_code = 'CMS_CONTENT_REVIEW'
      and cb.is_active = true
      and cb.is_required = true
      and cb.approval_status = 'ACTIVE'
), cms_agent as (
    select agent_id
    from agent_profiles
    where agent_id = 'p2p-cms-review-agent-001'
      and tenant_id = 'default'
      and approval_status = 'APPROVED'
      and enabled = true
      and risk_status = 'NORMAL'
), cms_agent_credential as (
    select credential_id
    from agent_credentials
    where agent_id = 'p2p-cms-review-agent-001'
      and revoked_at is null
      and (expires_at is null or expires_at > now())
), cms_agent_runtime as (
    select agent_id
    from agent_runtime_descriptors
    where agent_id = 'p2p-cms-review-agent-001'
      and status in ('IDLE','CONNECTED','BUSY_ACCEPTING')
      and draining = false
      and (available_slots > 0 or active_tasks < max_concurrent_tasks)
      and runtime_features_json @> '["TASK_ACK","TASK_RESULT"]'::jsonb
), cms_agent_qualification as (
    select qualification_id
    from agent_qualifications
    where tenant_id = 'default'
      and agent_id = 'p2p-cms-review-agent-001'
      and profile_code = 'CMS_CONTENT_REVIEW_REVIEWER'
      and qualification_status = 'APPROVED'
      and (expires_at is null or expires_at > now())
), cms_agent_capability as (
    select assignment_id
    from agent_capability_assignments
    where tenant_id = 'default'
      and agent_id = 'p2p-cms-review-agent-001'
      and capability_code = 'CMS_CONTENT_REVIEW'
      and status = 'APPROVED'
      and (expires_at is null or expires_at > now())
), cms_runtime_trust as (
    select count(distinct feature_code) as trusted_count
    from agent_runtime_feature_trust
    where tenant_id = 'default'
      and agent_id = 'p2p-cms-review-agent-001'
      and feature_code in ('TASK_ACK','TASK_RESULT')
      and trust_status = 'TRUSTED'
      and (expires_at is null or expires_at > now())
), cms_task as (
    select task_id
    from tasks
    where task_id = 'p2p-cms-content-review-task'
      and tenant_id = 'default'
      and source_system = 'CMS'
      and task_type = 'CMS_CONTENT_REVIEW'
      and status in ('QUEUED','CREATED','RETRY_WAIT')
), summary as (
    select 'cms_task_definition' as check_name, case when exists(select 1 from cms_task_definition) then 0 else 1 end as failure_count
    union all select 'cms_policy', case when exists(select 1 from cms_policy) then 0 else 1 end
    union all select 'cms_capability', case when exists(select 1 from cms_capability) then 0 else 1 end
    union all select 'cms_profile', case when exists(select 1 from cms_profile) then 0 else 1 end
    union all select 'cms_policy_binding', case when exists(select 1 from cms_policy_binding) then 0 else 1 end
    union all select 'cms_capability_binding', case when exists(select 1 from cms_capability_binding) then 0 else 1 end
    union all select 'cms_agent', case when exists(select 1 from cms_agent) then 0 else 1 end
    union all select 'cms_agent_credential', case when exists(select 1 from cms_agent_credential) then 0 else 1 end
    union all select 'cms_agent_runtime', case when exists(select 1 from cms_agent_runtime) then 0 else 1 end
    union all select 'cms_agent_qualification', case when exists(select 1 from cms_agent_qualification) then 0 else 1 end
    union all select 'cms_agent_capability', case when exists(select 1 from cms_agent_capability) then 0 else 1 end
    union all select 'cms_runtime_trust', case when (select trusted_count from cms_runtime_trust) = 2 then 0 else 1 end
    union all select 'cms_task', case when exists(select 1 from cms_task) then 0 else 1 end
)
select * from summary;

do $$
declare
    failures integer;
begin
    with cms_task_definition as (
        select definition_id from dispatch_task_definitions
        where tenant_id = 'default' and definition_id = 'taskdef-p2p-default-cms-content-review'
          and source_system = 'CMS' and task_type = 'CMS_CONTENT_REVIEW' and status = 'ACTIVE'
    ), cms_policy as (
        select skill_code from agent_skill_definitions
        where skill_code = 'CMS_CMS_CONTENT_REVIEW_BASELINE_POLICY' and enabled = true
          and source_system = 'CMS' and task_type = 'CMS_CONTENT_REVIEW'
          and task_definition_id = 'taskdef-p2p-default-cms-content-review'
    ), cms_capability as (
        select capability_code from agent_capability_catalog
        where tenant_id = 'default' and capability_code = 'CMS_CONTENT_REVIEW'
          and source_system = 'CMS' and task_type = 'CMS_CONTENT_REVIEW'
          and task_definition_id = 'taskdef-p2p-default-cms-content-review'
          and status = 'ACTIVE' and is_dispatch_eligible = true
    ), cms_profile as (
        select profile_code from agent_assignment_profiles
        where tenant_id = 'default' and profile_code = 'CMS_CONTENT_REVIEW_REVIEWER'
          and source_system = 'CMS' and task_type = 'CMS_CONTENT_REVIEW'
          and task_definition_id = 'taskdef-p2p-default-cms-content-review' and is_active = true
          and allowed_task_types_json = '["CMS_CONTENT_REVIEW"]'::jsonb
          and allowed_issue_providers_json = '["CMS"]'::jsonb
    ), cms_policy_binding as (
        select pb.binding_id from agent_assignment_profile_policy_bindings pb
        join cms_profile p on p.profile_code = pb.profile_code
        where pb.tenant_id = 'default' and pb.policy_code = 'CMS_CMS_CONTENT_REVIEW_BASELINE_POLICY'
          and pb.is_active = true and pb.is_required = true
    ), cms_capability_binding as (
        select cb.binding_id from assignment_profile_capability_bindings cb
        join cms_profile p on p.profile_code = cb.profile_code
        where cb.tenant_id = 'default' and cb.capability_code = 'CMS_CONTENT_REVIEW'
          and cb.is_active = true and cb.is_required = true and cb.approval_status = 'ACTIVE'
    ), cms_agent as (
        select agent_id from agent_profiles
        where agent_id = 'p2p-cms-review-agent-001' and tenant_id = 'default'
          and approval_status = 'APPROVED' and enabled = true and risk_status = 'NORMAL'
    ), cms_agent_credential as (
        select credential_id from agent_credentials
        where agent_id = 'p2p-cms-review-agent-001' and revoked_at is null
          and (expires_at is null or expires_at > now())
    ), cms_agent_runtime as (
        select agent_id from agent_runtime_descriptors
        where agent_id = 'p2p-cms-review-agent-001' and status in ('IDLE','CONNECTED','BUSY_ACCEPTING')
          and draining = false and (available_slots > 0 or active_tasks < max_concurrent_tasks)
          and runtime_features_json @> '["TASK_ACK","TASK_RESULT"]'::jsonb
    ), cms_agent_qualification as (
        select qualification_id from agent_qualifications
        where tenant_id = 'default' and agent_id = 'p2p-cms-review-agent-001'
          and profile_code = 'CMS_CONTENT_REVIEW_REVIEWER' and qualification_status = 'APPROVED'
          and (expires_at is null or expires_at > now())
    ), cms_agent_capability as (
        select assignment_id from agent_capability_assignments
        where tenant_id = 'default' and agent_id = 'p2p-cms-review-agent-001'
          and capability_code = 'CMS_CONTENT_REVIEW' and status = 'APPROVED'
          and (expires_at is null or expires_at > now())
    ), cms_runtime_trust as (
        select count(distinct feature_code) as trusted_count
        from agent_runtime_feature_trust
        where tenant_id = 'default' and agent_id = 'p2p-cms-review-agent-001'
          and feature_code in ('TASK_ACK','TASK_RESULT') and trust_status = 'TRUSTED'
          and (expires_at is null or expires_at > now())
    ), cms_task as (
        select task_id from tasks
        where task_id = 'p2p-cms-content-review-task' and tenant_id = 'default'
          and source_system = 'CMS' and task_type = 'CMS_CONTENT_REVIEW'
          and status in ('QUEUED','CREATED','RETRY_WAIT')
    ), failures_table as (
        select 1 where not exists(select 1 from cms_task_definition)
        union all select 1 where not exists(select 1 from cms_policy)
        union all select 1 where not exists(select 1 from cms_capability)
        union all select 1 where not exists(select 1 from cms_profile)
        union all select 1 where not exists(select 1 from cms_policy_binding)
        union all select 1 where not exists(select 1 from cms_capability_binding)
        union all select 1 where not exists(select 1 from cms_agent)
        union all select 1 where not exists(select 1 from cms_agent_credential)
        union all select 1 where not exists(select 1 from cms_agent_runtime)
        union all select 1 where not exists(select 1 from cms_agent_qualification)
        union all select 1 where not exists(select 1 from cms_agent_capability)
        union all select 1 where (select trusted_count from cms_runtime_trust) <> 2
        union all select 1 where not exists(select 1 from cms_task)
    )
    select count(*) into failures from failures_table;

    if failures > 0 then
        raise exception 'P2-P CMS_CONTENT_REVIEW contract verification failed: % violation(s)', failures;
    end if;
end $$;

\echo 'P2-P CMS_CONTENT_REVIEW end-to-end contract verification passed.'
