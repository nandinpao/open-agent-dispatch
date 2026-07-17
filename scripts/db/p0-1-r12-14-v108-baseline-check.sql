-- P0-1 manual DB verification for R12.14 / V108 baseline.
-- Run this after the local stack/migration has completed.
-- Example:
--   docker compose exec postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
--     -f /path/to/scripts/db/p0-1-r12-14-v108-baseline-check.sql

\echo '== P0-1 Flyway V106/V107/V108/V109 status =='
select installed_rank, version, script, success
from flyway_schema_history
where version in ('106','107','108','109')
order by installed_rank;

\echo '== P0-1 baseline report =='
select check_id, check_name, actual_count, expected_min, status
from dispatch_p0_1_r12_14_v108_baseline_report
order by check_id;

\echo '== P0-1 failures only =='
select check_id, check_name, actual_count, expected_min, remediation
from dispatch_p0_1_r12_14_v108_baseline_failures
order by check_id;

\echo '== ERP Flow-owned policies =='
select tenant_id, flow_id, policy_code, source_system, object_type, event_type,
       error_code, event_stage, requested_skill, status
from dispatch_policies
where tenant_id in ('tenant-a','default')
  and upper(coalesce(source_system, origin_source_system, '')) = 'ERP'
  and flow_id is not null
order by tenant_id, policy_code;

\echo '== ERP Flow required skills =='
select tenant_id, flow_id, rule_id, skill_code, event_stage, required
from flow_required_skills
where tenant_id in ('tenant-a','default')
  and skill_code like 'ERP_%'
order by tenant_id, flow_id, skill_code;

\echo '== ERP Flow Agent assignments =='
select faa.tenant_id, df.flow_code, faa.flow_id, faa.agent_id, faa.event_stage,
       faa.assignment_status, faa.runtime_status, faa.approval_status,
       faa.skill_coverage_total, faa.skill_coverage_matched, faa.readiness_status
from flow_agent_assignments faa
join dispatch_flows df on df.tenant_id = faa.tenant_id and df.flow_id = faa.flow_id
where faa.tenant_id in ('tenant-a','default')
  and df.flow_code = 'FLOW_ERP_INCIDENT_RESPONSE'
order by faa.tenant_id, faa.agent_id;

\echo '== ERP Agent capability grants =='
select tenant_id, agent_id, capability_code, status, source, approved_at, expires_at
from agent_capability_assignments
where tenant_id = 'tenant-a'
  and agent_id = 'agent-cluster-node-001-003'
  and capability_code in (
    'ERP_VENDOR_MASTER_RISK_TRIAGE',
    'ERP_PAYMENT_RISK_TRIAGE',
    'ERP_BUSINESS_EVENT_TRIAGE',
    'ERP_BUSINESS_REVIEWER'
  )
order by capability_code;

\echo '== Existing task repair diagnostics =='
select tenant_id, task_id, source_system, object_type, event_type, error_code,
       event_stage, routing_path, matched_flow_id, matched_rule_id,
       requested_skill, repair_status
from dispatch_r12_11_existing_task_flow_repair_report
where tenant_id = 'tenant-a'
  and source_system in ('ERP','MES','CMS')
order by source_system, event_type, task_id;
