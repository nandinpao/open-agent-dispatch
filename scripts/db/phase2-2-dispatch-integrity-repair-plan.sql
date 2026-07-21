-- Phase 2-2 Dispatch Integrity Repair Plan
--
-- Purpose:
--   Classify each Phase 2-1 integrity finding into an explicit repair strategy
--   before any Phase 2-3 foreign key or Phase 2-4 check constraint migration.
--
-- Safety:
--   This script is read-only. It emits a static repair plan and performs no
--   schema or data mutation.
--
-- Output:
--   One row per Phase 2-1 check_id with repair classification, default action,
--   automation policy and constraint-readiness impact.

\pset pager off
\pset null '<null>'

\echo '== Phase 2-2 Dispatch Integrity Repair Plan =='

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
)
select check_id,
       severity,
       repair_class,
       default_action,
       automation_policy,
       fk_readiness,
       check_readiness,
       manual_review_required,
       proposed_fix,
       notes
  from repair_plan
 order by case severity when 'BLOCKER' then 1 when 'WARNING' then 2 else 3 end,
          check_id;
