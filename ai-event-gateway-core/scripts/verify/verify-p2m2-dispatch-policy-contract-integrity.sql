\echo '==> P2-M2 dispatch policy task contract integrity verification'

with active_policy_contract_violations as (
    select skill_code
    from agent_skill_definitions
    where enabled = true
      and (
          task_definition_id is null
          or source_system is null
          or task_type is null
          or metadata_json->>'taskDefinitionId' is null
          or metadata_json->>'sourceSystem' is null
          or metadata_json->>'taskType' is null
      )
), active_policy_task_mismatches as (
    select sd.skill_code, sd.task_definition_id, sd.source_system, sd.task_type
    from agent_skill_definitions sd
    left join dispatch_task_definitions d
      on d.definition_id = sd.task_definition_id
     and d.source_system = sd.source_system
     and d.task_type = sd.task_type
    where sd.enabled = true
      and sd.task_definition_id is not null
      and d.definition_id is null
), active_profile_policy_binding_mismatches as (
    select pb.tenant_id, pb.profile_code, pb.policy_code
    from agent_assignment_profile_policy_bindings pb
    join agent_assignment_profiles p
      on p.tenant_id = pb.tenant_id
     and p.profile_code = pb.profile_code
    join agent_skill_definitions sd
      on sd.skill_code = pb.policy_code
    where pb.is_active = true
      and p.is_active = true
      and p.task_definition_id is not null
      and sd.task_definition_id is not null
      and sd.task_definition_id <> p.task_definition_id
), cms_policy_missing as (
    select 'CMS_CMS_CONTENT_REVIEW_BASELINE_POLICY' as policy_code
    where not exists (
        select 1
        from agent_skill_definitions
        where skill_code = 'CMS_CMS_CONTENT_REVIEW_BASELINE_POLICY'
          and enabled = true
          and source_system = 'CMS'
          and task_type = 'CMS_CONTENT_REVIEW'
          and task_definition_id is not null
    )
), summary as (
    select 'active_policy_contract_violations' as check_name, count(*) as failure_count from active_policy_contract_violations
    union all
    select 'active_policy_task_mismatches', count(*) from active_policy_task_mismatches
    union all
    select 'active_profile_policy_binding_mismatches', count(*) from active_profile_policy_binding_mismatches
    union all
    select 'cms_policy_missing', count(*) from cms_policy_missing
)
select * from summary;

do $$
declare
    failures integer;
begin
    with active_policy_contract_violations as (
        select skill_code
        from agent_skill_definitions
        where enabled = true
          and (
              task_definition_id is null
              or source_system is null
              or task_type is null
              or metadata_json->>'taskDefinitionId' is null
              or metadata_json->>'sourceSystem' is null
              or metadata_json->>'taskType' is null
          )
    ), active_policy_task_mismatches as (
        select sd.skill_code
        from agent_skill_definitions sd
        left join dispatch_task_definitions d
          on d.definition_id = sd.task_definition_id
         and d.source_system = sd.source_system
         and d.task_type = sd.task_type
        where sd.enabled = true
          and sd.task_definition_id is not null
          and d.definition_id is null
    ), active_profile_policy_binding_mismatches as (
        select pb.tenant_id, pb.profile_code, pb.policy_code
        from agent_assignment_profile_policy_bindings pb
        join agent_assignment_profiles p
          on p.tenant_id = pb.tenant_id
         and p.profile_code = pb.profile_code
        join agent_skill_definitions sd
          on sd.skill_code = pb.policy_code
        where pb.is_active = true
          and p.is_active = true
          and p.task_definition_id is not null
          and sd.task_definition_id is not null
          and sd.task_definition_id <> p.task_definition_id
    ), cms_policy_missing as (
        select 'CMS_CMS_CONTENT_REVIEW_BASELINE_POLICY' as policy_code
        where not exists (
            select 1
            from agent_skill_definitions
            where skill_code = 'CMS_CMS_CONTENT_REVIEW_BASELINE_POLICY'
              and enabled = true
              and source_system = 'CMS'
              and task_type = 'CMS_CONTENT_REVIEW'
              and task_definition_id is not null
        )
    )
    select count(*) into failures
    from (
        select 1 from active_policy_contract_violations
        union all select 1 from active_policy_task_mismatches
        union all select 1 from active_profile_policy_binding_mismatches
        union all select 1 from cms_policy_missing
    ) all_failures;

    if failures > 0 then
        raise exception 'P2-M2 dispatch policy task contract integrity verification failed: % violation(s)', failures;
    end if;
end $$;

\echo 'P2-M2 dispatch policy task contract integrity verification passed.'
