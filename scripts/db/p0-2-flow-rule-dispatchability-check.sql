-- P0-2 Flow Rule dispatchability validation runbook.
-- Run this after migrations finish.  Do not start with Postman/UI testing until
-- dispatch_p0_2_acceptance_failures returns zero rows.

\echo '== P0-2 acceptance failures: expected 0 rows =='
select *
from dispatch_p0_2_acceptance_failures
order by check_id;

\echo '== P0-2 acceptance report =='
select *
from dispatch_p0_2_acceptance_report
order by check_id;

\echo '== ERP/MES/CMS dispatchability summary =='
select *
from dispatch_p0_2_system_dispatchability_summary
order by source_system;

\echo '== Active Flow-owned rule dispatchability failures =='
select tenant_id, source_system, flow_code, rule_code, event_stage, object_type,
       event_type, error_code, requested_skill, required_skill_matches,
       ready_flow_agent_assignments, assigned_agents_with_requested_skill_grant,
       dispatchable_agent_count, dispatchability_status, remediation
from dispatch_p0_2_flow_rule_dispatchability_failures
order by source_system, flow_code, rule_code;

\echo '== Active Flow-owned rule dispatchability report =='
select tenant_id, source_system, flow_code, rule_code, event_stage, object_type,
       event_type, error_code, requested_skill, required_skill_matches,
       approved_flow_agent_assignments, ready_flow_agent_assignments,
       assigned_agents_with_requested_skill_grant, dispatchable_agent_count,
       flow_rule_task_evidence_count, dispatchability_status
from dispatch_p0_2_flow_rule_dispatchability_report
order by source_system, flow_code, rule_code;

\echo '== Non-terminal ERP/MES/CMS task routing defects =='
select tenant_id, task_id, source_system, event_stage, object_type, event_type,
       error_code, routing_path, routing_policy, matched_flow_id, matched_rule_id,
       requested_skill, active_flow_rule_candidates, candidate_flow_id,
       candidate_rule_id, candidate_requested_skill, assignment_count,
       assigned_agent_count, latest_assignment_agent_id,
       selected_agent_decision_count, latest_selected_agent_id,
       task_evidence_status
from dispatch_p0_2_task_routing_defects
order by source_system, event_type, task_id;

\echo '== Latest ERP/MES/CMS task routing evidence sample =='
select tenant_id, task_id, source_system, event_stage, object_type, event_type,
       error_code, routing_path, matched_flow_id, matched_rule_id,
       requested_skill, assigned_agent_count, latest_assignment_agent_id,
       selected_agent_decision_count, latest_selected_agent_id,
       task_evidence_status, created_at, updated_at
from dispatch_p0_2_task_routing_evidence_report
order by updated_at desc nulls last, created_at desc nulls last
limit 50;
