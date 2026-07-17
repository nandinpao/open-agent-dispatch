-- P6 Empty-DB Flow Rule E2E evidence check.
-- Usage:
--   psql "$DATABASE_URL" -v tenant_id=tenant-p6-empty-db -f scripts/db/p6-empty-db-flow-rule-e2e-check.sql

\set ON_ERROR_STOP on
\if :{?tenant_id}
\else
\set tenant_id 'tenant-p6-empty-db'
\endif

\echo '== P6 Dispatch Flow records =='
select f.tenant_id, f.flow_id, f.flow_code, f.source_system, f.status,
       count(distinct p.policy_id) as rule_count,
       count(distinct s.id) as skill_count,
       count(distinct a.id) as agent_assignment_count
from dispatch_flows f
left join dispatch_policies p on p.tenant_id = f.tenant_id and p.flow_id = f.flow_id
left join flow_required_skills s on s.tenant_id = f.tenant_id and s.flow_id = f.flow_id
left join flow_agent_assignments a on a.tenant_id = f.tenant_id and a.flow_id = f.flow_id
where f.tenant_id = :'tenant_id'
  and f.flow_code like 'P6\_%' escape '\'
group by f.tenant_id, f.flow_id, f.flow_code, f.source_system, f.status
order by f.source_system, f.flow_code;

\echo '== P6 Flow Rule dispatchability chain =='
with active_rules as (
  select p.tenant_id, p.flow_id, p.policy_id as rule_id, p.policy_code, p.source_system,
         coalesce(p.event_stage, 'EXTERNAL') as event_stage,
         p.object_type, p.event_type, p.error_code, p.requested_skill, p.status
  from dispatch_policies p
  where p.tenant_id = :'tenant_id'
    and p.policy_code like 'P6\_%' escape '\'
    and p.flow_id is not null
), chain as (
  select ar.*,
         exists (
           select 1 from flow_required_skills s
           where s.tenant_id = ar.tenant_id
             and s.flow_id = ar.flow_id
             and upper(s.skill_code) = upper(ar.requested_skill)
             and upper(coalesce(s.event_stage, 'EXTERNAL')) = upper(coalesce(ar.event_stage, 'EXTERNAL'))
         ) as has_required_skill,
         exists (
           select 1 from flow_agent_assignments a
           where a.tenant_id = ar.tenant_id
             and a.flow_id = ar.flow_id
             and upper(coalesce(a.assignment_status, 'DRAFT')) in ('ACTIVE','ENABLED')
             and upper(coalesce(a.approval_status, 'PENDING')) in ('APPROVED','ACTIVE')
         ) as has_approved_agent_assignment,
         exists (
           select 1
           from flow_agent_assignments a
           join agent_capability_assignments aca
             on aca.tenant_id = a.tenant_id
            and aca.agent_id = a.agent_id
            and upper(aca.capability_code) = upper(ar.requested_skill)
            and upper(coalesce(aca.status, 'PENDING')) in ('APPROVED','ACTIVE')
            and (aca.expires_at is null or aca.expires_at > now())
           where a.tenant_id = ar.tenant_id
             and a.flow_id = ar.flow_id
             and upper(coalesce(a.assignment_status, 'DRAFT')) in ('ACTIVE','ENABLED')
             and upper(coalesce(a.approval_status, 'PENDING')) in ('APPROVED','ACTIVE')
             and upper(coalesce(a.readiness_status, 'NOT_READY')) in ('READY','FLOW_AGENT_READY')
         ) as has_dispatchable_agent
  from active_rules ar
)
select *,
       case
         when status <> 'ACTIVE' then 'RULE_NOT_ACTIVE'
         when requested_skill is null then 'NO_REQUESTED_SKILL'
         when not has_required_skill then 'NO_FLOW_REQUIRED_SKILL'
         when not has_approved_agent_assignment then 'NO_FLOW_AGENT_ASSIGNMENT'
         when not has_dispatchable_agent then 'NO_AGENT_SKILL_GRANT_OR_READY_AGENT'
         else 'FLOW_RULE_DISPATCHABLE'
       end as p6_status
from chain
order by source_system, event_type;

\echo '== P6 positive task evidence =='
select t.task_id, t.source_event_id, t.object_type, t.event_type, t.error_code,
       t.routing_path, t.matched_flow_id, t.matched_rule_id, t.requested_skill,
       ta.agent_id as selected_agent_id,
       ta.assignment_id,
       dr.dispatch_request_id,
       dr.status as dispatch_request_status,
       t.created_at
from tasks t
left join lateral (
  select * from task_assignments ta
  where ta.task_id = t.task_id
  order by ta.created_at desc
  limit 1
) ta on true
left join lateral (
  select * from dispatch_requests dr
  where dr.task_id = t.task_id
  order by dr.created_at desc
  limit 1
) dr on true
where t.tenant_id = :'tenant_id'
  and t.created_at > now() - interval '2 days'
  and coalesce(t.routing_path, '') = 'FLOW_RULE'
order by t.created_at desc;

\echo '== P6 defects: should be zero for positive cases =='
with p6_tasks as (
  select t.task_id, t.routing_path, t.matched_flow_id, t.matched_rule_id, t.requested_skill,
         ta.agent_id, dr.dispatch_request_id
  from tasks t
  left join lateral (
    select * from task_assignments ta
    where ta.task_id = t.task_id
    order by ta.created_at desc
    limit 1
  ) ta on true
  left join lateral (
    select * from dispatch_requests dr
    where dr.task_id = t.task_id
    order by dr.created_at desc
    limit 1
  ) dr on true
  where t.tenant_id = :'tenant_id'
    and t.created_at > now() - interval '2 days'
    and t.event_type in ('VENDOR_MASTER_BANK_ACCOUNT_CHANGED','PAYMENT_BLOCKED_BY_RISK_RULE','EQUIPMENT_ALARM','CONTENT_PUBLISH_FAILED')
)
select task_id,
       case
         when routing_path <> 'FLOW_RULE' then 'NOT_FLOW_RULE'
         when matched_flow_id is null then 'NO_MATCHED_FLOW_ID'
         when matched_rule_id is null then 'NO_MATCHED_RULE_ID'
         when requested_skill is null then 'NO_REQUESTED_SKILL'
         when agent_id is null then 'NO_SELECTED_AGENT'
         when dispatch_request_id is null then 'NO_DISPATCH_REQUEST'
         else 'PASS'
       end as defect
from p6_tasks
where routing_path <> 'FLOW_RULE'
   or matched_flow_id is null
   or matched_rule_id is null
   or requested_skill is null
   or agent_id is null
   or dispatch_request_id is null
order by task_id;
