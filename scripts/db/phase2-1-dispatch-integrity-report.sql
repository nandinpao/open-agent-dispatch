-- Phase 2-1 Dispatch Referential Integrity Report
--
-- Purpose:
--   Report data defects that must be repaired before Phase 2-2 can safely add
--   composite foreign keys and check constraints.
--
-- Safety:
--   This script is read-only. It creates no table, view, function, index or constraint.
--   It is intended to be run by psql against a migrated database.
--
-- Output:
--   1. finding summary by severity and check_id
--   2. finding detail rows
--
-- Severity convention:
--   BLOCKER = must repair before FK/check migration
--   WARNING = allowed in compatibility data but should be reviewed before release
--   INFO    = useful diagnostic signal for Phase 2 planning

\pset pager off
\pset null '<null>'

\echo '== Phase 2-1 Dispatch Referential Integrity Summary =='

with integrity_findings as (
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
select severity,
       check_id,
       count(*) as finding_count
  from integrity_findings
 group by severity, check_id
 order by case severity when 'BLOCKER' then 1 when 'WARNING' then 2 else 3 end,
          check_id;

\echo '== Phase 2-1 Dispatch Referential Integrity Details =='

with integrity_findings as (
  select 'DFLOW_SOURCE_MISSING' as check_id, 'BLOCKER' as severity, f.tenant_id, 'dispatch_flows' as entity, f.flow_id as entity_id, 'source_systems.source_system_id' as reference_type, f.source_system as reference_id, 'Source Flow references a source_system that does not exist in the same tenant.' as message from dispatch_flows f left join source_systems s on s.tenant_id=f.tenant_id and s.source_system_id=f.source_system where s.source_system_id is null
  union all select 'DFLOW_DEFAULT_POOL_MISSING','BLOCKER',f.tenant_id,'dispatch_flows',f.flow_id,'agent_pools.pool_id',f.default_pool_id,'Source Flow default_pool_id does not exist in the same tenant.' from dispatch_flows f left join agent_pools p on p.tenant_id=f.tenant_id and p.pool_id=f.default_pool_id where f.default_pool_id is not null and p.pool_id is null
  union all select 'DFLOW_ACTIVE_WITHOUT_DEFAULT_POOL','BLOCKER',f.tenant_id,'dispatch_flows',f.flow_id,'agent_pools.pool_id',f.default_pool_id,'ACTIVE Source Flow has no default_pool_id; current model requires a default Agent Pool.' from dispatch_flows f where upper(coalesce(f.status,'DRAFT')) in ('ACTIVE','ENABLED') and nullif(trim(f.default_pool_id),'') is null
  union all select 'DFLOW_DEFAULT_POOL_SOURCE_MISMATCH','WARNING',f.tenant_id,'dispatch_flows',f.flow_id,'agent_pools.source_system',p.source_system,'Default Agent Pool source_system differs from the Source Flow source_system. Review whether this is intentional shared-pool routing.' from dispatch_flows f join agent_pools p on p.tenant_id=f.tenant_id and p.pool_id=f.default_pool_id where nullif(trim(coalesce(p.source_system,'')),'') is not null and p.source_system<>f.source_system
  union all select 'DFLOW_STATUS_INVALID','WARNING',f.tenant_id,'dispatch_flows',f.flow_id,'dispatch_flows.status',f.status,'Source Flow status is outside the current allowed status set.' from dispatch_flows f where upper(coalesce(f.status,'')) not in ('DRAFT','ACTIVE','ENABLED','DISABLED','INACTIVE','RETIRED')
  union all select 'APOOL_SOURCE_MISSING','BLOCKER',p.tenant_id,'agent_pools',p.pool_id,'source_systems.source_system_id',p.source_system,'Agent Pool source_system does not exist in the same tenant.' from agent_pools p left join source_systems s on s.tenant_id=p.tenant_id and s.source_system_id=p.source_system where nullif(trim(coalesce(p.source_system,'')),'') is not null and p.source_system<>'*' and s.source_system_id is null
  union all select 'APOOL_SELECTION_STRATEGY_UNSUPPORTED','BLOCKER',p.tenant_id,'agent_pools',p.pool_id,'agent_pools.selection_strategy',p.selection_strategy,'Agent Pool selection_strategy is not supported by the current Runtime contract.' from agent_pools p where upper(coalesce(p.selection_strategy,'')) not in ('LOWEST_LOAD','WEIGHTED_SCORE','MANUAL_ONLY')
  union all select 'APOOL_STATUS_INVALID','WARNING',p.tenant_id,'agent_pools',p.pool_id,'agent_pools.status',p.status,'Agent Pool status is outside the current allowed status set.' from agent_pools p where upper(coalesce(p.status,'')) not in ('ACTIVE','ENABLED','DISABLED','INACTIVE','RETIRED','DRAFT')
  union all select 'APOOL_TYPE_INVALID','WARNING',p.tenant_id,'agent_pools',p.pool_id,'agent_pools.pool_type',p.pool_type,'Agent Pool pool_type is outside the current known type set.' from agent_pools p where upper(coalesce(p.pool_type,'')) not in ('RESOLUTION','WORK_QUEUE','TRIAGE','MANUAL','ESCALATION')
  union all select 'APOOL_MEMBER_POOL_MISSING','BLOCKER',m.tenant_id,'agent_pool_members',concat(m.pool_id,'/',m.agent_id),'agent_pools.pool_id',m.pool_id,'Pool member references an Agent Pool that does not exist in the same tenant.' from agent_pool_members m left join agent_pools p on p.tenant_id=m.tenant_id and p.pool_id=m.pool_id where p.pool_id is null
  union all select 'APOOL_MEMBER_AGENT_PROFILE_MISSING','BLOCKER',m.tenant_id,'agent_pool_members',concat(m.pool_id,'/',m.agent_id),'agent_profiles.agent_id',m.agent_id,'Pool member references an Agent profile that does not exist in the same tenant.' from agent_pool_members m left join agent_profiles ap on ap.tenant_id=m.tenant_id and ap.agent_id=m.agent_id where ap.agent_id is null
  union all select 'APOOL_MEMBER_AGENT_RUNTIME_MISSING','WARNING',m.tenant_id,'agent_pool_members',concat(m.pool_id,'/',m.agent_id),'agents.agent_id',m.agent_id,'Pool member has no runtime agent row. This may be valid before first connection, but it blocks live dispatch.' from agent_pool_members m left join agents a on a.agent_id=m.agent_id where a.agent_id is null
  union all select 'APOOL_MEMBER_STATUS_INVALID','WARNING',m.tenant_id,'agent_pool_members',concat(m.pool_id,'/',m.agent_id),'agent_pool_members.member_status',m.member_status,'Pool member status is outside the current allowed status set.' from agent_pool_members m where upper(coalesce(m.member_status,'')) not in ('ACTIVE','ENABLED','DISABLED','INACTIVE','RETIRED','SUSPENDED')
  union all select 'APOOL_MEMBER_WEIGHT_INVALID','BLOCKER',m.tenant_id,'agent_pool_members',concat(m.pool_id,'/',m.agent_id),'agent_pool_members.weight',m.weight::text,'Pool member weight must be greater than zero before WEIGHTED_SCORE can be constrained.' from agent_pool_members m where m.weight is null or m.weight<=0
  union all select 'APOOL_MEMBER_PRIORITY_INVALID','BLOCKER',m.tenant_id,'agent_pool_members',concat(m.pool_id,'/',m.agent_id),'agent_pool_members.priority',m.priority::text,'Pool member priority must be greater than or equal to zero before priority constraints can be added.' from agent_pool_members m where m.priority is null or m.priority<0
  union all select 'DPOLICY_FLOW_MISSING','BLOCKER',r.tenant_id,'dispatch_policies',r.policy_id,'dispatch_flows.flow_id',r.flow_id,'Dispatch rule references a Source Flow that does not exist in the same tenant.' from dispatch_policies r left join dispatch_flows f on f.tenant_id=r.tenant_id and f.flow_id=r.flow_id where f.flow_id is null
  union all select 'DPOLICY_SOURCE_MISSING','BLOCKER',r.tenant_id,'dispatch_policies',r.policy_id,'source_systems.source_system_id',r.source_system,'Dispatch rule source_system does not exist in the same tenant.' from dispatch_policies r left join source_systems s on s.tenant_id=r.tenant_id and s.source_system_id=r.source_system where s.source_system_id is null
  union all select 'DPOLICY_SOURCE_FLOW_MISMATCH','BLOCKER',r.tenant_id,'dispatch_policies',r.policy_id,'dispatch_flows.source_system',f.source_system,'Dispatch rule source_system differs from its owning Source Flow source_system.' from dispatch_policies r join dispatch_flows f on f.tenant_id=r.tenant_id and f.flow_id=r.flow_id where r.source_system<>f.source_system
  union all select 'DPOLICY_TARGET_POOL_MISSING','BLOCKER',r.tenant_id,'dispatch_policies',r.policy_id,'agent_pools.pool_id',r.target_pool_id,'Dispatch rule target_pool_id does not exist in the same tenant.' from dispatch_policies r left join agent_pools p on p.tenant_id=r.tenant_id and p.pool_id=r.target_pool_id where r.target_pool_id is not null and p.pool_id is null
  union all select 'DPOLICY_ROUTING_STRATEGY_UNSUPPORTED','WARNING',r.tenant_id,'dispatch_policies',r.policy_id,'dispatch_policies.routing_strategy',r.routing_strategy,'Dispatch rule routing_strategy is outside current supported Pool strategy names.' from dispatch_policies r where upper(coalesce(r.routing_strategy,'')) not in ('LOWEST_LOAD','WEIGHTED_SCORE','MANUAL_ONLY')
  union all select 'DPOLICY_PRIORITY_INVALID','BLOCKER',r.tenant_id,'dispatch_policies',r.policy_id,'dispatch_policies.priority',r.priority::text,'Dispatch rule priority must be greater than or equal to zero.' from dispatch_policies r where r.priority is null or r.priority<0
  union all select 'LEGACY_CAP_FLOW_MISSING','WARNING',c.tenant_id,'flow_required_capabilities',c.id,'dispatch_flows.flow_id',c.flow_id,'Preserved legacy/reference-only capability row points to a missing Source Flow.' from flow_required_capabilities c left join dispatch_flows f on f.tenant_id=c.tenant_id and f.flow_id=c.flow_id where f.flow_id is null
  union all select 'LEGACY_CAP_RULE_MISSING','WARNING',c.tenant_id,'flow_required_capabilities',c.id,'dispatch_policies.policy_id',c.rule_id,'Preserved legacy/reference-only capability row points to a missing Dispatch rule.' from flow_required_capabilities c left join dispatch_policies r on r.tenant_id=c.tenant_id and r.policy_id=c.rule_id where c.rule_id is not null and r.policy_id is null
  union all select 'LEGACY_FLOW_AGENT_FLOW_MISSING','WARNING',fa.tenant_id,'flow_agent_assignments',fa.id,'dispatch_flows.flow_id',fa.flow_id,'Preserved legacy/reference-only direct Agent row points to a missing Source Flow.' from flow_agent_assignments fa left join dispatch_flows f on f.tenant_id=fa.tenant_id and f.flow_id=fa.flow_id where f.flow_id is null
  union all select 'LEGACY_FLOW_AGENT_PROFILE_MISSING','WARNING',fa.tenant_id,'flow_agent_assignments',fa.id,'agent_profiles.agent_id',fa.agent_id,'Preserved legacy/reference-only direct Agent row points to a missing Agent profile.' from flow_agent_assignments fa left join agent_profiles ap on ap.tenant_id=fa.tenant_id and ap.agent_id=fa.agent_id where ap.agent_id is null
  union all select 'TASK_SOURCE_MISSING','WARNING',t.tenant_id,'tasks',t.task_id,'source_systems.source_system_id',t.source_system,'Task source_system is missing from the tenant source system registry.' from tasks t left join source_systems s on s.tenant_id=t.tenant_id and s.source_system_id=t.source_system where t.tenant_id is not null and t.source_system is not null and s.source_system_id is null
  union all select 'TASK_MATCHED_FLOW_MISSING','WARNING',t.tenant_id,'tasks',t.task_id,'dispatch_flows.flow_id',t.matched_flow_id,'Task matched_flow_id no longer exists in the same tenant.' from tasks t left join dispatch_flows f on f.tenant_id=t.tenant_id and f.flow_id=t.matched_flow_id where t.tenant_id is not null and t.matched_flow_id is not null and f.flow_id is null
  union all select 'TASK_MATCHED_RULE_MISSING','WARNING',t.tenant_id,'tasks',t.task_id,'dispatch_policies.policy_id',t.matched_rule_id,'Task matched_rule_id no longer exists in the same tenant.' from tasks t left join dispatch_policies r on r.tenant_id=t.tenant_id and r.policy_id=t.matched_rule_id where t.tenant_id is not null and t.matched_rule_id is not null and r.policy_id is null
  union all select 'TASK_ASSIGNED_POOL_MISSING','WARNING',t.tenant_id,'tasks',t.task_id,'agent_pools.pool_id',t.assigned_pool_id,'Task assigned_pool_id no longer exists in the same tenant.' from tasks t left join agent_pools p on p.tenant_id=t.tenant_id and p.pool_id=t.assigned_pool_id where t.tenant_id is not null and t.assigned_pool_id is not null and p.pool_id is null
  union all select 'TASK_TARGET_POOL_MISSING','WARNING',t.tenant_id,'tasks',t.task_id,'agent_pools.pool_id',t.target_pool_id,'Task target_pool_id no longer exists in the same tenant.' from tasks t left join agent_pools p on p.tenant_id=t.tenant_id and p.pool_id=t.target_pool_id where t.tenant_id is not null and t.target_pool_id is not null and p.pool_id is null
  union all select 'ASSIGNMENT_TASK_MISSING','BLOCKER',coalesce(t.tenant_id,'<unknown>'),'task_assignments',ta.assignment_id,'tasks.task_id',ta.task_id,'Task assignment references a task_id that does not exist.' from task_assignments ta left join tasks t on t.task_id=ta.task_id where t.task_id is null
  union all select 'ASSIGNMENT_AGENT_RUNTIME_MISSING','WARNING',coalesce(t.tenant_id,'<unknown>'),'task_assignments',ta.assignment_id,'agents.agent_id',ta.agent_id,'Task assignment references an Agent runtime row that does not exist.' from task_assignments ta left join tasks t on t.task_id=ta.task_id left join agents a on a.agent_id=ta.agent_id where a.agent_id is null
  union all select 'ASSIGNMENT_POOL_MISSING','WARNING',t.tenant_id,'task_assignments',ta.assignment_id,'agent_pools.pool_id',coalesce(ta.assigned_pool_id,ta.target_pool_id),'Task assignment references an assigned/target pool that no longer exists in the task tenant.' from task_assignments ta join tasks t on t.task_id=ta.task_id left join agent_pools p on p.tenant_id=t.tenant_id and p.pool_id=coalesce(ta.assigned_pool_id,ta.target_pool_id) where coalesce(ta.assigned_pool_id,ta.target_pool_id) is not null and p.pool_id is null
  union all select 'DREQUEST_ASSIGNMENT_MISSING','BLOCKER',coalesce(t.tenant_id,'<unknown>'),'dispatch_requests',dr.dispatch_request_id,'task_assignments.assignment_id',dr.assignment_id,'Dispatch request references an assignment_id that does not exist.' from dispatch_requests dr left join task_assignments ta on ta.assignment_id=dr.assignment_id left join tasks t on t.task_id=coalesce(dr.task_id,ta.task_id) where dr.assignment_id is not null and ta.assignment_id is null
  union all select 'DREQUEST_TASK_MISSING','BLOCKER','<unknown>','dispatch_requests',dr.dispatch_request_id,'tasks.task_id',dr.task_id,'Dispatch request references a task_id that does not exist.' from dispatch_requests dr left join tasks t on t.task_id=dr.task_id where dr.task_id is not null and t.task_id is null
  union all select 'ROUTING_DECISION_TASK_MISSING','WARNING',coalesce(rd.tenant_id,'<unknown>'),'routing_decisions',rd.decision_id,'tasks.task_id',rd.task_id,'Routing decision references a task_id that does not exist.' from routing_decisions rd left join tasks t on t.task_id=rd.task_id where rd.task_id is not null and t.task_id is null
  union all select 'CALLBACK_TASK_MISSING','WARNING',coalesce(t.tenant_id,'<unknown>'),'task_callbacks',cb.callback_id,'tasks.task_id',cb.task_id,'Task callback references a task_id that does not exist.' from task_callbacks cb left join tasks t on t.task_id=cb.task_id where cb.task_id is not null and t.task_id is null
  union all select 'CALLBACK_ASSIGNMENT_MISSING','WARNING',coalesce(t.tenant_id,'<unknown>'),'task_callbacks',cb.callback_id,'task_assignments.assignment_id',cb.assignment_id,'Task callback references an assignment_id that does not exist.' from task_callbacks cb left join task_assignments ta on ta.assignment_id=cb.assignment_id left join tasks t on t.task_id=coalesce(cb.task_id,ta.task_id) where cb.assignment_id is not null and ta.assignment_id is null
)
select severity,
       check_id,
       tenant_id,
       entity,
       entity_id,
       reference_type,
       reference_id,
       message
  from integrity_findings
 order by case severity when 'BLOCKER' then 1 when 'WARNING' then 2 else 3 end,
          check_id,
          tenant_id,
          entity,
          entity_id;
