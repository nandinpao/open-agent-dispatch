\echo '==> P2-M1 dispatch contract integrity verification'

with active_profile_contract_violations as (
    select p.tenant_id, p.profile_code
    from agent_assignment_profiles p
    left join dispatch_task_definitions d
      on d.tenant_id = p.tenant_id
     and d.definition_id = p.task_definition_id
    where p.is_active = true
      and (
          p.task_definition_id is null
          or p.source_system is null
          or p.task_type is null
          or d.definition_id is null
          or d.source_system <> p.source_system
          or d.task_type <> p.task_type
          or d.status <> 'ACTIVE'
          or jsonb_typeof(coalesce(p.allowed_task_types_json, '[]'::jsonb)) <> 'array'
          or jsonb_array_length(coalesce(p.allowed_task_types_json, '[]'::jsonb)) <> 1
          or jsonb_typeof(coalesce(p.allowed_issue_providers_json, '[]'::jsonb)) <> 'array'
          or jsonb_array_length(coalesce(p.allowed_issue_providers_json, '[]'::jsonb)) <> 1
          or upper(p.allowed_task_types_json->>0) <> p.task_type
          or upper(p.allowed_issue_providers_json->>0) <> p.source_system
      )
), active_legacy_multi_profiles as (
    select tenant_id, profile_code
    from agent_assignment_profiles
    where is_active = true
      and (
          jsonb_array_length(coalesce(allowed_task_types_json, '[]'::jsonb)) > 1
          or jsonb_array_length(coalesce(allowed_issue_providers_json, '[]'::jsonb)) > 1
      )
), capability_binding_mismatches as (
    select b.tenant_id, b.profile_code, b.capability_code
    from assignment_profile_capability_bindings b
    join agent_assignment_profiles p
      on p.tenant_id = b.tenant_id
     and p.profile_code = b.profile_code
    left join agent_capability_catalog c
      on c.tenant_id = b.tenant_id
     and c.capability_code = b.capability_code
    where b.is_active = true
      and p.is_active = true
      and c.capability_code is not null
      and coalesce(c.task_definition_id, p.task_definition_id) <> p.task_definition_id
), summary as (
    select 'active_profile_contract_violations' as check_name, count(*) as failure_count from active_profile_contract_violations
    union all
    select 'active_legacy_multi_profiles', count(*) from active_legacy_multi_profiles
    union all
    select 'capability_binding_mismatches', count(*) from capability_binding_mismatches
)
select * from summary;

do $$
declare
    failures integer;
begin
    with active_profile_contract_violations as (
        select p.tenant_id, p.profile_code
        from agent_assignment_profiles p
        left join dispatch_task_definitions d
          on d.tenant_id = p.tenant_id
         and d.definition_id = p.task_definition_id
        where p.is_active = true
          and (
              p.task_definition_id is null
              or p.source_system is null
              or p.task_type is null
              or d.definition_id is null
              or d.source_system <> p.source_system
              or d.task_type <> p.task_type
              or d.status <> 'ACTIVE'
              or jsonb_typeof(coalesce(p.allowed_task_types_json, '[]'::jsonb)) <> 'array'
              or jsonb_array_length(coalesce(p.allowed_task_types_json, '[]'::jsonb)) <> 1
              or jsonb_typeof(coalesce(p.allowed_issue_providers_json, '[]'::jsonb)) <> 'array'
              or jsonb_array_length(coalesce(p.allowed_issue_providers_json, '[]'::jsonb)) <> 1
              or upper(p.allowed_task_types_json->>0) <> p.task_type
              or upper(p.allowed_issue_providers_json->>0) <> p.source_system
          )
    ), active_legacy_multi_profiles as (
        select tenant_id, profile_code
        from agent_assignment_profiles
        where is_active = true
          and (
              jsonb_array_length(coalesce(allowed_task_types_json, '[]'::jsonb)) > 1
              or jsonb_array_length(coalesce(allowed_issue_providers_json, '[]'::jsonb)) > 1
          )
    ), capability_binding_mismatches as (
        select b.tenant_id, b.profile_code, b.capability_code
        from assignment_profile_capability_bindings b
        join agent_assignment_profiles p
          on p.tenant_id = b.tenant_id
         and p.profile_code = b.profile_code
        left join agent_capability_catalog c
          on c.tenant_id = b.tenant_id
         and c.capability_code = b.capability_code
        where b.is_active = true
          and p.is_active = true
          and c.capability_code is not null
          and coalesce(c.task_definition_id, p.task_definition_id) <> p.task_definition_id
    )
    select count(*) into failures
    from (
        select 1 from active_profile_contract_violations
        union all select 1 from active_legacy_multi_profiles
        union all select 1 from capability_binding_mismatches
    ) all_failures;

    if failures > 0 then
        raise exception 'P2-M1 dispatch contract integrity verification failed: % violation(s)', failures;
    end if;
end $$;

\echo 'P2-M1 dispatch contract integrity verification passed.'
