-- P1 DB-backed Dispatch Flow CRUD manual verification.
-- Run after creating or updating a Dispatch Flow from /dispatch-flows.

select tenant_id, flow_id, flow_code, flow_name, source_system, status, updated_at
from dispatch_flows
order by updated_at desc;

select tenant_id, policy_id, policy_code, flow_id, rule_scope, event_stage,
       source_system, event_type, object_type, error_code, requested_skill, status, updated_at
from dispatch_policies
where flow_id is not null
order by updated_at desc;

select tenant_id, id, flow_id, rule_id, event_stage, agent_role, skill_code,
       skill_name, skill_kind, required, openclaw_skill, updated_at
from flow_required_skills
order by updated_at desc;

select tenant_id, id, flow_id, agent_id, event_stage, agent_role,
       assignment_status, runtime_status, approval_status, readiness_status, updated_at
from flow_agent_assignments
order by updated_at desc;
