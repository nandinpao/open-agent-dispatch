-- Phase 2-2 Dispatch Integrity Repair Dry Run
--
-- Purpose:
--   Join the Phase 2-1 integrity findings with the Phase 2-2 repair plan and
--   emit proposed non-mutating remediation actions before FK/check migrations.
--
-- Safety:
--   This script is read-only. It performs no schema or data mutation and emits
--   only summaries and action recommendations.
--
-- Output:
--   1. Repair readiness summary by severity and repair_class
--   2. FK/check blocker summary
--   3. Detailed proposed repair actions per current finding

\pset pager off
\pset null '<null>'

\echo '== Phase 2-2 Dispatch Repair Readiness Summary =='

with repair_plan(check_id, severity, repair_class, default_action, automation_policy, fk_readiness, check_readiness, proposed_fix, manual_review_required, notes) as (
  values
    ('DFLOW_SOURCE_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'CREATE_OR_REMAP_SOURCE_SYSTEM', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Create the missing source_systems row in the same tenant or remap the Source Flow to an existing source_system.', true, 'Do not auto-create source systems because display name, owner and lifecycle status are business decisions.'),
    ('DFLOW_DEFAULT_POOL_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'CREATE_OR_REMAP_DEFAULT_POOL', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Create the missing agent_pools row or remap dispatch_flows.default_pool_id to an existing pool in the same tenant.', true, 'Do not auto-create pools because membership and operating model must be confirmed.'),
    ('DFLOW_ACTIVE_WITHOUT_DEFAULT_POOL', 'BLOCKER', 'MANUAL_REQUIRED', 'ASSIGN_DEFAULT_POOL_OR_DISABLE_FLOW', 'manual', 'blocks_phase2_3_fk', 'blocks_phase2_4_check', 'Assign a valid default_pool_id before enabling the Flow, or change the Flow status to DRAFT/DISABLED.', true, 'Current Source Flow model requires an active Flow to have a default Agent Pool.'),
    ('DFLOW_DEFAULT_POOL_SOURCE_MISMATCH', 'WARNING', 'REVIEW_REQUIRED', 'CONFIRM_SHARED_POOL_OR_REMAP', 'manual', 'review_before_fk', 'not_applicable', 'Confirm cross-source shared-pool routing is intentional; otherwise remap the Flow to a pool with the same source_system.', true, 'This is not always invalid because shared pools may be allowed later by explicit policy.'),
    ('DFLOW_STATUS_INVALID', 'WARNING', 'NORMALIZABLE_ENUM', 'NORMALIZE_FLOW_STATUS', 'candidate_auto', 'not_applicable', 'blocks_phase2_4_check', 'Normalize status to DRAFT, ACTIVE, DISABLED, INACTIVE or RETIRED based on business state.', true, 'Can be scripted only after mapping legacy statuses.'),

    ('APOOL_SOURCE_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'CREATE_OR_REMAP_POOL_SOURCE', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Create the missing source_systems row, remap agent_pools.source_system, or set source_system to * only if explicitly shared.', true, 'Pool source scope affects all routing into the pool.'),
    ('APOOL_SELECTION_STRATEGY_UNSUPPORTED', 'BLOCKER', 'SAFE_NORMALIZATION', 'NORMALIZE_POOL_SELECTION_STRATEGY', 'candidate_auto', 'not_applicable', 'blocks_phase2_4_check', 'Normalize unsupported selection_strategy to LOWEST_LOAD unless an approved migration rule maps it to WEIGHTED_SCORE or MANUAL_ONLY.', false, 'LOWEST_LOAD is the safest supported default for unsupported historical strategies.'),
    ('APOOL_STATUS_INVALID', 'WARNING', 'NORMALIZABLE_ENUM', 'NORMALIZE_POOL_STATUS', 'candidate_auto', 'not_applicable', 'blocks_phase2_4_check', 'Normalize status to ACTIVE, DISABLED, INACTIVE, RETIRED or DRAFT.', true, 'Requires legacy status mapping.'),
    ('APOOL_TYPE_INVALID', 'WARNING', 'NORMALIZABLE_ENUM', 'NORMALIZE_POOL_TYPE', 'candidate_auto', 'not_applicable', 'blocks_phase2_4_check', 'Normalize pool_type to RESOLUTION, WORK_QUEUE, TRIAGE, MANUAL or ESCALATION.', true, 'Requires legacy pool type mapping.'),

    ('APOOL_MEMBER_POOL_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'CREATE_POOL_OR_REMOVE_MEMBER', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Create the missing pool or remove/archive the orphan agent_pool_members row after confirming it is not needed.', true, 'Deleting membership automatically can silently remove routing capacity.'),
    ('APOOL_MEMBER_AGENT_PROFILE_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'CREATE_PROFILE_OR_REMOVE_MEMBER', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Create the missing agent_profiles row in the same tenant or remove/archive the Pool member.', true, 'Business Agent profile is the Core authority for Pool membership.'),
    ('APOOL_MEMBER_AGENT_RUNTIME_MISSING', 'WARNING', 'RUNTIME_OBSERVATION', 'WAIT_FOR_RUNTIME_OR_REVIEW_AGENT_ID', 'manual', 'not_applicable', 'not_applicable', 'Confirm whether the Agent has not connected yet or the member references the wrong agent_id.', true, 'Runtime Agent rows may legitimately be absent before first connection.'),
    ('APOOL_MEMBER_STATUS_INVALID', 'WARNING', 'NORMALIZABLE_ENUM', 'NORMALIZE_MEMBER_STATUS', 'candidate_auto', 'not_applicable', 'blocks_phase2_4_check', 'Normalize member_status to ACTIVE, DISABLED, INACTIVE, RETIRED or SUSPENDED.', true, 'Requires legacy status mapping.'),
    ('APOOL_MEMBER_WEIGHT_INVALID', 'BLOCKER', 'SAFE_NORMALIZATION', 'SET_MEMBER_WEIGHT_DEFAULT', 'candidate_auto', 'not_applicable', 'blocks_phase2_4_check', 'Set null or non-positive weight to 1.', false, 'Weight=1 is already the current default and preserves eligibility without preferential scoring.'),
    ('APOOL_MEMBER_PRIORITY_INVALID', 'BLOCKER', 'SAFE_NORMALIZATION', 'SET_MEMBER_PRIORITY_DEFAULT', 'candidate_auto', 'not_applicable', 'blocks_phase2_4_check', 'Set null or negative priority to 100.', false, 'Priority=100 is already the current default.'),

    ('DPOLICY_FLOW_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'CREATE_FLOW_OR_REMOVE_RULE', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Create the missing dispatch_flows row or remove/archive the orphan dispatch_policies row.', true, 'A rule cannot be evaluated without an owning Source Flow.'),
    ('DPOLICY_SOURCE_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'CREATE_OR_REMAP_RULE_SOURCE', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Create the missing source_systems row or remap dispatch_policies.source_system to an existing source_system.', true, 'Rule source_system must remain aligned with the owning Flow.'),
    ('DPOLICY_SOURCE_FLOW_MISMATCH', 'BLOCKER', 'SAFE_NORMALIZATION', 'ALIGN_RULE_SOURCE_TO_FLOW', 'candidate_auto', 'blocks_phase2_3_fk', 'not_applicable', 'Set dispatch_policies.source_system to the owning dispatch_flows.source_system after confirming the rule belongs to that Flow.', true, 'This can be automated only when rule ownership is already trusted.'),
    ('DPOLICY_TARGET_POOL_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'CREATE_OR_REMAP_RULE_TARGET_POOL', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Create the missing target Agent Pool, remap target_pool_id, or clear target_pool_id to fall back to Flow default only if product behavior is accepted.', true, 'Clearing a target pool changes rule-specific routing.'),
    ('DPOLICY_ROUTING_STRATEGY_UNSUPPORTED', 'WARNING', 'SAFE_NORMALIZATION', 'NORMALIZE_RULE_ROUTING_STRATEGY', 'candidate_auto', 'not_applicable', 'blocks_phase2_4_check', 'Normalize unsupported routing_strategy to LOWEST_LOAD unless an approved migration rule maps it to WEIGHTED_SCORE or MANUAL_ONLY.', false, 'Rule-level strategy is secondary to Pool strategy but should be constrained.'),
    ('DPOLICY_PRIORITY_INVALID', 'BLOCKER', 'SAFE_NORMALIZATION', 'SET_RULE_PRIORITY_DEFAULT', 'candidate_auto', 'not_applicable', 'blocks_phase2_4_check', 'Set null or negative priority to 100.', false, 'Priority=100 is already the current default.'),

    ('LEGACY_CAP_FLOW_MISSING', 'WARNING', 'LEGACY_REFERENCE_CLEANUP', 'ARCHIVE_OR_REMOVE_LEGACY_CAPABILITY', 'manual', 'not_applicable', 'not_applicable', 'Archive or remove preserved reference-only capability rows after confirming the legacy Flow no longer exists.', true, 'Reference-only rows do not block current routing but should not remain misleading.'),
    ('LEGACY_CAP_RULE_MISSING', 'WARNING', 'LEGACY_REFERENCE_CLEANUP', 'ARCHIVE_OR_REMOVE_LEGACY_RULE_CAPABILITY', 'manual', 'not_applicable', 'not_applicable', 'Archive or remove preserved reference-only rule capability rows after confirming the rule no longer exists.', true, 'Reference-only rows are intentionally not FK-blocking in Phase 2-3.'),
    ('LEGACY_FLOW_AGENT_FLOW_MISSING', 'WARNING', 'LEGACY_REFERENCE_CLEANUP', 'ARCHIVE_OR_REMOVE_LEGACY_FLOW_AGENT', 'manual', 'not_applicable', 'not_applicable', 'Archive or remove preserved reference-only direct Agent rows after confirming the Flow no longer exists.', true, 'Direct Agent rows are not part of current routing.'),
    ('LEGACY_FLOW_AGENT_PROFILE_MISSING', 'WARNING', 'LEGACY_REFERENCE_CLEANUP', 'ARCHIVE_OR_REMOVE_LEGACY_AGENT_REFERENCE', 'manual', 'not_applicable', 'not_applicable', 'Archive or remove preserved direct Agent references whose Agent profile no longer exists.', true, 'Direct Agent rows remain reference-only.'),

    ('TASK_SOURCE_MISSING', 'WARNING', 'HISTORY_REPAIR_OR_ACCEPT', 'RECREATE_SOURCE_FOR_HISTORY_OR_ACCEPT', 'manual', 'not_applicable', 'not_applicable', 'Recreate source_systems for historical task readability or explicitly accept the missing historical reference.', true, 'Historical tasks may outlive mutable configuration.'),
    ('TASK_MATCHED_FLOW_MISSING', 'WARNING', 'HISTORY_REPAIR_OR_ACCEPT', 'RECREATE_FLOW_FOR_HISTORY_OR_ACCEPT', 'manual', 'not_applicable', 'not_applicable', 'Recreate archived Flow metadata for history or accept that the historical matched_flow_id is no longer enforced.', true, 'Do not FK historical matched_flow_id until archival policy exists.'),
    ('TASK_MATCHED_RULE_MISSING', 'WARNING', 'HISTORY_REPAIR_OR_ACCEPT', 'RECREATE_RULE_FOR_HISTORY_OR_ACCEPT', 'manual', 'not_applicable', 'not_applicable', 'Recreate archived Rule metadata for history or accept that the historical matched_rule_id is no longer enforced.', true, 'Do not FK historical matched_rule_id until archival policy exists.'),
    ('TASK_ASSIGNED_POOL_MISSING', 'WARNING', 'HISTORY_REPAIR_OR_ACCEPT', 'RECREATE_POOL_FOR_HISTORY_OR_ACCEPT', 'manual', 'not_applicable', 'not_applicable', 'Recreate archived Pool metadata for history or accept the historical assigned_pool_id as denormalized evidence.', true, 'History references should be handled by snapshot/evidence policy.'),
    ('TASK_TARGET_POOL_MISSING', 'WARNING', 'HISTORY_REPAIR_OR_ACCEPT', 'RECREATE_TARGET_POOL_FOR_HISTORY_OR_ACCEPT', 'manual', 'not_applicable', 'not_applicable', 'Recreate archived target Pool metadata for history or accept the historical target_pool_id as denormalized evidence.', true, 'History references should not block current FK work.'),

    ('ASSIGNMENT_TASK_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'RECREATE_TASK_OR_ARCHIVE_ASSIGNMENT', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Recreate the missing Task from evidence or archive/remove the orphan task_assignments row after support review.', true, 'Assignment cannot exist without a Task in the current lifecycle.'),
    ('ASSIGNMENT_AGENT_RUNTIME_MISSING', 'WARNING', 'RUNTIME_OBSERVATION', 'WAIT_FOR_RUNTIME_OR_REVIEW_AGENT_ID', 'manual', 'not_applicable', 'not_applicable', 'Confirm whether the Agent runtime row has not connected yet or the assignment references an invalid agent_id.', true, 'Historical assignment may reference a disconnected Agent.'),
    ('ASSIGNMENT_POOL_MISSING', 'WARNING', 'HISTORY_REPAIR_OR_ACCEPT', 'RECREATE_POOL_FOR_ASSIGNMENT_HISTORY_OR_ACCEPT', 'manual', 'not_applicable', 'not_applicable', 'Recreate archived Pool metadata or accept the historical assignment pool reference as evidence only.', true, 'Do not FK history pool columns until archival model is settled.'),
    ('DREQUEST_ASSIGNMENT_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'RECREATE_ASSIGNMENT_OR_ARCHIVE_REQUEST', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Recreate the missing assignment or archive/remove the orphan dispatch_requests row after support review.', true, 'Dispatch request cannot safely execute without assignment context.'),
    ('DREQUEST_TASK_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'RECREATE_TASK_OR_ARCHIVE_REQUEST', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Recreate the missing Task or archive/remove the orphan dispatch_requests row after support review.', true, 'Dispatch request cannot safely execute without task context.'),
    ('ROUTING_DECISION_TASK_MISSING', 'WARNING', 'HISTORY_REPAIR_OR_ACCEPT', 'RECREATE_TASK_FOR_DECISION_OR_ACCEPT', 'manual', 'not_applicable', 'not_applicable', 'Recreate Task evidence for historical routing decision or accept it as detached diagnostic history.', true, 'Routing decision history should later use snapshot policy.'),
    ('CALLBACK_TASK_MISSING', 'WARNING', 'HISTORY_REPAIR_OR_ACCEPT', 'RECREATE_TASK_FOR_CALLBACK_OR_ACCEPT', 'manual', 'not_applicable', 'not_applicable', 'Recreate Task evidence for callback history or accept it as detached callback evidence.', true, 'Callback history may remain detached until archival policy exists.'),
    ('CALLBACK_ASSIGNMENT_MISSING', 'WARNING', 'HISTORY_REPAIR_OR_ACCEPT', 'RECREATE_ASSIGNMENT_FOR_CALLBACK_OR_ACCEPT', 'manual', 'not_applicable', 'not_applicable', 'Recreate assignment evidence for callback history or accept it as detached callback evidence.', true, 'Callback assignment FK should wait for history policy.')
),
integrity_findings as (
  -- Source System and Flow references.
  select 'DFLOW_SOURCE_MISSING' as check_id,
         'BLOCKER' as severity,
         f.tenant_id,
         'dispatch_flows' as entity,
         f.flow_id as entity_id,
         'source_systems.source_system_id' as reference_type,
         f.source_system as reference_id,
         'Source Flow references a source_system that does not exist in the same tenant.' as message
    from dispatch_flows f
    left join source_systems s
      on s.tenant_id = f.tenant_id
     and s.source_system_id = f.source_system
   where s.source_system_id is null

  union all
  select 'DFLOW_DEFAULT_POOL_MISSING', 'BLOCKER', f.tenant_id,
         'dispatch_flows', f.flow_id,
         'agent_pools.pool_id', f.default_pool_id,
         'Source Flow default_pool_id does not exist in the same tenant.'
    from dispatch_flows f
    left join agent_pools p
      on p.tenant_id = f.tenant_id
     and p.pool_id = f.default_pool_id
   where f.default_pool_id is not null
     and p.pool_id is null

  union all
  select 'DFLOW_ACTIVE_WITHOUT_DEFAULT_POOL', 'BLOCKER', f.tenant_id,
         'dispatch_flows', f.flow_id,
         'agent_pools.pool_id', f.default_pool_id,
         'ACTIVE Source Flow has no default_pool_id; current model requires a default Agent Pool.'
    from dispatch_flows f
   where upper(coalesce(f.status, 'DRAFT')) in ('ACTIVE', 'ENABLED')
     and nullif(trim(f.default_pool_id), '') is null

  union all
  select 'DFLOW_DEFAULT_POOL_SOURCE_MISMATCH', 'WARNING', f.tenant_id,
         'dispatch_flows', f.flow_id,
         'agent_pools.source_system', p.source_system,
         'Default Agent Pool source_system differs from the Source Flow source_system. Review whether this is intentional shared-pool routing.'
    from dispatch_flows f
    join agent_pools p
      on p.tenant_id = f.tenant_id
     and p.pool_id = f.default_pool_id
   where nullif(trim(coalesce(p.source_system, '')), '') is not null
     and p.source_system <> f.source_system

  union all
  select 'DFLOW_STATUS_INVALID', 'WARNING', f.tenant_id,
         'dispatch_flows', f.flow_id,
         'dispatch_flows.status', f.status,
         'Source Flow status is outside the current allowed status set.'
    from dispatch_flows f
   where upper(coalesce(f.status, '')) not in ('DRAFT', 'ACTIVE', 'ENABLED', 'DISABLED', 'INACTIVE', 'RETIRED')

  -- Agent Pool master data.
  union all
  select 'APOOL_SOURCE_MISSING', 'BLOCKER', p.tenant_id,
         'agent_pools', p.pool_id,
         'source_systems.source_system_id', p.source_system,
         'Agent Pool source_system does not exist in the same tenant.'
    from agent_pools p
    left join source_systems s
      on s.tenant_id = p.tenant_id
     and s.source_system_id = p.source_system
   where nullif(trim(coalesce(p.source_system, '')), '') is not null
     and p.source_system <> '*'
     and s.source_system_id is null

  union all
  select 'APOOL_SELECTION_STRATEGY_UNSUPPORTED', 'BLOCKER', p.tenant_id,
         'agent_pools', p.pool_id,
         'agent_pools.selection_strategy', p.selection_strategy,
         'Agent Pool selection_strategy is not supported by the current Runtime contract.'
    from agent_pools p
   where upper(coalesce(p.selection_strategy, '')) not in ('LOWEST_LOAD', 'WEIGHTED_SCORE', 'MANUAL_ONLY')

  union all
  select 'APOOL_STATUS_INVALID', 'WARNING', p.tenant_id,
         'agent_pools', p.pool_id,
         'agent_pools.status', p.status,
         'Agent Pool status is outside the current allowed status set.'
    from agent_pools p
   where upper(coalesce(p.status, '')) not in ('ACTIVE', 'ENABLED', 'DISABLED', 'INACTIVE', 'RETIRED', 'DRAFT')

  union all
  select 'APOOL_TYPE_INVALID', 'WARNING', p.tenant_id,
         'agent_pools', p.pool_id,
         'agent_pools.pool_type', p.pool_type,
         'Agent Pool pool_type is outside the current known type set.'
    from agent_pools p
   where upper(coalesce(p.pool_type, '')) not in ('RESOLUTION', 'WORK_QUEUE', 'TRIAGE', 'MANUAL', 'ESCALATION')

  -- Pool membership.
  union all
  select 'APOOL_MEMBER_POOL_MISSING', 'BLOCKER', m.tenant_id,
         'agent_pool_members', concat(m.pool_id, '/', m.agent_id),
         'agent_pools.pool_id', m.pool_id,
         'Pool member references an Agent Pool that does not exist in the same tenant.'
    from agent_pool_members m
    left join agent_pools p
      on p.tenant_id = m.tenant_id
     and p.pool_id = m.pool_id
   where p.pool_id is null

  union all
  select 'APOOL_MEMBER_AGENT_PROFILE_MISSING', 'BLOCKER', m.tenant_id,
         'agent_pool_members', concat(m.pool_id, '/', m.agent_id),
         'agent_profiles.agent_id', m.agent_id,
         'Pool member references an Agent profile that does not exist in the same tenant.'
    from agent_pool_members m
    left join agent_profiles ap
      on ap.tenant_id = m.tenant_id
     and ap.agent_id = m.agent_id
   where ap.agent_id is null

  union all
  select 'APOOL_MEMBER_AGENT_RUNTIME_MISSING', 'WARNING', m.tenant_id,
         'agent_pool_members', concat(m.pool_id, '/', m.agent_id),
         'agents.agent_id', m.agent_id,
         'Pool member has no runtime agent row. This may be valid before first connection, but it blocks live dispatch.'
    from agent_pool_members m
    left join agents a
      on a.agent_id = m.agent_id
   where a.agent_id is null

  union all
  select 'APOOL_MEMBER_STATUS_INVALID', 'WARNING', m.tenant_id,
         'agent_pool_members', concat(m.pool_id, '/', m.agent_id),
         'agent_pool_members.member_status', m.member_status,
         'Pool member status is outside the current allowed status set.'
    from agent_pool_members m
   where upper(coalesce(m.member_status, '')) not in ('ACTIVE', 'ENABLED', 'DISABLED', 'INACTIVE', 'RETIRED', 'SUSPENDED')

  union all
  select 'APOOL_MEMBER_WEIGHT_INVALID', 'BLOCKER', m.tenant_id,
         'agent_pool_members', concat(m.pool_id, '/', m.agent_id),
         'agent_pool_members.weight', m.weight::text,
         'Pool member weight must be greater than zero before WEIGHTED_SCORE can be constrained.'
    from agent_pool_members m
   where m.weight is null or m.weight <= 0

  union all
  select 'APOOL_MEMBER_PRIORITY_INVALID', 'BLOCKER', m.tenant_id,
         'agent_pool_members', concat(m.pool_id, '/', m.agent_id),
         'agent_pool_members.priority', m.priority::text,
         'Pool member priority must be greater than or equal to zero before priority constraints can be added.'
    from agent_pool_members m
   where m.priority is null or m.priority < 0

  -- Rule references.
  union all
  select 'DPOLICY_FLOW_MISSING', 'BLOCKER', r.tenant_id,
         'dispatch_policies', r.policy_id,
         'dispatch_flows.flow_id', r.flow_id,
         'Dispatch rule references a Source Flow that does not exist in the same tenant.'
    from dispatch_policies r
    left join dispatch_flows f
      on f.tenant_id = r.tenant_id
     and f.flow_id = r.flow_id
   where f.flow_id is null

  union all
  select 'DPOLICY_SOURCE_MISSING', 'BLOCKER', r.tenant_id,
         'dispatch_policies', r.policy_id,
         'source_systems.source_system_id', r.source_system,
         'Dispatch rule source_system does not exist in the same tenant.'
    from dispatch_policies r
    left join source_systems s
      on s.tenant_id = r.tenant_id
     and s.source_system_id = r.source_system
   where s.source_system_id is null

  union all
  select 'DPOLICY_SOURCE_FLOW_MISMATCH', 'BLOCKER', r.tenant_id,
         'dispatch_policies', r.policy_id,
         'dispatch_flows.source_system', f.source_system,
         'Dispatch rule source_system differs from its owning Source Flow source_system.'
    from dispatch_policies r
    join dispatch_flows f
      on f.tenant_id = r.tenant_id
     and f.flow_id = r.flow_id
   where r.source_system <> f.source_system

  union all
  select 'DPOLICY_TARGET_POOL_MISSING', 'BLOCKER', r.tenant_id,
         'dispatch_policies', r.policy_id,
         'agent_pools.pool_id', r.target_pool_id,
         'Dispatch rule target_pool_id does not exist in the same tenant.'
    from dispatch_policies r
    left join agent_pools p
      on p.tenant_id = r.tenant_id
     and p.pool_id = r.target_pool_id
   where r.target_pool_id is not null
     and p.pool_id is null

  union all
  select 'DPOLICY_ROUTING_STRATEGY_UNSUPPORTED', 'WARNING', r.tenant_id,
         'dispatch_policies', r.policy_id,
         'dispatch_policies.routing_strategy', r.routing_strategy,
         'Dispatch rule routing_strategy is outside current supported Pool strategy names.'
    from dispatch_policies r
   where upper(coalesce(r.routing_strategy, '')) not in ('LOWEST_LOAD', 'WEIGHTED_SCORE', 'MANUAL_ONLY')

  union all
  select 'DPOLICY_PRIORITY_INVALID', 'BLOCKER', r.tenant_id,
         'dispatch_policies', r.policy_id,
         'dispatch_policies.priority', r.priority::text,
         'Dispatch rule priority must be greater than or equal to zero.'
    from dispatch_policies r
   where r.priority is null or r.priority < 0

  -- Preserved legacy/reference-only child rows.
  union all
  select 'LEGACY_CAP_FLOW_MISSING', 'WARNING', c.tenant_id,
         'flow_required_capabilities', c.id,
         'dispatch_flows.flow_id', c.flow_id,
         'Preserved legacy/reference-only capability row points to a missing Source Flow.'
    from flow_required_capabilities c
    left join dispatch_flows f
      on f.tenant_id = c.tenant_id
     and f.flow_id = c.flow_id
   where f.flow_id is null

  union all
  select 'LEGACY_CAP_RULE_MISSING', 'WARNING', c.tenant_id,
         'flow_required_capabilities', c.id,
         'dispatch_policies.policy_id', c.rule_id,
         'Preserved legacy/reference-only capability row points to a missing Dispatch rule.'
    from flow_required_capabilities c
    left join dispatch_policies r
      on r.tenant_id = c.tenant_id
     and r.policy_id = c.rule_id
   where c.rule_id is not null
     and r.policy_id is null

  union all
  select 'LEGACY_FLOW_AGENT_FLOW_MISSING', 'WARNING', fa.tenant_id,
         'flow_agent_assignments', fa.id,
         'dispatch_flows.flow_id', fa.flow_id,
         'Preserved legacy/reference-only direct Agent row points to a missing Source Flow.'
    from flow_agent_assignments fa
    left join dispatch_flows f
      on f.tenant_id = fa.tenant_id
     and f.flow_id = fa.flow_id
   where f.flow_id is null

  union all
  select 'LEGACY_FLOW_AGENT_PROFILE_MISSING', 'WARNING', fa.tenant_id,
         'flow_agent_assignments', fa.id,
         'agent_profiles.agent_id', fa.agent_id,
         'Preserved legacy/reference-only direct Agent row points to a missing Agent profile.'
    from flow_agent_assignments fa
    left join agent_profiles ap
      on ap.tenant_id = fa.tenant_id
     and ap.agent_id = fa.agent_id
   where ap.agent_id is null

  -- Task and assignment history references.
  union all
  select 'TASK_SOURCE_MISSING', 'WARNING', t.tenant_id,
         'tasks', t.task_id,
         'source_systems.source_system_id', t.source_system,
         'Task source_system is missing from the tenant source system registry.'
    from tasks t
    left join source_systems s
      on s.tenant_id = t.tenant_id
     and s.source_system_id = t.source_system
   where t.tenant_id is not null
     and t.source_system is not null
     and s.source_system_id is null

  union all
  select 'TASK_MATCHED_FLOW_MISSING', 'WARNING', t.tenant_id,
         'tasks', t.task_id,
         'dispatch_flows.flow_id', t.matched_flow_id,
         'Task matched_flow_id no longer exists in the same tenant.'
    from tasks t
    left join dispatch_flows f
      on f.tenant_id = t.tenant_id
     and f.flow_id = t.matched_flow_id
   where t.tenant_id is not null
     and t.matched_flow_id is not null
     and f.flow_id is null

  union all
  select 'TASK_MATCHED_RULE_MISSING', 'WARNING', t.tenant_id,
         'tasks', t.task_id,
         'dispatch_policies.policy_id', t.matched_rule_id,
         'Task matched_rule_id no longer exists in the same tenant.'
    from tasks t
    left join dispatch_policies r
      on r.tenant_id = t.tenant_id
     and r.policy_id = t.matched_rule_id
   where t.tenant_id is not null
     and t.matched_rule_id is not null
     and r.policy_id is null

  union all
  select 'TASK_ASSIGNED_POOL_MISSING', 'WARNING', t.tenant_id,
         'tasks', t.task_id,
         'agent_pools.pool_id', t.assigned_pool_id,
         'Task assigned_pool_id no longer exists in the same tenant.'
    from tasks t
    left join agent_pools p
      on p.tenant_id = t.tenant_id
     and p.pool_id = t.assigned_pool_id
   where t.tenant_id is not null
     and t.assigned_pool_id is not null
     and p.pool_id is null

  union all
  select 'TASK_TARGET_POOL_MISSING', 'WARNING', t.tenant_id,
         'tasks', t.task_id,
         'agent_pools.pool_id', t.target_pool_id,
         'Task target_pool_id no longer exists in the same tenant.'
    from tasks t
    left join agent_pools p
      on p.tenant_id = t.tenant_id
     and p.pool_id = t.target_pool_id
   where t.tenant_id is not null
     and t.target_pool_id is not null
     and p.pool_id is null

  union all
  select 'ASSIGNMENT_TASK_MISSING', 'BLOCKER', ta_tenant.tenant_id,
         'task_assignments', ta.assignment_id,
         'tasks.task_id', ta.task_id,
         'Task assignment references a task_id that does not exist.'
    from task_assignments ta
    left join tasks t
      on t.task_id = ta.task_id
    left join lateral (select coalesce(t.tenant_id, '<unknown>') as tenant_id) ta_tenant on true
   where t.task_id is null

  union all
  select 'ASSIGNMENT_AGENT_RUNTIME_MISSING', 'WARNING', coalesce(t.tenant_id, '<unknown>'),
         'task_assignments', ta.assignment_id,
         'agents.agent_id', ta.agent_id,
         'Task assignment references an Agent runtime row that does not exist.'
    from task_assignments ta
    left join tasks t
      on t.task_id = ta.task_id
    left join agents a
      on a.agent_id = ta.agent_id
   where a.agent_id is null

  union all
  select 'ASSIGNMENT_POOL_MISSING', 'WARNING', t.tenant_id,
         'task_assignments', ta.assignment_id,
         'agent_pools.pool_id', coalesce(ta.assigned_pool_id, ta.target_pool_id),
         'Task assignment references an assigned/target pool that no longer exists in the task tenant.'
    from task_assignments ta
    join tasks t
      on t.task_id = ta.task_id
    left join agent_pools p
      on p.tenant_id = t.tenant_id
     and p.pool_id = coalesce(ta.assigned_pool_id, ta.target_pool_id)
   where coalesce(ta.assigned_pool_id, ta.target_pool_id) is not null
     and p.pool_id is null

  union all
  select 'DREQUEST_ASSIGNMENT_MISSING', 'BLOCKER', coalesce(t.tenant_id, '<unknown>'),
         'dispatch_requests', dr.dispatch_request_id,
         'task_assignments.assignment_id', dr.assignment_id,
         'Dispatch request references an assignment_id that does not exist.'
    from dispatch_requests dr
    left join task_assignments ta
      on ta.assignment_id = dr.assignment_id
    left join tasks t
      on t.task_id = coalesce(dr.task_id, ta.task_id)
   where dr.assignment_id is not null
     and ta.assignment_id is null

  union all
  select 'DREQUEST_TASK_MISSING', 'BLOCKER', '<unknown>',
         'dispatch_requests', dr.dispatch_request_id,
         'tasks.task_id', dr.task_id,
         'Dispatch request references a task_id that does not exist.'
    from dispatch_requests dr
    left join tasks t
      on t.task_id = dr.task_id
   where dr.task_id is not null
     and t.task_id is null

  union all
  select 'ROUTING_DECISION_TASK_MISSING', 'WARNING', coalesce(rd.tenant_id, '<unknown>'),
         'routing_decisions', rd.decision_id,
         'tasks.task_id', rd.task_id,
         'Routing decision references a task_id that does not exist.'
    from routing_decisions rd
    left join tasks t
      on t.task_id = rd.task_id
   where rd.task_id is not null
     and t.task_id is null

  union all
  select 'CALLBACK_TASK_MISSING', 'WARNING', coalesce(t.tenant_id, '<unknown>'),
         'task_callbacks', cb.callback_id,
         'tasks.task_id', cb.task_id,
         'Task callback references a task_id that does not exist.'
    from task_callbacks cb
    left join tasks t
      on t.task_id = cb.task_id
   where cb.task_id is not null
     and t.task_id is null

  union all
  select 'CALLBACK_ASSIGNMENT_MISSING', 'WARNING', coalesce(t.tenant_id, '<unknown>'),
         'task_callbacks', cb.callback_id,
         'task_assignments.assignment_id', cb.assignment_id,
         'Task callback references an assignment_id that does not exist.'
    from task_callbacks cb
    left join task_assignments ta
      on ta.assignment_id = cb.assignment_id
    left join tasks t
      on t.task_id = coalesce(cb.task_id, ta.task_id)
   where cb.assignment_id is not null
     and ta.assignment_id is null
)
select f.severity,
       p.repair_class,
       p.automation_policy,
       p.fk_readiness,
       p.check_readiness,
       count(*) as finding_count
  from integrity_findings f
  join repair_plan p
    on p.check_id = f.check_id
 group by f.severity,
          p.repair_class,
          p.automation_policy,
          p.fk_readiness,
          p.check_readiness
 order by case f.severity when 'BLOCKER' then 1 when 'WARNING' then 2 else 3 end,
          p.repair_class,
          p.automation_policy;

\echo '== Phase 2-2 Constraint Readiness Blockers =='

with repair_plan(check_id, severity, repair_class, default_action, automation_policy, fk_readiness, check_readiness, proposed_fix, manual_review_required, notes) as (
  values
    ('DFLOW_SOURCE_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'CREATE_OR_REMAP_SOURCE_SYSTEM', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Create the missing source_systems row in the same tenant or remap the Source Flow to an existing source_system.', true, 'Do not auto-create source systems because display name, owner and lifecycle status are business decisions.'),
    ('DFLOW_DEFAULT_POOL_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'CREATE_OR_REMAP_DEFAULT_POOL', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Create the missing agent_pools row or remap dispatch_flows.default_pool_id to an existing pool in the same tenant.', true, 'Do not auto-create pools because membership and operating model must be confirmed.'),
    ('DFLOW_ACTIVE_WITHOUT_DEFAULT_POOL', 'BLOCKER', 'MANUAL_REQUIRED', 'ASSIGN_DEFAULT_POOL_OR_DISABLE_FLOW', 'manual', 'blocks_phase2_3_fk', 'blocks_phase2_4_check', 'Assign a valid default_pool_id before enabling the Flow, or change the Flow status to DRAFT/DISABLED.', true, 'Current Source Flow model requires an active Flow to have a default Agent Pool.'),
    ('DFLOW_DEFAULT_POOL_SOURCE_MISMATCH', 'WARNING', 'REVIEW_REQUIRED', 'CONFIRM_SHARED_POOL_OR_REMAP', 'manual', 'review_before_fk', 'not_applicable', 'Confirm cross-source shared-pool routing is intentional; otherwise remap the Flow to a pool with the same source_system.', true, 'This is not always invalid because shared pools may be allowed later by explicit policy.'),
    ('DFLOW_STATUS_INVALID', 'WARNING', 'NORMALIZABLE_ENUM', 'NORMALIZE_FLOW_STATUS', 'candidate_auto', 'not_applicable', 'blocks_phase2_4_check', 'Normalize status to DRAFT, ACTIVE, DISABLED, INACTIVE or RETIRED based on business state.', true, 'Can be scripted only after mapping legacy statuses.'),

    ('APOOL_SOURCE_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'CREATE_OR_REMAP_POOL_SOURCE', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Create the missing source_systems row, remap agent_pools.source_system, or set source_system to * only if explicitly shared.', true, 'Pool source scope affects all routing into the pool.'),
    ('APOOL_SELECTION_STRATEGY_UNSUPPORTED', 'BLOCKER', 'SAFE_NORMALIZATION', 'NORMALIZE_POOL_SELECTION_STRATEGY', 'candidate_auto', 'not_applicable', 'blocks_phase2_4_check', 'Normalize unsupported selection_strategy to LOWEST_LOAD unless an approved migration rule maps it to WEIGHTED_SCORE or MANUAL_ONLY.', false, 'LOWEST_LOAD is the safest supported default for unsupported historical strategies.'),
    ('APOOL_STATUS_INVALID', 'WARNING', 'NORMALIZABLE_ENUM', 'NORMALIZE_POOL_STATUS', 'candidate_auto', 'not_applicable', 'blocks_phase2_4_check', 'Normalize status to ACTIVE, DISABLED, INACTIVE, RETIRED or DRAFT.', true, 'Requires legacy status mapping.'),
    ('APOOL_TYPE_INVALID', 'WARNING', 'NORMALIZABLE_ENUM', 'NORMALIZE_POOL_TYPE', 'candidate_auto', 'not_applicable', 'blocks_phase2_4_check', 'Normalize pool_type to RESOLUTION, WORK_QUEUE, TRIAGE, MANUAL or ESCALATION.', true, 'Requires legacy pool type mapping.'),

    ('APOOL_MEMBER_POOL_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'CREATE_POOL_OR_REMOVE_MEMBER', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Create the missing pool or remove/archive the orphan agent_pool_members row after confirming it is not needed.', true, 'Deleting membership automatically can silently remove routing capacity.'),
    ('APOOL_MEMBER_AGENT_PROFILE_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'CREATE_PROFILE_OR_REMOVE_MEMBER', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Create the missing agent_profiles row in the same tenant or remove/archive the Pool member.', true, 'Business Agent profile is the Core authority for Pool membership.'),
    ('APOOL_MEMBER_AGENT_RUNTIME_MISSING', 'WARNING', 'RUNTIME_OBSERVATION', 'WAIT_FOR_RUNTIME_OR_REVIEW_AGENT_ID', 'manual', 'not_applicable', 'not_applicable', 'Confirm whether the Agent has not connected yet or the member references the wrong agent_id.', true, 'Runtime Agent rows may legitimately be absent before first connection.'),
    ('APOOL_MEMBER_STATUS_INVALID', 'WARNING', 'NORMALIZABLE_ENUM', 'NORMALIZE_MEMBER_STATUS', 'candidate_auto', 'not_applicable', 'blocks_phase2_4_check', 'Normalize member_status to ACTIVE, DISABLED, INACTIVE, RETIRED or SUSPENDED.', true, 'Requires legacy status mapping.'),
    ('APOOL_MEMBER_WEIGHT_INVALID', 'BLOCKER', 'SAFE_NORMALIZATION', 'SET_MEMBER_WEIGHT_DEFAULT', 'candidate_auto', 'not_applicable', 'blocks_phase2_4_check', 'Set null or non-positive weight to 1.', false, 'Weight=1 is already the current default and preserves eligibility without preferential scoring.'),
    ('APOOL_MEMBER_PRIORITY_INVALID', 'BLOCKER', 'SAFE_NORMALIZATION', 'SET_MEMBER_PRIORITY_DEFAULT', 'candidate_auto', 'not_applicable', 'blocks_phase2_4_check', 'Set null or negative priority to 100.', false, 'Priority=100 is already the current default.'),

    ('DPOLICY_FLOW_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'CREATE_FLOW_OR_REMOVE_RULE', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Create the missing dispatch_flows row or remove/archive the orphan dispatch_policies row.', true, 'A rule cannot be evaluated without an owning Source Flow.'),
    ('DPOLICY_SOURCE_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'CREATE_OR_REMAP_RULE_SOURCE', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Create the missing source_systems row or remap dispatch_policies.source_system to an existing source_system.', true, 'Rule source_system must remain aligned with the owning Flow.'),
    ('DPOLICY_SOURCE_FLOW_MISMATCH', 'BLOCKER', 'SAFE_NORMALIZATION', 'ALIGN_RULE_SOURCE_TO_FLOW', 'candidate_auto', 'blocks_phase2_3_fk', 'not_applicable', 'Set dispatch_policies.source_system to the owning dispatch_flows.source_system after confirming the rule belongs to that Flow.', true, 'This can be automated only when rule ownership is already trusted.'),
    ('DPOLICY_TARGET_POOL_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'CREATE_OR_REMAP_RULE_TARGET_POOL', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Create the missing target Agent Pool, remap target_pool_id, or clear target_pool_id to fall back to Flow default only if product behavior is accepted.', true, 'Clearing a target pool changes rule-specific routing.'),
    ('DPOLICY_ROUTING_STRATEGY_UNSUPPORTED', 'WARNING', 'SAFE_NORMALIZATION', 'NORMALIZE_RULE_ROUTING_STRATEGY', 'candidate_auto', 'not_applicable', 'blocks_phase2_4_check', 'Normalize unsupported routing_strategy to LOWEST_LOAD unless an approved migration rule maps it to WEIGHTED_SCORE or MANUAL_ONLY.', false, 'Rule-level strategy is secondary to Pool strategy but should be constrained.'),
    ('DPOLICY_PRIORITY_INVALID', 'BLOCKER', 'SAFE_NORMALIZATION', 'SET_RULE_PRIORITY_DEFAULT', 'candidate_auto', 'not_applicable', 'blocks_phase2_4_check', 'Set null or negative priority to 100.', false, 'Priority=100 is already the current default.'),

    ('LEGACY_CAP_FLOW_MISSING', 'WARNING', 'LEGACY_REFERENCE_CLEANUP', 'ARCHIVE_OR_REMOVE_LEGACY_CAPABILITY', 'manual', 'not_applicable', 'not_applicable', 'Archive or remove preserved reference-only capability rows after confirming the legacy Flow no longer exists.', true, 'Reference-only rows do not block current routing but should not remain misleading.'),
    ('LEGACY_CAP_RULE_MISSING', 'WARNING', 'LEGACY_REFERENCE_CLEANUP', 'ARCHIVE_OR_REMOVE_LEGACY_RULE_CAPABILITY', 'manual', 'not_applicable', 'not_applicable', 'Archive or remove preserved reference-only rule capability rows after confirming the rule no longer exists.', true, 'Reference-only rows are intentionally not FK-blocking in Phase 2-3.'),
    ('LEGACY_FLOW_AGENT_FLOW_MISSING', 'WARNING', 'LEGACY_REFERENCE_CLEANUP', 'ARCHIVE_OR_REMOVE_LEGACY_FLOW_AGENT', 'manual', 'not_applicable', 'not_applicable', 'Archive or remove preserved reference-only direct Agent rows after confirming the Flow no longer exists.', true, 'Direct Agent rows are not part of current routing.'),
    ('LEGACY_FLOW_AGENT_PROFILE_MISSING', 'WARNING', 'LEGACY_REFERENCE_CLEANUP', 'ARCHIVE_OR_REMOVE_LEGACY_AGENT_REFERENCE', 'manual', 'not_applicable', 'not_applicable', 'Archive or remove preserved direct Agent references whose Agent profile no longer exists.', true, 'Direct Agent rows remain reference-only.'),

    ('TASK_SOURCE_MISSING', 'WARNING', 'HISTORY_REPAIR_OR_ACCEPT', 'RECREATE_SOURCE_FOR_HISTORY_OR_ACCEPT', 'manual', 'not_applicable', 'not_applicable', 'Recreate source_systems for historical task readability or explicitly accept the missing historical reference.', true, 'Historical tasks may outlive mutable configuration.'),
    ('TASK_MATCHED_FLOW_MISSING', 'WARNING', 'HISTORY_REPAIR_OR_ACCEPT', 'RECREATE_FLOW_FOR_HISTORY_OR_ACCEPT', 'manual', 'not_applicable', 'not_applicable', 'Recreate archived Flow metadata for history or accept that the historical matched_flow_id is no longer enforced.', true, 'Do not FK historical matched_flow_id until archival policy exists.'),
    ('TASK_MATCHED_RULE_MISSING', 'WARNING', 'HISTORY_REPAIR_OR_ACCEPT', 'RECREATE_RULE_FOR_HISTORY_OR_ACCEPT', 'manual', 'not_applicable', 'not_applicable', 'Recreate archived Rule metadata for history or accept that the historical matched_rule_id is no longer enforced.', true, 'Do not FK historical matched_rule_id until archival policy exists.'),
    ('TASK_ASSIGNED_POOL_MISSING', 'WARNING', 'HISTORY_REPAIR_OR_ACCEPT', 'RECREATE_POOL_FOR_HISTORY_OR_ACCEPT', 'manual', 'not_applicable', 'not_applicable', 'Recreate archived Pool metadata for history or accept the historical assigned_pool_id as denormalized evidence.', true, 'History references should be handled by snapshot/evidence policy.'),
    ('TASK_TARGET_POOL_MISSING', 'WARNING', 'HISTORY_REPAIR_OR_ACCEPT', 'RECREATE_TARGET_POOL_FOR_HISTORY_OR_ACCEPT', 'manual', 'not_applicable', 'not_applicable', 'Recreate archived target Pool metadata for history or accept the historical target_pool_id as denormalized evidence.', true, 'History references should not block current FK work.'),

    ('ASSIGNMENT_TASK_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'RECREATE_TASK_OR_ARCHIVE_ASSIGNMENT', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Recreate the missing Task from evidence or archive/remove the orphan task_assignments row after support review.', true, 'Assignment cannot exist without a Task in the current lifecycle.'),
    ('ASSIGNMENT_AGENT_RUNTIME_MISSING', 'WARNING', 'RUNTIME_OBSERVATION', 'WAIT_FOR_RUNTIME_OR_REVIEW_AGENT_ID', 'manual', 'not_applicable', 'not_applicable', 'Confirm whether the Agent runtime row has not connected yet or the assignment references an invalid agent_id.', true, 'Historical assignment may reference a disconnected Agent.'),
    ('ASSIGNMENT_POOL_MISSING', 'WARNING', 'HISTORY_REPAIR_OR_ACCEPT', 'RECREATE_POOL_FOR_ASSIGNMENT_HISTORY_OR_ACCEPT', 'manual', 'not_applicable', 'not_applicable', 'Recreate archived Pool metadata or accept the historical assignment pool reference as evidence only.', true, 'Do not FK history pool columns until archival model is settled.'),
    ('DREQUEST_ASSIGNMENT_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'RECREATE_ASSIGNMENT_OR_ARCHIVE_REQUEST', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Recreate the missing assignment or archive/remove the orphan dispatch_requests row after support review.', true, 'Dispatch request cannot safely execute without assignment context.'),
    ('DREQUEST_TASK_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'RECREATE_TASK_OR_ARCHIVE_REQUEST', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Recreate the missing Task or archive/remove the orphan dispatch_requests row after support review.', true, 'Dispatch request cannot safely execute without task context.'),
    ('ROUTING_DECISION_TASK_MISSING', 'WARNING', 'HISTORY_REPAIR_OR_ACCEPT', 'RECREATE_TASK_FOR_DECISION_OR_ACCEPT', 'manual', 'not_applicable', 'not_applicable', 'Recreate Task evidence for historical routing decision or accept it as detached diagnostic history.', true, 'Routing decision history should later use snapshot policy.'),
    ('CALLBACK_TASK_MISSING', 'WARNING', 'HISTORY_REPAIR_OR_ACCEPT', 'RECREATE_TASK_FOR_CALLBACK_OR_ACCEPT', 'manual', 'not_applicable', 'not_applicable', 'Recreate Task evidence for callback history or accept it as detached callback evidence.', true, 'Callback history may remain detached until archival policy exists.'),
    ('CALLBACK_ASSIGNMENT_MISSING', 'WARNING', 'HISTORY_REPAIR_OR_ACCEPT', 'RECREATE_ASSIGNMENT_FOR_CALLBACK_OR_ACCEPT', 'manual', 'not_applicable', 'not_applicable', 'Recreate assignment evidence for callback history or accept it as detached callback evidence.', true, 'Callback assignment FK should wait for history policy.')
),
integrity_findings as (
  -- Source System and Flow references.
  select 'DFLOW_SOURCE_MISSING' as check_id,
         'BLOCKER' as severity,
         f.tenant_id,
         'dispatch_flows' as entity,
         f.flow_id as entity_id,
         'source_systems.source_system_id' as reference_type,
         f.source_system as reference_id,
         'Source Flow references a source_system that does not exist in the same tenant.' as message
    from dispatch_flows f
    left join source_systems s
      on s.tenant_id = f.tenant_id
     and s.source_system_id = f.source_system
   where s.source_system_id is null

  union all
  select 'DFLOW_DEFAULT_POOL_MISSING', 'BLOCKER', f.tenant_id,
         'dispatch_flows', f.flow_id,
         'agent_pools.pool_id', f.default_pool_id,
         'Source Flow default_pool_id does not exist in the same tenant.'
    from dispatch_flows f
    left join agent_pools p
      on p.tenant_id = f.tenant_id
     and p.pool_id = f.default_pool_id
   where f.default_pool_id is not null
     and p.pool_id is null

  union all
  select 'DFLOW_ACTIVE_WITHOUT_DEFAULT_POOL', 'BLOCKER', f.tenant_id,
         'dispatch_flows', f.flow_id,
         'agent_pools.pool_id', f.default_pool_id,
         'ACTIVE Source Flow has no default_pool_id; current model requires a default Agent Pool.'
    from dispatch_flows f
   where upper(coalesce(f.status, 'DRAFT')) in ('ACTIVE', 'ENABLED')
     and nullif(trim(f.default_pool_id), '') is null

  union all
  select 'DFLOW_DEFAULT_POOL_SOURCE_MISMATCH', 'WARNING', f.tenant_id,
         'dispatch_flows', f.flow_id,
         'agent_pools.source_system', p.source_system,
         'Default Agent Pool source_system differs from the Source Flow source_system. Review whether this is intentional shared-pool routing.'
    from dispatch_flows f
    join agent_pools p
      on p.tenant_id = f.tenant_id
     and p.pool_id = f.default_pool_id
   where nullif(trim(coalesce(p.source_system, '')), '') is not null
     and p.source_system <> f.source_system

  union all
  select 'DFLOW_STATUS_INVALID', 'WARNING', f.tenant_id,
         'dispatch_flows', f.flow_id,
         'dispatch_flows.status', f.status,
         'Source Flow status is outside the current allowed status set.'
    from dispatch_flows f
   where upper(coalesce(f.status, '')) not in ('DRAFT', 'ACTIVE', 'ENABLED', 'DISABLED', 'INACTIVE', 'RETIRED')

  -- Agent Pool master data.
  union all
  select 'APOOL_SOURCE_MISSING', 'BLOCKER', p.tenant_id,
         'agent_pools', p.pool_id,
         'source_systems.source_system_id', p.source_system,
         'Agent Pool source_system does not exist in the same tenant.'
    from agent_pools p
    left join source_systems s
      on s.tenant_id = p.tenant_id
     and s.source_system_id = p.source_system
   where nullif(trim(coalesce(p.source_system, '')), '') is not null
     and p.source_system <> '*'
     and s.source_system_id is null

  union all
  select 'APOOL_SELECTION_STRATEGY_UNSUPPORTED', 'BLOCKER', p.tenant_id,
         'agent_pools', p.pool_id,
         'agent_pools.selection_strategy', p.selection_strategy,
         'Agent Pool selection_strategy is not supported by the current Runtime contract.'
    from agent_pools p
   where upper(coalesce(p.selection_strategy, '')) not in ('LOWEST_LOAD', 'WEIGHTED_SCORE', 'MANUAL_ONLY')

  union all
  select 'APOOL_STATUS_INVALID', 'WARNING', p.tenant_id,
         'agent_pools', p.pool_id,
         'agent_pools.status', p.status,
         'Agent Pool status is outside the current allowed status set.'
    from agent_pools p
   where upper(coalesce(p.status, '')) not in ('ACTIVE', 'ENABLED', 'DISABLED', 'INACTIVE', 'RETIRED', 'DRAFT')

  union all
  select 'APOOL_TYPE_INVALID', 'WARNING', p.tenant_id,
         'agent_pools', p.pool_id,
         'agent_pools.pool_type', p.pool_type,
         'Agent Pool pool_type is outside the current known type set.'
    from agent_pools p
   where upper(coalesce(p.pool_type, '')) not in ('RESOLUTION', 'WORK_QUEUE', 'TRIAGE', 'MANUAL', 'ESCALATION')

  -- Pool membership.
  union all
  select 'APOOL_MEMBER_POOL_MISSING', 'BLOCKER', m.tenant_id,
         'agent_pool_members', concat(m.pool_id, '/', m.agent_id),
         'agent_pools.pool_id', m.pool_id,
         'Pool member references an Agent Pool that does not exist in the same tenant.'
    from agent_pool_members m
    left join agent_pools p
      on p.tenant_id = m.tenant_id
     and p.pool_id = m.pool_id
   where p.pool_id is null

  union all
  select 'APOOL_MEMBER_AGENT_PROFILE_MISSING', 'BLOCKER', m.tenant_id,
         'agent_pool_members', concat(m.pool_id, '/', m.agent_id),
         'agent_profiles.agent_id', m.agent_id,
         'Pool member references an Agent profile that does not exist in the same tenant.'
    from agent_pool_members m
    left join agent_profiles ap
      on ap.tenant_id = m.tenant_id
     and ap.agent_id = m.agent_id
   where ap.agent_id is null

  union all
  select 'APOOL_MEMBER_AGENT_RUNTIME_MISSING', 'WARNING', m.tenant_id,
         'agent_pool_members', concat(m.pool_id, '/', m.agent_id),
         'agents.agent_id', m.agent_id,
         'Pool member has no runtime agent row. This may be valid before first connection, but it blocks live dispatch.'
    from agent_pool_members m
    left join agents a
      on a.agent_id = m.agent_id
   where a.agent_id is null

  union all
  select 'APOOL_MEMBER_STATUS_INVALID', 'WARNING', m.tenant_id,
         'agent_pool_members', concat(m.pool_id, '/', m.agent_id),
         'agent_pool_members.member_status', m.member_status,
         'Pool member status is outside the current allowed status set.'
    from agent_pool_members m
   where upper(coalesce(m.member_status, '')) not in ('ACTIVE', 'ENABLED', 'DISABLED', 'INACTIVE', 'RETIRED', 'SUSPENDED')

  union all
  select 'APOOL_MEMBER_WEIGHT_INVALID', 'BLOCKER', m.tenant_id,
         'agent_pool_members', concat(m.pool_id, '/', m.agent_id),
         'agent_pool_members.weight', m.weight::text,
         'Pool member weight must be greater than zero before WEIGHTED_SCORE can be constrained.'
    from agent_pool_members m
   where m.weight is null or m.weight <= 0

  union all
  select 'APOOL_MEMBER_PRIORITY_INVALID', 'BLOCKER', m.tenant_id,
         'agent_pool_members', concat(m.pool_id, '/', m.agent_id),
         'agent_pool_members.priority', m.priority::text,
         'Pool member priority must be greater than or equal to zero before priority constraints can be added.'
    from agent_pool_members m
   where m.priority is null or m.priority < 0

  -- Rule references.
  union all
  select 'DPOLICY_FLOW_MISSING', 'BLOCKER', r.tenant_id,
         'dispatch_policies', r.policy_id,
         'dispatch_flows.flow_id', r.flow_id,
         'Dispatch rule references a Source Flow that does not exist in the same tenant.'
    from dispatch_policies r
    left join dispatch_flows f
      on f.tenant_id = r.tenant_id
     and f.flow_id = r.flow_id
   where f.flow_id is null

  union all
  select 'DPOLICY_SOURCE_MISSING', 'BLOCKER', r.tenant_id,
         'dispatch_policies', r.policy_id,
         'source_systems.source_system_id', r.source_system,
         'Dispatch rule source_system does not exist in the same tenant.'
    from dispatch_policies r
    left join source_systems s
      on s.tenant_id = r.tenant_id
     and s.source_system_id = r.source_system
   where s.source_system_id is null

  union all
  select 'DPOLICY_SOURCE_FLOW_MISMATCH', 'BLOCKER', r.tenant_id,
         'dispatch_policies', r.policy_id,
         'dispatch_flows.source_system', f.source_system,
         'Dispatch rule source_system differs from its owning Source Flow source_system.'
    from dispatch_policies r
    join dispatch_flows f
      on f.tenant_id = r.tenant_id
     and f.flow_id = r.flow_id
   where r.source_system <> f.source_system

  union all
  select 'DPOLICY_TARGET_POOL_MISSING', 'BLOCKER', r.tenant_id,
         'dispatch_policies', r.policy_id,
         'agent_pools.pool_id', r.target_pool_id,
         'Dispatch rule target_pool_id does not exist in the same tenant.'
    from dispatch_policies r
    left join agent_pools p
      on p.tenant_id = r.tenant_id
     and p.pool_id = r.target_pool_id
   where r.target_pool_id is not null
     and p.pool_id is null

  union all
  select 'DPOLICY_ROUTING_STRATEGY_UNSUPPORTED', 'WARNING', r.tenant_id,
         'dispatch_policies', r.policy_id,
         'dispatch_policies.routing_strategy', r.routing_strategy,
         'Dispatch rule routing_strategy is outside current supported Pool strategy names.'
    from dispatch_policies r
   where upper(coalesce(r.routing_strategy, '')) not in ('LOWEST_LOAD', 'WEIGHTED_SCORE', 'MANUAL_ONLY')

  union all
  select 'DPOLICY_PRIORITY_INVALID', 'BLOCKER', r.tenant_id,
         'dispatch_policies', r.policy_id,
         'dispatch_policies.priority', r.priority::text,
         'Dispatch rule priority must be greater than or equal to zero.'
    from dispatch_policies r
   where r.priority is null or r.priority < 0

  -- Preserved legacy/reference-only child rows.
  union all
  select 'LEGACY_CAP_FLOW_MISSING', 'WARNING', c.tenant_id,
         'flow_required_capabilities', c.id,
         'dispatch_flows.flow_id', c.flow_id,
         'Preserved legacy/reference-only capability row points to a missing Source Flow.'
    from flow_required_capabilities c
    left join dispatch_flows f
      on f.tenant_id = c.tenant_id
     and f.flow_id = c.flow_id
   where f.flow_id is null

  union all
  select 'LEGACY_CAP_RULE_MISSING', 'WARNING', c.tenant_id,
         'flow_required_capabilities', c.id,
         'dispatch_policies.policy_id', c.rule_id,
         'Preserved legacy/reference-only capability row points to a missing Dispatch rule.'
    from flow_required_capabilities c
    left join dispatch_policies r
      on r.tenant_id = c.tenant_id
     and r.policy_id = c.rule_id
   where c.rule_id is not null
     and r.policy_id is null

  union all
  select 'LEGACY_FLOW_AGENT_FLOW_MISSING', 'WARNING', fa.tenant_id,
         'flow_agent_assignments', fa.id,
         'dispatch_flows.flow_id', fa.flow_id,
         'Preserved legacy/reference-only direct Agent row points to a missing Source Flow.'
    from flow_agent_assignments fa
    left join dispatch_flows f
      on f.tenant_id = fa.tenant_id
     and f.flow_id = fa.flow_id
   where f.flow_id is null

  union all
  select 'LEGACY_FLOW_AGENT_PROFILE_MISSING', 'WARNING', fa.tenant_id,
         'flow_agent_assignments', fa.id,
         'agent_profiles.agent_id', fa.agent_id,
         'Preserved legacy/reference-only direct Agent row points to a missing Agent profile.'
    from flow_agent_assignments fa
    left join agent_profiles ap
      on ap.tenant_id = fa.tenant_id
     and ap.agent_id = fa.agent_id
   where ap.agent_id is null

  -- Task and assignment history references.
  union all
  select 'TASK_SOURCE_MISSING', 'WARNING', t.tenant_id,
         'tasks', t.task_id,
         'source_systems.source_system_id', t.source_system,
         'Task source_system is missing from the tenant source system registry.'
    from tasks t
    left join source_systems s
      on s.tenant_id = t.tenant_id
     and s.source_system_id = t.source_system
   where t.tenant_id is not null
     and t.source_system is not null
     and s.source_system_id is null

  union all
  select 'TASK_MATCHED_FLOW_MISSING', 'WARNING', t.tenant_id,
         'tasks', t.task_id,
         'dispatch_flows.flow_id', t.matched_flow_id,
         'Task matched_flow_id no longer exists in the same tenant.'
    from tasks t
    left join dispatch_flows f
      on f.tenant_id = t.tenant_id
     and f.flow_id = t.matched_flow_id
   where t.tenant_id is not null
     and t.matched_flow_id is not null
     and f.flow_id is null

  union all
  select 'TASK_MATCHED_RULE_MISSING', 'WARNING', t.tenant_id,
         'tasks', t.task_id,
         'dispatch_policies.policy_id', t.matched_rule_id,
         'Task matched_rule_id no longer exists in the same tenant.'
    from tasks t
    left join dispatch_policies r
      on r.tenant_id = t.tenant_id
     and r.policy_id = t.matched_rule_id
   where t.tenant_id is not null
     and t.matched_rule_id is not null
     and r.policy_id is null

  union all
  select 'TASK_ASSIGNED_POOL_MISSING', 'WARNING', t.tenant_id,
         'tasks', t.task_id,
         'agent_pools.pool_id', t.assigned_pool_id,
         'Task assigned_pool_id no longer exists in the same tenant.'
    from tasks t
    left join agent_pools p
      on p.tenant_id = t.tenant_id
     and p.pool_id = t.assigned_pool_id
   where t.tenant_id is not null
     and t.assigned_pool_id is not null
     and p.pool_id is null

  union all
  select 'TASK_TARGET_POOL_MISSING', 'WARNING', t.tenant_id,
         'tasks', t.task_id,
         'agent_pools.pool_id', t.target_pool_id,
         'Task target_pool_id no longer exists in the same tenant.'
    from tasks t
    left join agent_pools p
      on p.tenant_id = t.tenant_id
     and p.pool_id = t.target_pool_id
   where t.tenant_id is not null
     and t.target_pool_id is not null
     and p.pool_id is null

  union all
  select 'ASSIGNMENT_TASK_MISSING', 'BLOCKER', ta_tenant.tenant_id,
         'task_assignments', ta.assignment_id,
         'tasks.task_id', ta.task_id,
         'Task assignment references a task_id that does not exist.'
    from task_assignments ta
    left join tasks t
      on t.task_id = ta.task_id
    left join lateral (select coalesce(t.tenant_id, '<unknown>') as tenant_id) ta_tenant on true
   where t.task_id is null

  union all
  select 'ASSIGNMENT_AGENT_RUNTIME_MISSING', 'WARNING', coalesce(t.tenant_id, '<unknown>'),
         'task_assignments', ta.assignment_id,
         'agents.agent_id', ta.agent_id,
         'Task assignment references an Agent runtime row that does not exist.'
    from task_assignments ta
    left join tasks t
      on t.task_id = ta.task_id
    left join agents a
      on a.agent_id = ta.agent_id
   where a.agent_id is null

  union all
  select 'ASSIGNMENT_POOL_MISSING', 'WARNING', t.tenant_id,
         'task_assignments', ta.assignment_id,
         'agent_pools.pool_id', coalesce(ta.assigned_pool_id, ta.target_pool_id),
         'Task assignment references an assigned/target pool that no longer exists in the task tenant.'
    from task_assignments ta
    join tasks t
      on t.task_id = ta.task_id
    left join agent_pools p
      on p.tenant_id = t.tenant_id
     and p.pool_id = coalesce(ta.assigned_pool_id, ta.target_pool_id)
   where coalesce(ta.assigned_pool_id, ta.target_pool_id) is not null
     and p.pool_id is null

  union all
  select 'DREQUEST_ASSIGNMENT_MISSING', 'BLOCKER', coalesce(t.tenant_id, '<unknown>'),
         'dispatch_requests', dr.dispatch_request_id,
         'task_assignments.assignment_id', dr.assignment_id,
         'Dispatch request references an assignment_id that does not exist.'
    from dispatch_requests dr
    left join task_assignments ta
      on ta.assignment_id = dr.assignment_id
    left join tasks t
      on t.task_id = coalesce(dr.task_id, ta.task_id)
   where dr.assignment_id is not null
     and ta.assignment_id is null

  union all
  select 'DREQUEST_TASK_MISSING', 'BLOCKER', '<unknown>',
         'dispatch_requests', dr.dispatch_request_id,
         'tasks.task_id', dr.task_id,
         'Dispatch request references a task_id that does not exist.'
    from dispatch_requests dr
    left join tasks t
      on t.task_id = dr.task_id
   where dr.task_id is not null
     and t.task_id is null

  union all
  select 'ROUTING_DECISION_TASK_MISSING', 'WARNING', coalesce(rd.tenant_id, '<unknown>'),
         'routing_decisions', rd.decision_id,
         'tasks.task_id', rd.task_id,
         'Routing decision references a task_id that does not exist.'
    from routing_decisions rd
    left join tasks t
      on t.task_id = rd.task_id
   where rd.task_id is not null
     and t.task_id is null

  union all
  select 'CALLBACK_TASK_MISSING', 'WARNING', coalesce(t.tenant_id, '<unknown>'),
         'task_callbacks', cb.callback_id,
         'tasks.task_id', cb.task_id,
         'Task callback references a task_id that does not exist.'
    from task_callbacks cb
    left join tasks t
      on t.task_id = cb.task_id
   where cb.task_id is not null
     and t.task_id is null

  union all
  select 'CALLBACK_ASSIGNMENT_MISSING', 'WARNING', coalesce(t.tenant_id, '<unknown>'),
         'task_callbacks', cb.callback_id,
         'task_assignments.assignment_id', cb.assignment_id,
         'Task callback references an assignment_id that does not exist.'
    from task_callbacks cb
    left join task_assignments ta
      on ta.assignment_id = cb.assignment_id
    left join tasks t
      on t.task_id = coalesce(cb.task_id, ta.task_id)
   where cb.assignment_id is not null
     and ta.assignment_id is null
)
select p.fk_readiness,
       p.check_readiness,
       f.check_id,
       f.severity,
       p.default_action,
       count(*) as finding_count
  from integrity_findings f
  join repair_plan p
    on p.check_id = f.check_id
 where p.fk_readiness like 'blocks_%'
    or p.check_readiness like 'blocks_%'
 group by p.fk_readiness,
          p.check_readiness,
          f.check_id,
          f.severity,
          p.default_action
 order by case f.severity when 'BLOCKER' then 1 when 'WARNING' then 2 else 3 end,
          f.check_id;

\echo '== Phase 2-2 Proposed Repair Actions Detail =='

with repair_plan(check_id, severity, repair_class, default_action, automation_policy, fk_readiness, check_readiness, proposed_fix, manual_review_required, notes) as (
  values
    ('DFLOW_SOURCE_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'CREATE_OR_REMAP_SOURCE_SYSTEM', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Create the missing source_systems row in the same tenant or remap the Source Flow to an existing source_system.', true, 'Do not auto-create source systems because display name, owner and lifecycle status are business decisions.'),
    ('DFLOW_DEFAULT_POOL_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'CREATE_OR_REMAP_DEFAULT_POOL', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Create the missing agent_pools row or remap dispatch_flows.default_pool_id to an existing pool in the same tenant.', true, 'Do not auto-create pools because membership and operating model must be confirmed.'),
    ('DFLOW_ACTIVE_WITHOUT_DEFAULT_POOL', 'BLOCKER', 'MANUAL_REQUIRED', 'ASSIGN_DEFAULT_POOL_OR_DISABLE_FLOW', 'manual', 'blocks_phase2_3_fk', 'blocks_phase2_4_check', 'Assign a valid default_pool_id before enabling the Flow, or change the Flow status to DRAFT/DISABLED.', true, 'Current Source Flow model requires an active Flow to have a default Agent Pool.'),
    ('DFLOW_DEFAULT_POOL_SOURCE_MISMATCH', 'WARNING', 'REVIEW_REQUIRED', 'CONFIRM_SHARED_POOL_OR_REMAP', 'manual', 'review_before_fk', 'not_applicable', 'Confirm cross-source shared-pool routing is intentional; otherwise remap the Flow to a pool with the same source_system.', true, 'This is not always invalid because shared pools may be allowed later by explicit policy.'),
    ('DFLOW_STATUS_INVALID', 'WARNING', 'NORMALIZABLE_ENUM', 'NORMALIZE_FLOW_STATUS', 'candidate_auto', 'not_applicable', 'blocks_phase2_4_check', 'Normalize status to DRAFT, ACTIVE, DISABLED, INACTIVE or RETIRED based on business state.', true, 'Can be scripted only after mapping legacy statuses.'),

    ('APOOL_SOURCE_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'CREATE_OR_REMAP_POOL_SOURCE', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Create the missing source_systems row, remap agent_pools.source_system, or set source_system to * only if explicitly shared.', true, 'Pool source scope affects all routing into the pool.'),
    ('APOOL_SELECTION_STRATEGY_UNSUPPORTED', 'BLOCKER', 'SAFE_NORMALIZATION', 'NORMALIZE_POOL_SELECTION_STRATEGY', 'candidate_auto', 'not_applicable', 'blocks_phase2_4_check', 'Normalize unsupported selection_strategy to LOWEST_LOAD unless an approved migration rule maps it to WEIGHTED_SCORE or MANUAL_ONLY.', false, 'LOWEST_LOAD is the safest supported default for unsupported historical strategies.'),
    ('APOOL_STATUS_INVALID', 'WARNING', 'NORMALIZABLE_ENUM', 'NORMALIZE_POOL_STATUS', 'candidate_auto', 'not_applicable', 'blocks_phase2_4_check', 'Normalize status to ACTIVE, DISABLED, INACTIVE, RETIRED or DRAFT.', true, 'Requires legacy status mapping.'),
    ('APOOL_TYPE_INVALID', 'WARNING', 'NORMALIZABLE_ENUM', 'NORMALIZE_POOL_TYPE', 'candidate_auto', 'not_applicable', 'blocks_phase2_4_check', 'Normalize pool_type to RESOLUTION, WORK_QUEUE, TRIAGE, MANUAL or ESCALATION.', true, 'Requires legacy pool type mapping.'),

    ('APOOL_MEMBER_POOL_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'CREATE_POOL_OR_REMOVE_MEMBER', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Create the missing pool or remove/archive the orphan agent_pool_members row after confirming it is not needed.', true, 'Deleting membership automatically can silently remove routing capacity.'),
    ('APOOL_MEMBER_AGENT_PROFILE_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'CREATE_PROFILE_OR_REMOVE_MEMBER', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Create the missing agent_profiles row in the same tenant or remove/archive the Pool member.', true, 'Business Agent profile is the Core authority for Pool membership.'),
    ('APOOL_MEMBER_AGENT_RUNTIME_MISSING', 'WARNING', 'RUNTIME_OBSERVATION', 'WAIT_FOR_RUNTIME_OR_REVIEW_AGENT_ID', 'manual', 'not_applicable', 'not_applicable', 'Confirm whether the Agent has not connected yet or the member references the wrong agent_id.', true, 'Runtime Agent rows may legitimately be absent before first connection.'),
    ('APOOL_MEMBER_STATUS_INVALID', 'WARNING', 'NORMALIZABLE_ENUM', 'NORMALIZE_MEMBER_STATUS', 'candidate_auto', 'not_applicable', 'blocks_phase2_4_check', 'Normalize member_status to ACTIVE, DISABLED, INACTIVE, RETIRED or SUSPENDED.', true, 'Requires legacy status mapping.'),
    ('APOOL_MEMBER_WEIGHT_INVALID', 'BLOCKER', 'SAFE_NORMALIZATION', 'SET_MEMBER_WEIGHT_DEFAULT', 'candidate_auto', 'not_applicable', 'blocks_phase2_4_check', 'Set null or non-positive weight to 1.', false, 'Weight=1 is already the current default and preserves eligibility without preferential scoring.'),
    ('APOOL_MEMBER_PRIORITY_INVALID', 'BLOCKER', 'SAFE_NORMALIZATION', 'SET_MEMBER_PRIORITY_DEFAULT', 'candidate_auto', 'not_applicable', 'blocks_phase2_4_check', 'Set null or negative priority to 100.', false, 'Priority=100 is already the current default.'),

    ('DPOLICY_FLOW_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'CREATE_FLOW_OR_REMOVE_RULE', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Create the missing dispatch_flows row or remove/archive the orphan dispatch_policies row.', true, 'A rule cannot be evaluated without an owning Source Flow.'),
    ('DPOLICY_SOURCE_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'CREATE_OR_REMAP_RULE_SOURCE', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Create the missing source_systems row or remap dispatch_policies.source_system to an existing source_system.', true, 'Rule source_system must remain aligned with the owning Flow.'),
    ('DPOLICY_SOURCE_FLOW_MISMATCH', 'BLOCKER', 'SAFE_NORMALIZATION', 'ALIGN_RULE_SOURCE_TO_FLOW', 'candidate_auto', 'blocks_phase2_3_fk', 'not_applicable', 'Set dispatch_policies.source_system to the owning dispatch_flows.source_system after confirming the rule belongs to that Flow.', true, 'This can be automated only when rule ownership is already trusted.'),
    ('DPOLICY_TARGET_POOL_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'CREATE_OR_REMAP_RULE_TARGET_POOL', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Create the missing target Agent Pool, remap target_pool_id, or clear target_pool_id to fall back to Flow default only if product behavior is accepted.', true, 'Clearing a target pool changes rule-specific routing.'),
    ('DPOLICY_ROUTING_STRATEGY_UNSUPPORTED', 'WARNING', 'SAFE_NORMALIZATION', 'NORMALIZE_RULE_ROUTING_STRATEGY', 'candidate_auto', 'not_applicable', 'blocks_phase2_4_check', 'Normalize unsupported routing_strategy to LOWEST_LOAD unless an approved migration rule maps it to WEIGHTED_SCORE or MANUAL_ONLY.', false, 'Rule-level strategy is secondary to Pool strategy but should be constrained.'),
    ('DPOLICY_PRIORITY_INVALID', 'BLOCKER', 'SAFE_NORMALIZATION', 'SET_RULE_PRIORITY_DEFAULT', 'candidate_auto', 'not_applicable', 'blocks_phase2_4_check', 'Set null or negative priority to 100.', false, 'Priority=100 is already the current default.'),

    ('LEGACY_CAP_FLOW_MISSING', 'WARNING', 'LEGACY_REFERENCE_CLEANUP', 'ARCHIVE_OR_REMOVE_LEGACY_CAPABILITY', 'manual', 'not_applicable', 'not_applicable', 'Archive or remove preserved reference-only capability rows after confirming the legacy Flow no longer exists.', true, 'Reference-only rows do not block current routing but should not remain misleading.'),
    ('LEGACY_CAP_RULE_MISSING', 'WARNING', 'LEGACY_REFERENCE_CLEANUP', 'ARCHIVE_OR_REMOVE_LEGACY_RULE_CAPABILITY', 'manual', 'not_applicable', 'not_applicable', 'Archive or remove preserved reference-only rule capability rows after confirming the rule no longer exists.', true, 'Reference-only rows are intentionally not FK-blocking in Phase 2-3.'),
    ('LEGACY_FLOW_AGENT_FLOW_MISSING', 'WARNING', 'LEGACY_REFERENCE_CLEANUP', 'ARCHIVE_OR_REMOVE_LEGACY_FLOW_AGENT', 'manual', 'not_applicable', 'not_applicable', 'Archive or remove preserved reference-only direct Agent rows after confirming the Flow no longer exists.', true, 'Direct Agent rows are not part of current routing.'),
    ('LEGACY_FLOW_AGENT_PROFILE_MISSING', 'WARNING', 'LEGACY_REFERENCE_CLEANUP', 'ARCHIVE_OR_REMOVE_LEGACY_AGENT_REFERENCE', 'manual', 'not_applicable', 'not_applicable', 'Archive or remove preserved direct Agent references whose Agent profile no longer exists.', true, 'Direct Agent rows remain reference-only.'),

    ('TASK_SOURCE_MISSING', 'WARNING', 'HISTORY_REPAIR_OR_ACCEPT', 'RECREATE_SOURCE_FOR_HISTORY_OR_ACCEPT', 'manual', 'not_applicable', 'not_applicable', 'Recreate source_systems for historical task readability or explicitly accept the missing historical reference.', true, 'Historical tasks may outlive mutable configuration.'),
    ('TASK_MATCHED_FLOW_MISSING', 'WARNING', 'HISTORY_REPAIR_OR_ACCEPT', 'RECREATE_FLOW_FOR_HISTORY_OR_ACCEPT', 'manual', 'not_applicable', 'not_applicable', 'Recreate archived Flow metadata for history or accept that the historical matched_flow_id is no longer enforced.', true, 'Do not FK historical matched_flow_id until archival policy exists.'),
    ('TASK_MATCHED_RULE_MISSING', 'WARNING', 'HISTORY_REPAIR_OR_ACCEPT', 'RECREATE_RULE_FOR_HISTORY_OR_ACCEPT', 'manual', 'not_applicable', 'not_applicable', 'Recreate archived Rule metadata for history or accept that the historical matched_rule_id is no longer enforced.', true, 'Do not FK historical matched_rule_id until archival policy exists.'),
    ('TASK_ASSIGNED_POOL_MISSING', 'WARNING', 'HISTORY_REPAIR_OR_ACCEPT', 'RECREATE_POOL_FOR_HISTORY_OR_ACCEPT', 'manual', 'not_applicable', 'not_applicable', 'Recreate archived Pool metadata for history or accept the historical assigned_pool_id as denormalized evidence.', true, 'History references should be handled by snapshot/evidence policy.'),
    ('TASK_TARGET_POOL_MISSING', 'WARNING', 'HISTORY_REPAIR_OR_ACCEPT', 'RECREATE_TARGET_POOL_FOR_HISTORY_OR_ACCEPT', 'manual', 'not_applicable', 'not_applicable', 'Recreate archived target Pool metadata for history or accept the historical target_pool_id as denormalized evidence.', true, 'History references should not block current FK work.'),

    ('ASSIGNMENT_TASK_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'RECREATE_TASK_OR_ARCHIVE_ASSIGNMENT', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Recreate the missing Task from evidence or archive/remove the orphan task_assignments row after support review.', true, 'Assignment cannot exist without a Task in the current lifecycle.'),
    ('ASSIGNMENT_AGENT_RUNTIME_MISSING', 'WARNING', 'RUNTIME_OBSERVATION', 'WAIT_FOR_RUNTIME_OR_REVIEW_AGENT_ID', 'manual', 'not_applicable', 'not_applicable', 'Confirm whether the Agent runtime row has not connected yet or the assignment references an invalid agent_id.', true, 'Historical assignment may reference a disconnected Agent.'),
    ('ASSIGNMENT_POOL_MISSING', 'WARNING', 'HISTORY_REPAIR_OR_ACCEPT', 'RECREATE_POOL_FOR_ASSIGNMENT_HISTORY_OR_ACCEPT', 'manual', 'not_applicable', 'not_applicable', 'Recreate archived Pool metadata or accept the historical assignment pool reference as evidence only.', true, 'Do not FK history pool columns until archival model is settled.'),
    ('DREQUEST_ASSIGNMENT_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'RECREATE_ASSIGNMENT_OR_ARCHIVE_REQUEST', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Recreate the missing assignment or archive/remove the orphan dispatch_requests row after support review.', true, 'Dispatch request cannot safely execute without assignment context.'),
    ('DREQUEST_TASK_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'RECREATE_TASK_OR_ARCHIVE_REQUEST', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Recreate the missing Task or archive/remove the orphan dispatch_requests row after support review.', true, 'Dispatch request cannot safely execute without task context.'),
    ('ROUTING_DECISION_TASK_MISSING', 'WARNING', 'HISTORY_REPAIR_OR_ACCEPT', 'RECREATE_TASK_FOR_DECISION_OR_ACCEPT', 'manual', 'not_applicable', 'not_applicable', 'Recreate Task evidence for historical routing decision or accept it as detached diagnostic history.', true, 'Routing decision history should later use snapshot policy.'),
    ('CALLBACK_TASK_MISSING', 'WARNING', 'HISTORY_REPAIR_OR_ACCEPT', 'RECREATE_TASK_FOR_CALLBACK_OR_ACCEPT', 'manual', 'not_applicable', 'not_applicable', 'Recreate Task evidence for callback history or accept it as detached callback evidence.', true, 'Callback history may remain detached until archival policy exists.'),
    ('CALLBACK_ASSIGNMENT_MISSING', 'WARNING', 'HISTORY_REPAIR_OR_ACCEPT', 'RECREATE_ASSIGNMENT_FOR_CALLBACK_OR_ACCEPT', 'manual', 'not_applicable', 'not_applicable', 'Recreate assignment evidence for callback history or accept it as detached callback evidence.', true, 'Callback assignment FK should wait for history policy.')
),
integrity_findings as (
  -- Source System and Flow references.
  select 'DFLOW_SOURCE_MISSING' as check_id,
         'BLOCKER' as severity,
         f.tenant_id,
         'dispatch_flows' as entity,
         f.flow_id as entity_id,
         'source_systems.source_system_id' as reference_type,
         f.source_system as reference_id,
         'Source Flow references a source_system that does not exist in the same tenant.' as message
    from dispatch_flows f
    left join source_systems s
      on s.tenant_id = f.tenant_id
     and s.source_system_id = f.source_system
   where s.source_system_id is null

  union all
  select 'DFLOW_DEFAULT_POOL_MISSING', 'BLOCKER', f.tenant_id,
         'dispatch_flows', f.flow_id,
         'agent_pools.pool_id', f.default_pool_id,
         'Source Flow default_pool_id does not exist in the same tenant.'
    from dispatch_flows f
    left join agent_pools p
      on p.tenant_id = f.tenant_id
     and p.pool_id = f.default_pool_id
   where f.default_pool_id is not null
     and p.pool_id is null

  union all
  select 'DFLOW_ACTIVE_WITHOUT_DEFAULT_POOL', 'BLOCKER', f.tenant_id,
         'dispatch_flows', f.flow_id,
         'agent_pools.pool_id', f.default_pool_id,
         'ACTIVE Source Flow has no default_pool_id; current model requires a default Agent Pool.'
    from dispatch_flows f
   where upper(coalesce(f.status, 'DRAFT')) in ('ACTIVE', 'ENABLED')
     and nullif(trim(f.default_pool_id), '') is null

  union all
  select 'DFLOW_DEFAULT_POOL_SOURCE_MISMATCH', 'WARNING', f.tenant_id,
         'dispatch_flows', f.flow_id,
         'agent_pools.source_system', p.source_system,
         'Default Agent Pool source_system differs from the Source Flow source_system. Review whether this is intentional shared-pool routing.'
    from dispatch_flows f
    join agent_pools p
      on p.tenant_id = f.tenant_id
     and p.pool_id = f.default_pool_id
   where nullif(trim(coalesce(p.source_system, '')), '') is not null
     and p.source_system <> f.source_system

  union all
  select 'DFLOW_STATUS_INVALID', 'WARNING', f.tenant_id,
         'dispatch_flows', f.flow_id,
         'dispatch_flows.status', f.status,
         'Source Flow status is outside the current allowed status set.'
    from dispatch_flows f
   where upper(coalesce(f.status, '')) not in ('DRAFT', 'ACTIVE', 'ENABLED', 'DISABLED', 'INACTIVE', 'RETIRED')

  -- Agent Pool master data.
  union all
  select 'APOOL_SOURCE_MISSING', 'BLOCKER', p.tenant_id,
         'agent_pools', p.pool_id,
         'source_systems.source_system_id', p.source_system,
         'Agent Pool source_system does not exist in the same tenant.'
    from agent_pools p
    left join source_systems s
      on s.tenant_id = p.tenant_id
     and s.source_system_id = p.source_system
   where nullif(trim(coalesce(p.source_system, '')), '') is not null
     and p.source_system <> '*'
     and s.source_system_id is null

  union all
  select 'APOOL_SELECTION_STRATEGY_UNSUPPORTED', 'BLOCKER', p.tenant_id,
         'agent_pools', p.pool_id,
         'agent_pools.selection_strategy', p.selection_strategy,
         'Agent Pool selection_strategy is not supported by the current Runtime contract.'
    from agent_pools p
   where upper(coalesce(p.selection_strategy, '')) not in ('LOWEST_LOAD', 'WEIGHTED_SCORE', 'MANUAL_ONLY')

  union all
  select 'APOOL_STATUS_INVALID', 'WARNING', p.tenant_id,
         'agent_pools', p.pool_id,
         'agent_pools.status', p.status,
         'Agent Pool status is outside the current allowed status set.'
    from agent_pools p
   where upper(coalesce(p.status, '')) not in ('ACTIVE', 'ENABLED', 'DISABLED', 'INACTIVE', 'RETIRED', 'DRAFT')

  union all
  select 'APOOL_TYPE_INVALID', 'WARNING', p.tenant_id,
         'agent_pools', p.pool_id,
         'agent_pools.pool_type', p.pool_type,
         'Agent Pool pool_type is outside the current known type set.'
    from agent_pools p
   where upper(coalesce(p.pool_type, '')) not in ('RESOLUTION', 'WORK_QUEUE', 'TRIAGE', 'MANUAL', 'ESCALATION')

  -- Pool membership.
  union all
  select 'APOOL_MEMBER_POOL_MISSING', 'BLOCKER', m.tenant_id,
         'agent_pool_members', concat(m.pool_id, '/', m.agent_id),
         'agent_pools.pool_id', m.pool_id,
         'Pool member references an Agent Pool that does not exist in the same tenant.'
    from agent_pool_members m
    left join agent_pools p
      on p.tenant_id = m.tenant_id
     and p.pool_id = m.pool_id
   where p.pool_id is null

  union all
  select 'APOOL_MEMBER_AGENT_PROFILE_MISSING', 'BLOCKER', m.tenant_id,
         'agent_pool_members', concat(m.pool_id, '/', m.agent_id),
         'agent_profiles.agent_id', m.agent_id,
         'Pool member references an Agent profile that does not exist in the same tenant.'
    from agent_pool_members m
    left join agent_profiles ap
      on ap.tenant_id = m.tenant_id
     and ap.agent_id = m.agent_id
   where ap.agent_id is null

  union all
  select 'APOOL_MEMBER_AGENT_RUNTIME_MISSING', 'WARNING', m.tenant_id,
         'agent_pool_members', concat(m.pool_id, '/', m.agent_id),
         'agents.agent_id', m.agent_id,
         'Pool member has no runtime agent row. This may be valid before first connection, but it blocks live dispatch.'
    from agent_pool_members m
    left join agents a
      on a.agent_id = m.agent_id
   where a.agent_id is null

  union all
  select 'APOOL_MEMBER_STATUS_INVALID', 'WARNING', m.tenant_id,
         'agent_pool_members', concat(m.pool_id, '/', m.agent_id),
         'agent_pool_members.member_status', m.member_status,
         'Pool member status is outside the current allowed status set.'
    from agent_pool_members m
   where upper(coalesce(m.member_status, '')) not in ('ACTIVE', 'ENABLED', 'DISABLED', 'INACTIVE', 'RETIRED', 'SUSPENDED')

  union all
  select 'APOOL_MEMBER_WEIGHT_INVALID', 'BLOCKER', m.tenant_id,
         'agent_pool_members', concat(m.pool_id, '/', m.agent_id),
         'agent_pool_members.weight', m.weight::text,
         'Pool member weight must be greater than zero before WEIGHTED_SCORE can be constrained.'
    from agent_pool_members m
   where m.weight is null or m.weight <= 0

  union all
  select 'APOOL_MEMBER_PRIORITY_INVALID', 'BLOCKER', m.tenant_id,
         'agent_pool_members', concat(m.pool_id, '/', m.agent_id),
         'agent_pool_members.priority', m.priority::text,
         'Pool member priority must be greater than or equal to zero before priority constraints can be added.'
    from agent_pool_members m
   where m.priority is null or m.priority < 0

  -- Rule references.
  union all
  select 'DPOLICY_FLOW_MISSING', 'BLOCKER', r.tenant_id,
         'dispatch_policies', r.policy_id,
         'dispatch_flows.flow_id', r.flow_id,
         'Dispatch rule references a Source Flow that does not exist in the same tenant.'
    from dispatch_policies r
    left join dispatch_flows f
      on f.tenant_id = r.tenant_id
     and f.flow_id = r.flow_id
   where f.flow_id is null

  union all
  select 'DPOLICY_SOURCE_MISSING', 'BLOCKER', r.tenant_id,
         'dispatch_policies', r.policy_id,
         'source_systems.source_system_id', r.source_system,
         'Dispatch rule source_system does not exist in the same tenant.'
    from dispatch_policies r
    left join source_systems s
      on s.tenant_id = r.tenant_id
     and s.source_system_id = r.source_system
   where s.source_system_id is null

  union all
  select 'DPOLICY_SOURCE_FLOW_MISMATCH', 'BLOCKER', r.tenant_id,
         'dispatch_policies', r.policy_id,
         'dispatch_flows.source_system', f.source_system,
         'Dispatch rule source_system differs from its owning Source Flow source_system.'
    from dispatch_policies r
    join dispatch_flows f
      on f.tenant_id = r.tenant_id
     and f.flow_id = r.flow_id
   where r.source_system <> f.source_system

  union all
  select 'DPOLICY_TARGET_POOL_MISSING', 'BLOCKER', r.tenant_id,
         'dispatch_policies', r.policy_id,
         'agent_pools.pool_id', r.target_pool_id,
         'Dispatch rule target_pool_id does not exist in the same tenant.'
    from dispatch_policies r
    left join agent_pools p
      on p.tenant_id = r.tenant_id
     and p.pool_id = r.target_pool_id
   where r.target_pool_id is not null
     and p.pool_id is null

  union all
  select 'DPOLICY_ROUTING_STRATEGY_UNSUPPORTED', 'WARNING', r.tenant_id,
         'dispatch_policies', r.policy_id,
         'dispatch_policies.routing_strategy', r.routing_strategy,
         'Dispatch rule routing_strategy is outside current supported Pool strategy names.'
    from dispatch_policies r
   where upper(coalesce(r.routing_strategy, '')) not in ('LOWEST_LOAD', 'WEIGHTED_SCORE', 'MANUAL_ONLY')

  union all
  select 'DPOLICY_PRIORITY_INVALID', 'BLOCKER', r.tenant_id,
         'dispatch_policies', r.policy_id,
         'dispatch_policies.priority', r.priority::text,
         'Dispatch rule priority must be greater than or equal to zero.'
    from dispatch_policies r
   where r.priority is null or r.priority < 0

  -- Preserved legacy/reference-only child rows.
  union all
  select 'LEGACY_CAP_FLOW_MISSING', 'WARNING', c.tenant_id,
         'flow_required_capabilities', c.id,
         'dispatch_flows.flow_id', c.flow_id,
         'Preserved legacy/reference-only capability row points to a missing Source Flow.'
    from flow_required_capabilities c
    left join dispatch_flows f
      on f.tenant_id = c.tenant_id
     and f.flow_id = c.flow_id
   where f.flow_id is null

  union all
  select 'LEGACY_CAP_RULE_MISSING', 'WARNING', c.tenant_id,
         'flow_required_capabilities', c.id,
         'dispatch_policies.policy_id', c.rule_id,
         'Preserved legacy/reference-only capability row points to a missing Dispatch rule.'
    from flow_required_capabilities c
    left join dispatch_policies r
      on r.tenant_id = c.tenant_id
     and r.policy_id = c.rule_id
   where c.rule_id is not null
     and r.policy_id is null

  union all
  select 'LEGACY_FLOW_AGENT_FLOW_MISSING', 'WARNING', fa.tenant_id,
         'flow_agent_assignments', fa.id,
         'dispatch_flows.flow_id', fa.flow_id,
         'Preserved legacy/reference-only direct Agent row points to a missing Source Flow.'
    from flow_agent_assignments fa
    left join dispatch_flows f
      on f.tenant_id = fa.tenant_id
     and f.flow_id = fa.flow_id
   where f.flow_id is null

  union all
  select 'LEGACY_FLOW_AGENT_PROFILE_MISSING', 'WARNING', fa.tenant_id,
         'flow_agent_assignments', fa.id,
         'agent_profiles.agent_id', fa.agent_id,
         'Preserved legacy/reference-only direct Agent row points to a missing Agent profile.'
    from flow_agent_assignments fa
    left join agent_profiles ap
      on ap.tenant_id = fa.tenant_id
     and ap.agent_id = fa.agent_id
   where ap.agent_id is null

  -- Task and assignment history references.
  union all
  select 'TASK_SOURCE_MISSING', 'WARNING', t.tenant_id,
         'tasks', t.task_id,
         'source_systems.source_system_id', t.source_system,
         'Task source_system is missing from the tenant source system registry.'
    from tasks t
    left join source_systems s
      on s.tenant_id = t.tenant_id
     and s.source_system_id = t.source_system
   where t.tenant_id is not null
     and t.source_system is not null
     and s.source_system_id is null

  union all
  select 'TASK_MATCHED_FLOW_MISSING', 'WARNING', t.tenant_id,
         'tasks', t.task_id,
         'dispatch_flows.flow_id', t.matched_flow_id,
         'Task matched_flow_id no longer exists in the same tenant.'
    from tasks t
    left join dispatch_flows f
      on f.tenant_id = t.tenant_id
     and f.flow_id = t.matched_flow_id
   where t.tenant_id is not null
     and t.matched_flow_id is not null
     and f.flow_id is null

  union all
  select 'TASK_MATCHED_RULE_MISSING', 'WARNING', t.tenant_id,
         'tasks', t.task_id,
         'dispatch_policies.policy_id', t.matched_rule_id,
         'Task matched_rule_id no longer exists in the same tenant.'
    from tasks t
    left join dispatch_policies r
      on r.tenant_id = t.tenant_id
     and r.policy_id = t.matched_rule_id
   where t.tenant_id is not null
     and t.matched_rule_id is not null
     and r.policy_id is null

  union all
  select 'TASK_ASSIGNED_POOL_MISSING', 'WARNING', t.tenant_id,
         'tasks', t.task_id,
         'agent_pools.pool_id', t.assigned_pool_id,
         'Task assigned_pool_id no longer exists in the same tenant.'
    from tasks t
    left join agent_pools p
      on p.tenant_id = t.tenant_id
     and p.pool_id = t.assigned_pool_id
   where t.tenant_id is not null
     and t.assigned_pool_id is not null
     and p.pool_id is null

  union all
  select 'TASK_TARGET_POOL_MISSING', 'WARNING', t.tenant_id,
         'tasks', t.task_id,
         'agent_pools.pool_id', t.target_pool_id,
         'Task target_pool_id no longer exists in the same tenant.'
    from tasks t
    left join agent_pools p
      on p.tenant_id = t.tenant_id
     and p.pool_id = t.target_pool_id
   where t.tenant_id is not null
     and t.target_pool_id is not null
     and p.pool_id is null

  union all
  select 'ASSIGNMENT_TASK_MISSING', 'BLOCKER', ta_tenant.tenant_id,
         'task_assignments', ta.assignment_id,
         'tasks.task_id', ta.task_id,
         'Task assignment references a task_id that does not exist.'
    from task_assignments ta
    left join tasks t
      on t.task_id = ta.task_id
    left join lateral (select coalesce(t.tenant_id, '<unknown>') as tenant_id) ta_tenant on true
   where t.task_id is null

  union all
  select 'ASSIGNMENT_AGENT_RUNTIME_MISSING', 'WARNING', coalesce(t.tenant_id, '<unknown>'),
         'task_assignments', ta.assignment_id,
         'agents.agent_id', ta.agent_id,
         'Task assignment references an Agent runtime row that does not exist.'
    from task_assignments ta
    left join tasks t
      on t.task_id = ta.task_id
    left join agents a
      on a.agent_id = ta.agent_id
   where a.agent_id is null

  union all
  select 'ASSIGNMENT_POOL_MISSING', 'WARNING', t.tenant_id,
         'task_assignments', ta.assignment_id,
         'agent_pools.pool_id', coalesce(ta.assigned_pool_id, ta.target_pool_id),
         'Task assignment references an assigned/target pool that no longer exists in the task tenant.'
    from task_assignments ta
    join tasks t
      on t.task_id = ta.task_id
    left join agent_pools p
      on p.tenant_id = t.tenant_id
     and p.pool_id = coalesce(ta.assigned_pool_id, ta.target_pool_id)
   where coalesce(ta.assigned_pool_id, ta.target_pool_id) is not null
     and p.pool_id is null

  union all
  select 'DREQUEST_ASSIGNMENT_MISSING', 'BLOCKER', coalesce(t.tenant_id, '<unknown>'),
         'dispatch_requests', dr.dispatch_request_id,
         'task_assignments.assignment_id', dr.assignment_id,
         'Dispatch request references an assignment_id that does not exist.'
    from dispatch_requests dr
    left join task_assignments ta
      on ta.assignment_id = dr.assignment_id
    left join tasks t
      on t.task_id = coalesce(dr.task_id, ta.task_id)
   where dr.assignment_id is not null
     and ta.assignment_id is null

  union all
  select 'DREQUEST_TASK_MISSING', 'BLOCKER', '<unknown>',
         'dispatch_requests', dr.dispatch_request_id,
         'tasks.task_id', dr.task_id,
         'Dispatch request references a task_id that does not exist.'
    from dispatch_requests dr
    left join tasks t
      on t.task_id = dr.task_id
   where dr.task_id is not null
     and t.task_id is null

  union all
  select 'ROUTING_DECISION_TASK_MISSING', 'WARNING', coalesce(rd.tenant_id, '<unknown>'),
         'routing_decisions', rd.decision_id,
         'tasks.task_id', rd.task_id,
         'Routing decision references a task_id that does not exist.'
    from routing_decisions rd
    left join tasks t
      on t.task_id = rd.task_id
   where rd.task_id is not null
     and t.task_id is null

  union all
  select 'CALLBACK_TASK_MISSING', 'WARNING', coalesce(t.tenant_id, '<unknown>'),
         'task_callbacks', cb.callback_id,
         'tasks.task_id', cb.task_id,
         'Task callback references a task_id that does not exist.'
    from task_callbacks cb
    left join tasks t
      on t.task_id = cb.task_id
   where cb.task_id is not null
     and t.task_id is null

  union all
  select 'CALLBACK_ASSIGNMENT_MISSING', 'WARNING', coalesce(t.tenant_id, '<unknown>'),
         'task_callbacks', cb.callback_id,
         'task_assignments.assignment_id', cb.assignment_id,
         'Task callback references an assignment_id that does not exist.'
    from task_callbacks cb
    left join task_assignments ta
      on ta.assignment_id = cb.assignment_id
    left join tasks t
      on t.task_id = coalesce(cb.task_id, ta.task_id)
   where cb.assignment_id is not null
     and ta.assignment_id is null
)
select f.severity,
       f.check_id,
       p.repair_class,
       p.default_action,
       p.automation_policy,
       p.manual_review_required,
       p.fk_readiness,
       p.check_readiness,
       f.tenant_id,
       f.entity,
       f.entity_id,
       f.reference_type,
       f.reference_id,
       f.message,
       p.proposed_fix,
       p.notes
  from integrity_findings f
  join repair_plan p
    on p.check_id = f.check_id
 order by case f.severity when 'BLOCKER' then 1 when 'WARNING' then 2 else 3 end,
          f.check_id,
          f.tenant_id,
          f.entity,
          f.entity_id;

\echo '== Phase 2-2 Candidate Auto Repair Preview =='

with repair_plan(check_id, severity, repair_class, default_action, automation_policy, fk_readiness, check_readiness, proposed_fix, manual_review_required, notes) as (
  values
    ('DFLOW_SOURCE_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'CREATE_OR_REMAP_SOURCE_SYSTEM', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Create the missing source_systems row in the same tenant or remap the Source Flow to an existing source_system.', true, 'Do not auto-create source systems because display name, owner and lifecycle status are business decisions.'),
    ('DFLOW_DEFAULT_POOL_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'CREATE_OR_REMAP_DEFAULT_POOL', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Create the missing agent_pools row or remap dispatch_flows.default_pool_id to an existing pool in the same tenant.', true, 'Do not auto-create pools because membership and operating model must be confirmed.'),
    ('DFLOW_ACTIVE_WITHOUT_DEFAULT_POOL', 'BLOCKER', 'MANUAL_REQUIRED', 'ASSIGN_DEFAULT_POOL_OR_DISABLE_FLOW', 'manual', 'blocks_phase2_3_fk', 'blocks_phase2_4_check', 'Assign a valid default_pool_id before enabling the Flow, or change the Flow status to DRAFT/DISABLED.', true, 'Current Source Flow model requires an active Flow to have a default Agent Pool.'),
    ('DFLOW_DEFAULT_POOL_SOURCE_MISMATCH', 'WARNING', 'REVIEW_REQUIRED', 'CONFIRM_SHARED_POOL_OR_REMAP', 'manual', 'review_before_fk', 'not_applicable', 'Confirm cross-source shared-pool routing is intentional; otherwise remap the Flow to a pool with the same source_system.', true, 'This is not always invalid because shared pools may be allowed later by explicit policy.'),
    ('DFLOW_STATUS_INVALID', 'WARNING', 'NORMALIZABLE_ENUM', 'NORMALIZE_FLOW_STATUS', 'candidate_auto', 'not_applicable', 'blocks_phase2_4_check', 'Normalize status to DRAFT, ACTIVE, DISABLED, INACTIVE or RETIRED based on business state.', true, 'Can be scripted only after mapping legacy statuses.'),

    ('APOOL_SOURCE_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'CREATE_OR_REMAP_POOL_SOURCE', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Create the missing source_systems row, remap agent_pools.source_system, or set source_system to * only if explicitly shared.', true, 'Pool source scope affects all routing into the pool.'),
    ('APOOL_SELECTION_STRATEGY_UNSUPPORTED', 'BLOCKER', 'SAFE_NORMALIZATION', 'NORMALIZE_POOL_SELECTION_STRATEGY', 'candidate_auto', 'not_applicable', 'blocks_phase2_4_check', 'Normalize unsupported selection_strategy to LOWEST_LOAD unless an approved migration rule maps it to WEIGHTED_SCORE or MANUAL_ONLY.', false, 'LOWEST_LOAD is the safest supported default for unsupported historical strategies.'),
    ('APOOL_STATUS_INVALID', 'WARNING', 'NORMALIZABLE_ENUM', 'NORMALIZE_POOL_STATUS', 'candidate_auto', 'not_applicable', 'blocks_phase2_4_check', 'Normalize status to ACTIVE, DISABLED, INACTIVE, RETIRED or DRAFT.', true, 'Requires legacy status mapping.'),
    ('APOOL_TYPE_INVALID', 'WARNING', 'NORMALIZABLE_ENUM', 'NORMALIZE_POOL_TYPE', 'candidate_auto', 'not_applicable', 'blocks_phase2_4_check', 'Normalize pool_type to RESOLUTION, WORK_QUEUE, TRIAGE, MANUAL or ESCALATION.', true, 'Requires legacy pool type mapping.'),

    ('APOOL_MEMBER_POOL_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'CREATE_POOL_OR_REMOVE_MEMBER', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Create the missing pool or remove/archive the orphan agent_pool_members row after confirming it is not needed.', true, 'Deleting membership automatically can silently remove routing capacity.'),
    ('APOOL_MEMBER_AGENT_PROFILE_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'CREATE_PROFILE_OR_REMOVE_MEMBER', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Create the missing agent_profiles row in the same tenant or remove/archive the Pool member.', true, 'Business Agent profile is the Core authority for Pool membership.'),
    ('APOOL_MEMBER_AGENT_RUNTIME_MISSING', 'WARNING', 'RUNTIME_OBSERVATION', 'WAIT_FOR_RUNTIME_OR_REVIEW_AGENT_ID', 'manual', 'not_applicable', 'not_applicable', 'Confirm whether the Agent has not connected yet or the member references the wrong agent_id.', true, 'Runtime Agent rows may legitimately be absent before first connection.'),
    ('APOOL_MEMBER_STATUS_INVALID', 'WARNING', 'NORMALIZABLE_ENUM', 'NORMALIZE_MEMBER_STATUS', 'candidate_auto', 'not_applicable', 'blocks_phase2_4_check', 'Normalize member_status to ACTIVE, DISABLED, INACTIVE, RETIRED or SUSPENDED.', true, 'Requires legacy status mapping.'),
    ('APOOL_MEMBER_WEIGHT_INVALID', 'BLOCKER', 'SAFE_NORMALIZATION', 'SET_MEMBER_WEIGHT_DEFAULT', 'candidate_auto', 'not_applicable', 'blocks_phase2_4_check', 'Set null or non-positive weight to 1.', false, 'Weight=1 is already the current default and preserves eligibility without preferential scoring.'),
    ('APOOL_MEMBER_PRIORITY_INVALID', 'BLOCKER', 'SAFE_NORMALIZATION', 'SET_MEMBER_PRIORITY_DEFAULT', 'candidate_auto', 'not_applicable', 'blocks_phase2_4_check', 'Set null or negative priority to 100.', false, 'Priority=100 is already the current default.'),

    ('DPOLICY_FLOW_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'CREATE_FLOW_OR_REMOVE_RULE', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Create the missing dispatch_flows row or remove/archive the orphan dispatch_policies row.', true, 'A rule cannot be evaluated without an owning Source Flow.'),
    ('DPOLICY_SOURCE_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'CREATE_OR_REMAP_RULE_SOURCE', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Create the missing source_systems row or remap dispatch_policies.source_system to an existing source_system.', true, 'Rule source_system must remain aligned with the owning Flow.'),
    ('DPOLICY_SOURCE_FLOW_MISMATCH', 'BLOCKER', 'SAFE_NORMALIZATION', 'ALIGN_RULE_SOURCE_TO_FLOW', 'candidate_auto', 'blocks_phase2_3_fk', 'not_applicable', 'Set dispatch_policies.source_system to the owning dispatch_flows.source_system after confirming the rule belongs to that Flow.', true, 'This can be automated only when rule ownership is already trusted.'),
    ('DPOLICY_TARGET_POOL_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'CREATE_OR_REMAP_RULE_TARGET_POOL', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Create the missing target Agent Pool, remap target_pool_id, or clear target_pool_id to fall back to Flow default only if product behavior is accepted.', true, 'Clearing a target pool changes rule-specific routing.'),
    ('DPOLICY_ROUTING_STRATEGY_UNSUPPORTED', 'WARNING', 'SAFE_NORMALIZATION', 'NORMALIZE_RULE_ROUTING_STRATEGY', 'candidate_auto', 'not_applicable', 'blocks_phase2_4_check', 'Normalize unsupported routing_strategy to LOWEST_LOAD unless an approved migration rule maps it to WEIGHTED_SCORE or MANUAL_ONLY.', false, 'Rule-level strategy is secondary to Pool strategy but should be constrained.'),
    ('DPOLICY_PRIORITY_INVALID', 'BLOCKER', 'SAFE_NORMALIZATION', 'SET_RULE_PRIORITY_DEFAULT', 'candidate_auto', 'not_applicable', 'blocks_phase2_4_check', 'Set null or negative priority to 100.', false, 'Priority=100 is already the current default.'),

    ('LEGACY_CAP_FLOW_MISSING', 'WARNING', 'LEGACY_REFERENCE_CLEANUP', 'ARCHIVE_OR_REMOVE_LEGACY_CAPABILITY', 'manual', 'not_applicable', 'not_applicable', 'Archive or remove preserved reference-only capability rows after confirming the legacy Flow no longer exists.', true, 'Reference-only rows do not block current routing but should not remain misleading.'),
    ('LEGACY_CAP_RULE_MISSING', 'WARNING', 'LEGACY_REFERENCE_CLEANUP', 'ARCHIVE_OR_REMOVE_LEGACY_RULE_CAPABILITY', 'manual', 'not_applicable', 'not_applicable', 'Archive or remove preserved reference-only rule capability rows after confirming the rule no longer exists.', true, 'Reference-only rows are intentionally not FK-blocking in Phase 2-3.'),
    ('LEGACY_FLOW_AGENT_FLOW_MISSING', 'WARNING', 'LEGACY_REFERENCE_CLEANUP', 'ARCHIVE_OR_REMOVE_LEGACY_FLOW_AGENT', 'manual', 'not_applicable', 'not_applicable', 'Archive or remove preserved reference-only direct Agent rows after confirming the Flow no longer exists.', true, 'Direct Agent rows are not part of current routing.'),
    ('LEGACY_FLOW_AGENT_PROFILE_MISSING', 'WARNING', 'LEGACY_REFERENCE_CLEANUP', 'ARCHIVE_OR_REMOVE_LEGACY_AGENT_REFERENCE', 'manual', 'not_applicable', 'not_applicable', 'Archive or remove preserved direct Agent references whose Agent profile no longer exists.', true, 'Direct Agent rows remain reference-only.'),

    ('TASK_SOURCE_MISSING', 'WARNING', 'HISTORY_REPAIR_OR_ACCEPT', 'RECREATE_SOURCE_FOR_HISTORY_OR_ACCEPT', 'manual', 'not_applicable', 'not_applicable', 'Recreate source_systems for historical task readability or explicitly accept the missing historical reference.', true, 'Historical tasks may outlive mutable configuration.'),
    ('TASK_MATCHED_FLOW_MISSING', 'WARNING', 'HISTORY_REPAIR_OR_ACCEPT', 'RECREATE_FLOW_FOR_HISTORY_OR_ACCEPT', 'manual', 'not_applicable', 'not_applicable', 'Recreate archived Flow metadata for history or accept that the historical matched_flow_id is no longer enforced.', true, 'Do not FK historical matched_flow_id until archival policy exists.'),
    ('TASK_MATCHED_RULE_MISSING', 'WARNING', 'HISTORY_REPAIR_OR_ACCEPT', 'RECREATE_RULE_FOR_HISTORY_OR_ACCEPT', 'manual', 'not_applicable', 'not_applicable', 'Recreate archived Rule metadata for history or accept that the historical matched_rule_id is no longer enforced.', true, 'Do not FK historical matched_rule_id until archival policy exists.'),
    ('TASK_ASSIGNED_POOL_MISSING', 'WARNING', 'HISTORY_REPAIR_OR_ACCEPT', 'RECREATE_POOL_FOR_HISTORY_OR_ACCEPT', 'manual', 'not_applicable', 'not_applicable', 'Recreate archived Pool metadata for history or accept the historical assigned_pool_id as denormalized evidence.', true, 'History references should be handled by snapshot/evidence policy.'),
    ('TASK_TARGET_POOL_MISSING', 'WARNING', 'HISTORY_REPAIR_OR_ACCEPT', 'RECREATE_TARGET_POOL_FOR_HISTORY_OR_ACCEPT', 'manual', 'not_applicable', 'not_applicable', 'Recreate archived target Pool metadata for history or accept the historical target_pool_id as denormalized evidence.', true, 'History references should not block current FK work.'),

    ('ASSIGNMENT_TASK_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'RECREATE_TASK_OR_ARCHIVE_ASSIGNMENT', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Recreate the missing Task from evidence or archive/remove the orphan task_assignments row after support review.', true, 'Assignment cannot exist without a Task in the current lifecycle.'),
    ('ASSIGNMENT_AGENT_RUNTIME_MISSING', 'WARNING', 'RUNTIME_OBSERVATION', 'WAIT_FOR_RUNTIME_OR_REVIEW_AGENT_ID', 'manual', 'not_applicable', 'not_applicable', 'Confirm whether the Agent runtime row has not connected yet or the assignment references an invalid agent_id.', true, 'Historical assignment may reference a disconnected Agent.'),
    ('ASSIGNMENT_POOL_MISSING', 'WARNING', 'HISTORY_REPAIR_OR_ACCEPT', 'RECREATE_POOL_FOR_ASSIGNMENT_HISTORY_OR_ACCEPT', 'manual', 'not_applicable', 'not_applicable', 'Recreate archived Pool metadata or accept the historical assignment pool reference as evidence only.', true, 'Do not FK history pool columns until archival model is settled.'),
    ('DREQUEST_ASSIGNMENT_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'RECREATE_ASSIGNMENT_OR_ARCHIVE_REQUEST', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Recreate the missing assignment or archive/remove the orphan dispatch_requests row after support review.', true, 'Dispatch request cannot safely execute without assignment context.'),
    ('DREQUEST_TASK_MISSING', 'BLOCKER', 'MANUAL_REQUIRED', 'RECREATE_TASK_OR_ARCHIVE_REQUEST', 'manual', 'blocks_phase2_3_fk', 'not_applicable', 'Recreate the missing Task or archive/remove the orphan dispatch_requests row after support review.', true, 'Dispatch request cannot safely execute without task context.'),
    ('ROUTING_DECISION_TASK_MISSING', 'WARNING', 'HISTORY_REPAIR_OR_ACCEPT', 'RECREATE_TASK_FOR_DECISION_OR_ACCEPT', 'manual', 'not_applicable', 'not_applicable', 'Recreate Task evidence for historical routing decision or accept it as detached diagnostic history.', true, 'Routing decision history should later use snapshot policy.'),
    ('CALLBACK_TASK_MISSING', 'WARNING', 'HISTORY_REPAIR_OR_ACCEPT', 'RECREATE_TASK_FOR_CALLBACK_OR_ACCEPT', 'manual', 'not_applicable', 'not_applicable', 'Recreate Task evidence for callback history or accept it as detached callback evidence.', true, 'Callback history may remain detached until archival policy exists.'),
    ('CALLBACK_ASSIGNMENT_MISSING', 'WARNING', 'HISTORY_REPAIR_OR_ACCEPT', 'RECREATE_ASSIGNMENT_FOR_CALLBACK_OR_ACCEPT', 'manual', 'not_applicable', 'not_applicable', 'Recreate assignment evidence for callback history or accept it as detached callback evidence.', true, 'Callback assignment FK should wait for history policy.')
),
integrity_findings as (
  -- Source System and Flow references.
  select 'DFLOW_SOURCE_MISSING' as check_id,
         'BLOCKER' as severity,
         f.tenant_id,
         'dispatch_flows' as entity,
         f.flow_id as entity_id,
         'source_systems.source_system_id' as reference_type,
         f.source_system as reference_id,
         'Source Flow references a source_system that does not exist in the same tenant.' as message
    from dispatch_flows f
    left join source_systems s
      on s.tenant_id = f.tenant_id
     and s.source_system_id = f.source_system
   where s.source_system_id is null

  union all
  select 'DFLOW_DEFAULT_POOL_MISSING', 'BLOCKER', f.tenant_id,
         'dispatch_flows', f.flow_id,
         'agent_pools.pool_id', f.default_pool_id,
         'Source Flow default_pool_id does not exist in the same tenant.'
    from dispatch_flows f
    left join agent_pools p
      on p.tenant_id = f.tenant_id
     and p.pool_id = f.default_pool_id
   where f.default_pool_id is not null
     and p.pool_id is null

  union all
  select 'DFLOW_ACTIVE_WITHOUT_DEFAULT_POOL', 'BLOCKER', f.tenant_id,
         'dispatch_flows', f.flow_id,
         'agent_pools.pool_id', f.default_pool_id,
         'ACTIVE Source Flow has no default_pool_id; current model requires a default Agent Pool.'
    from dispatch_flows f
   where upper(coalesce(f.status, 'DRAFT')) in ('ACTIVE', 'ENABLED')
     and nullif(trim(f.default_pool_id), '') is null

  union all
  select 'DFLOW_DEFAULT_POOL_SOURCE_MISMATCH', 'WARNING', f.tenant_id,
         'dispatch_flows', f.flow_id,
         'agent_pools.source_system', p.source_system,
         'Default Agent Pool source_system differs from the Source Flow source_system. Review whether this is intentional shared-pool routing.'
    from dispatch_flows f
    join agent_pools p
      on p.tenant_id = f.tenant_id
     and p.pool_id = f.default_pool_id
   where nullif(trim(coalesce(p.source_system, '')), '') is not null
     and p.source_system <> f.source_system

  union all
  select 'DFLOW_STATUS_INVALID', 'WARNING', f.tenant_id,
         'dispatch_flows', f.flow_id,
         'dispatch_flows.status', f.status,
         'Source Flow status is outside the current allowed status set.'
    from dispatch_flows f
   where upper(coalesce(f.status, '')) not in ('DRAFT', 'ACTIVE', 'ENABLED', 'DISABLED', 'INACTIVE', 'RETIRED')

  -- Agent Pool master data.
  union all
  select 'APOOL_SOURCE_MISSING', 'BLOCKER', p.tenant_id,
         'agent_pools', p.pool_id,
         'source_systems.source_system_id', p.source_system,
         'Agent Pool source_system does not exist in the same tenant.'
    from agent_pools p
    left join source_systems s
      on s.tenant_id = p.tenant_id
     and s.source_system_id = p.source_system
   where nullif(trim(coalesce(p.source_system, '')), '') is not null
     and p.source_system <> '*'
     and s.source_system_id is null

  union all
  select 'APOOL_SELECTION_STRATEGY_UNSUPPORTED', 'BLOCKER', p.tenant_id,
         'agent_pools', p.pool_id,
         'agent_pools.selection_strategy', p.selection_strategy,
         'Agent Pool selection_strategy is not supported by the current Runtime contract.'
    from agent_pools p
   where upper(coalesce(p.selection_strategy, '')) not in ('LOWEST_LOAD', 'WEIGHTED_SCORE', 'MANUAL_ONLY')

  union all
  select 'APOOL_STATUS_INVALID', 'WARNING', p.tenant_id,
         'agent_pools', p.pool_id,
         'agent_pools.status', p.status,
         'Agent Pool status is outside the current allowed status set.'
    from agent_pools p
   where upper(coalesce(p.status, '')) not in ('ACTIVE', 'ENABLED', 'DISABLED', 'INACTIVE', 'RETIRED', 'DRAFT')

  union all
  select 'APOOL_TYPE_INVALID', 'WARNING', p.tenant_id,
         'agent_pools', p.pool_id,
         'agent_pools.pool_type', p.pool_type,
         'Agent Pool pool_type is outside the current known type set.'
    from agent_pools p
   where upper(coalesce(p.pool_type, '')) not in ('RESOLUTION', 'WORK_QUEUE', 'TRIAGE', 'MANUAL', 'ESCALATION')

  -- Pool membership.
  union all
  select 'APOOL_MEMBER_POOL_MISSING', 'BLOCKER', m.tenant_id,
         'agent_pool_members', concat(m.pool_id, '/', m.agent_id),
         'agent_pools.pool_id', m.pool_id,
         'Pool member references an Agent Pool that does not exist in the same tenant.'
    from agent_pool_members m
    left join agent_pools p
      on p.tenant_id = m.tenant_id
     and p.pool_id = m.pool_id
   where p.pool_id is null

  union all
  select 'APOOL_MEMBER_AGENT_PROFILE_MISSING', 'BLOCKER', m.tenant_id,
         'agent_pool_members', concat(m.pool_id, '/', m.agent_id),
         'agent_profiles.agent_id', m.agent_id,
         'Pool member references an Agent profile that does not exist in the same tenant.'
    from agent_pool_members m
    left join agent_profiles ap
      on ap.tenant_id = m.tenant_id
     and ap.agent_id = m.agent_id
   where ap.agent_id is null

  union all
  select 'APOOL_MEMBER_AGENT_RUNTIME_MISSING', 'WARNING', m.tenant_id,
         'agent_pool_members', concat(m.pool_id, '/', m.agent_id),
         'agents.agent_id', m.agent_id,
         'Pool member has no runtime agent row. This may be valid before first connection, but it blocks live dispatch.'
    from agent_pool_members m
    left join agents a
      on a.agent_id = m.agent_id
   where a.agent_id is null

  union all
  select 'APOOL_MEMBER_STATUS_INVALID', 'WARNING', m.tenant_id,
         'agent_pool_members', concat(m.pool_id, '/', m.agent_id),
         'agent_pool_members.member_status', m.member_status,
         'Pool member status is outside the current allowed status set.'
    from agent_pool_members m
   where upper(coalesce(m.member_status, '')) not in ('ACTIVE', 'ENABLED', 'DISABLED', 'INACTIVE', 'RETIRED', 'SUSPENDED')

  union all
  select 'APOOL_MEMBER_WEIGHT_INVALID', 'BLOCKER', m.tenant_id,
         'agent_pool_members', concat(m.pool_id, '/', m.agent_id),
         'agent_pool_members.weight', m.weight::text,
         'Pool member weight must be greater than zero before WEIGHTED_SCORE can be constrained.'
    from agent_pool_members m
   where m.weight is null or m.weight <= 0

  union all
  select 'APOOL_MEMBER_PRIORITY_INVALID', 'BLOCKER', m.tenant_id,
         'agent_pool_members', concat(m.pool_id, '/', m.agent_id),
         'agent_pool_members.priority', m.priority::text,
         'Pool member priority must be greater than or equal to zero before priority constraints can be added.'
    from agent_pool_members m
   where m.priority is null or m.priority < 0

  -- Rule references.
  union all
  select 'DPOLICY_FLOW_MISSING', 'BLOCKER', r.tenant_id,
         'dispatch_policies', r.policy_id,
         'dispatch_flows.flow_id', r.flow_id,
         'Dispatch rule references a Source Flow that does not exist in the same tenant.'
    from dispatch_policies r
    left join dispatch_flows f
      on f.tenant_id = r.tenant_id
     and f.flow_id = r.flow_id
   where f.flow_id is null

  union all
  select 'DPOLICY_SOURCE_MISSING', 'BLOCKER', r.tenant_id,
         'dispatch_policies', r.policy_id,
         'source_systems.source_system_id', r.source_system,
         'Dispatch rule source_system does not exist in the same tenant.'
    from dispatch_policies r
    left join source_systems s
      on s.tenant_id = r.tenant_id
     and s.source_system_id = r.source_system
   where s.source_system_id is null

  union all
  select 'DPOLICY_SOURCE_FLOW_MISMATCH', 'BLOCKER', r.tenant_id,
         'dispatch_policies', r.policy_id,
         'dispatch_flows.source_system', f.source_system,
         'Dispatch rule source_system differs from its owning Source Flow source_system.'
    from dispatch_policies r
    join dispatch_flows f
      on f.tenant_id = r.tenant_id
     and f.flow_id = r.flow_id
   where r.source_system <> f.source_system

  union all
  select 'DPOLICY_TARGET_POOL_MISSING', 'BLOCKER', r.tenant_id,
         'dispatch_policies', r.policy_id,
         'agent_pools.pool_id', r.target_pool_id,
         'Dispatch rule target_pool_id does not exist in the same tenant.'
    from dispatch_policies r
    left join agent_pools p
      on p.tenant_id = r.tenant_id
     and p.pool_id = r.target_pool_id
   where r.target_pool_id is not null
     and p.pool_id is null

  union all
  select 'DPOLICY_ROUTING_STRATEGY_UNSUPPORTED', 'WARNING', r.tenant_id,
         'dispatch_policies', r.policy_id,
         'dispatch_policies.routing_strategy', r.routing_strategy,
         'Dispatch rule routing_strategy is outside current supported Pool strategy names.'
    from dispatch_policies r
   where upper(coalesce(r.routing_strategy, '')) not in ('LOWEST_LOAD', 'WEIGHTED_SCORE', 'MANUAL_ONLY')

  union all
  select 'DPOLICY_PRIORITY_INVALID', 'BLOCKER', r.tenant_id,
         'dispatch_policies', r.policy_id,
         'dispatch_policies.priority', r.priority::text,
         'Dispatch rule priority must be greater than or equal to zero.'
    from dispatch_policies r
   where r.priority is null or r.priority < 0

  -- Preserved legacy/reference-only child rows.
  union all
  select 'LEGACY_CAP_FLOW_MISSING', 'WARNING', c.tenant_id,
         'flow_required_capabilities', c.id,
         'dispatch_flows.flow_id', c.flow_id,
         'Preserved legacy/reference-only capability row points to a missing Source Flow.'
    from flow_required_capabilities c
    left join dispatch_flows f
      on f.tenant_id = c.tenant_id
     and f.flow_id = c.flow_id
   where f.flow_id is null

  union all
  select 'LEGACY_CAP_RULE_MISSING', 'WARNING', c.tenant_id,
         'flow_required_capabilities', c.id,
         'dispatch_policies.policy_id', c.rule_id,
         'Preserved legacy/reference-only capability row points to a missing Dispatch rule.'
    from flow_required_capabilities c
    left join dispatch_policies r
      on r.tenant_id = c.tenant_id
     and r.policy_id = c.rule_id
   where c.rule_id is not null
     and r.policy_id is null

  union all
  select 'LEGACY_FLOW_AGENT_FLOW_MISSING', 'WARNING', fa.tenant_id,
         'flow_agent_assignments', fa.id,
         'dispatch_flows.flow_id', fa.flow_id,
         'Preserved legacy/reference-only direct Agent row points to a missing Source Flow.'
    from flow_agent_assignments fa
    left join dispatch_flows f
      on f.tenant_id = fa.tenant_id
     and f.flow_id = fa.flow_id
   where f.flow_id is null

  union all
  select 'LEGACY_FLOW_AGENT_PROFILE_MISSING', 'WARNING', fa.tenant_id,
         'flow_agent_assignments', fa.id,
         'agent_profiles.agent_id', fa.agent_id,
         'Preserved legacy/reference-only direct Agent row points to a missing Agent profile.'
    from flow_agent_assignments fa
    left join agent_profiles ap
      on ap.tenant_id = fa.tenant_id
     and ap.agent_id = fa.agent_id
   where ap.agent_id is null

  -- Task and assignment history references.
  union all
  select 'TASK_SOURCE_MISSING', 'WARNING', t.tenant_id,
         'tasks', t.task_id,
         'source_systems.source_system_id', t.source_system,
         'Task source_system is missing from the tenant source system registry.'
    from tasks t
    left join source_systems s
      on s.tenant_id = t.tenant_id
     and s.source_system_id = t.source_system
   where t.tenant_id is not null
     and t.source_system is not null
     and s.source_system_id is null

  union all
  select 'TASK_MATCHED_FLOW_MISSING', 'WARNING', t.tenant_id,
         'tasks', t.task_id,
         'dispatch_flows.flow_id', t.matched_flow_id,
         'Task matched_flow_id no longer exists in the same tenant.'
    from tasks t
    left join dispatch_flows f
      on f.tenant_id = t.tenant_id
     and f.flow_id = t.matched_flow_id
   where t.tenant_id is not null
     and t.matched_flow_id is not null
     and f.flow_id is null

  union all
  select 'TASK_MATCHED_RULE_MISSING', 'WARNING', t.tenant_id,
         'tasks', t.task_id,
         'dispatch_policies.policy_id', t.matched_rule_id,
         'Task matched_rule_id no longer exists in the same tenant.'
    from tasks t
    left join dispatch_policies r
      on r.tenant_id = t.tenant_id
     and r.policy_id = t.matched_rule_id
   where t.tenant_id is not null
     and t.matched_rule_id is not null
     and r.policy_id is null

  union all
  select 'TASK_ASSIGNED_POOL_MISSING', 'WARNING', t.tenant_id,
         'tasks', t.task_id,
         'agent_pools.pool_id', t.assigned_pool_id,
         'Task assigned_pool_id no longer exists in the same tenant.'
    from tasks t
    left join agent_pools p
      on p.tenant_id = t.tenant_id
     and p.pool_id = t.assigned_pool_id
   where t.tenant_id is not null
     and t.assigned_pool_id is not null
     and p.pool_id is null

  union all
  select 'TASK_TARGET_POOL_MISSING', 'WARNING', t.tenant_id,
         'tasks', t.task_id,
         'agent_pools.pool_id', t.target_pool_id,
         'Task target_pool_id no longer exists in the same tenant.'
    from tasks t
    left join agent_pools p
      on p.tenant_id = t.tenant_id
     and p.pool_id = t.target_pool_id
   where t.tenant_id is not null
     and t.target_pool_id is not null
     and p.pool_id is null

  union all
  select 'ASSIGNMENT_TASK_MISSING', 'BLOCKER', ta_tenant.tenant_id,
         'task_assignments', ta.assignment_id,
         'tasks.task_id', ta.task_id,
         'Task assignment references a task_id that does not exist.'
    from task_assignments ta
    left join tasks t
      on t.task_id = ta.task_id
    left join lateral (select coalesce(t.tenant_id, '<unknown>') as tenant_id) ta_tenant on true
   where t.task_id is null

  union all
  select 'ASSIGNMENT_AGENT_RUNTIME_MISSING', 'WARNING', coalesce(t.tenant_id, '<unknown>'),
         'task_assignments', ta.assignment_id,
         'agents.agent_id', ta.agent_id,
         'Task assignment references an Agent runtime row that does not exist.'
    from task_assignments ta
    left join tasks t
      on t.task_id = ta.task_id
    left join agents a
      on a.agent_id = ta.agent_id
   where a.agent_id is null

  union all
  select 'ASSIGNMENT_POOL_MISSING', 'WARNING', t.tenant_id,
         'task_assignments', ta.assignment_id,
         'agent_pools.pool_id', coalesce(ta.assigned_pool_id, ta.target_pool_id),
         'Task assignment references an assigned/target pool that no longer exists in the task tenant.'
    from task_assignments ta
    join tasks t
      on t.task_id = ta.task_id
    left join agent_pools p
      on p.tenant_id = t.tenant_id
     and p.pool_id = coalesce(ta.assigned_pool_id, ta.target_pool_id)
   where coalesce(ta.assigned_pool_id, ta.target_pool_id) is not null
     and p.pool_id is null

  union all
  select 'DREQUEST_ASSIGNMENT_MISSING', 'BLOCKER', coalesce(t.tenant_id, '<unknown>'),
         'dispatch_requests', dr.dispatch_request_id,
         'task_assignments.assignment_id', dr.assignment_id,
         'Dispatch request references an assignment_id that does not exist.'
    from dispatch_requests dr
    left join task_assignments ta
      on ta.assignment_id = dr.assignment_id
    left join tasks t
      on t.task_id = coalesce(dr.task_id, ta.task_id)
   where dr.assignment_id is not null
     and ta.assignment_id is null

  union all
  select 'DREQUEST_TASK_MISSING', 'BLOCKER', '<unknown>',
         'dispatch_requests', dr.dispatch_request_id,
         'tasks.task_id', dr.task_id,
         'Dispatch request references a task_id that does not exist.'
    from dispatch_requests dr
    left join tasks t
      on t.task_id = dr.task_id
   where dr.task_id is not null
     and t.task_id is null

  union all
  select 'ROUTING_DECISION_TASK_MISSING', 'WARNING', coalesce(rd.tenant_id, '<unknown>'),
         'routing_decisions', rd.decision_id,
         'tasks.task_id', rd.task_id,
         'Routing decision references a task_id that does not exist.'
    from routing_decisions rd
    left join tasks t
      on t.task_id = rd.task_id
   where rd.task_id is not null
     and t.task_id is null

  union all
  select 'CALLBACK_TASK_MISSING', 'WARNING', coalesce(t.tenant_id, '<unknown>'),
         'task_callbacks', cb.callback_id,
         'tasks.task_id', cb.task_id,
         'Task callback references a task_id that does not exist.'
    from task_callbacks cb
    left join tasks t
      on t.task_id = cb.task_id
   where cb.task_id is not null
     and t.task_id is null

  union all
  select 'CALLBACK_ASSIGNMENT_MISSING', 'WARNING', coalesce(t.tenant_id, '<unknown>'),
         'task_callbacks', cb.callback_id,
         'task_assignments.assignment_id', cb.assignment_id,
         'Task callback references an assignment_id that does not exist.'
    from task_callbacks cb
    left join task_assignments ta
      on ta.assignment_id = cb.assignment_id
    left join tasks t
      on t.task_id = coalesce(cb.task_id, ta.task_id)
   where cb.assignment_id is not null
     and ta.assignment_id is null
)
select f.check_id,
       p.default_action,
       f.tenant_id,
       f.entity,
       f.entity_id,
       f.reference_type,
       f.reference_id,
       case f.check_id
         when 'APOOL_SELECTION_STRATEGY_UNSUPPORTED' then 'Would normalize agent_pools.selection_strategy to LOWEST_LOAD after approval.'
         when 'APOOL_MEMBER_WEIGHT_INVALID' then 'Would normalize agent_pool_members.weight to 1.'
         when 'APOOL_MEMBER_PRIORITY_INVALID' then 'Would normalize agent_pool_members.priority to 100.'
         when 'DPOLICY_SOURCE_FLOW_MISMATCH' then 'Would align dispatch_policies.source_system to owning dispatch_flows.source_system after ownership confirmation.'
         when 'DPOLICY_ROUTING_STRATEGY_UNSUPPORTED' then 'Would normalize dispatch_policies.routing_strategy to LOWEST_LOAD after approval.'
         when 'DPOLICY_PRIORITY_INVALID' then 'Would normalize dispatch_policies.priority to 100.'
         else 'No candidate auto-repair preview is defined for this finding.'
       end as candidate_auto_repair_preview
  from integrity_findings f
  join repair_plan p
    on p.check_id = f.check_id
 where p.automation_policy = 'candidate_auto'
 order by f.check_id,
          f.tenant_id,
          f.entity,
          f.entity_id;
