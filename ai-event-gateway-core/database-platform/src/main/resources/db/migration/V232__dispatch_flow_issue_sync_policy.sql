-- V38-7B1-HF2: make external Issue creation an explicit Source Flow / Rule authority.
-- Existing flows preserve prior behavior (OPTIONAL = failed Task requires Issue; successful Task does not).

alter table dispatch_flows
  add column if not exists issue_sync_policy varchar(32) not null default 'OPTIONAL';

alter table dispatch_flows
  drop constraint if exists ck_dispatch_flows_issue_sync_policy;
alter table dispatch_flows
  add constraint ck_dispatch_flows_issue_sync_policy
  check (issue_sync_policy in ('NONE','OPTIONAL','REQUIRED','MANUAL'));

alter table dispatch_policies
  add column if not exists issue_sync_policy varchar(32);

alter table dispatch_policies
  drop constraint if exists ck_dispatch_policies_issue_sync_policy;
alter table dispatch_policies
  add constraint ck_dispatch_policies_issue_sync_policy
  check (issue_sync_policy is null or issue_sync_policy in ('NONE','OPTIONAL','REQUIRED','MANUAL'));

-- One-time compatibility projection for deployments that previously stored simple behavior tokens
-- in issue_policy_id. Arbitrary external policy identifiers remain untouched and inherit Flow policy.
update dispatch_policies
   set issue_sync_policy = case upper(btrim(issue_policy_id))
       when 'NONE' then 'NONE'
       when 'NEVER' then 'NONE'
       when 'OPTIONAL' then 'OPTIONAL'
       when 'ON_FAILURE' then 'OPTIONAL'
       when 'FAILURE_ONLY' then 'OPTIONAL'
       when 'REQUIRED' then 'REQUIRED'
       when 'ALWAYS' then 'REQUIRED'
       when 'MANUAL' then 'MANUAL'
       else issue_sync_policy
   end
 where issue_sync_policy is null
   and issue_policy_id is not null;

comment on column dispatch_flows.issue_sync_policy is
  'Canonical default external Issue behavior for Tasks created by this Source Flow: NONE, OPTIONAL(failure only), REQUIRED(always), or MANUAL.';
comment on column dispatch_policies.issue_sync_policy is
  'Optional Flow Rule override of dispatch_flows.issue_sync_policy. NULL inherits the parent Source Flow policy.';

create or replace view dispatch_flow_rules as
select
  tenant_id,
  policy_id as rule_id,
  policy_code as rule_code,
  policy_name as rule_name,
  flow_id,
  rule_scope,
  event_stage,
  source_system,
  origin_source_system,
  target_system,
  object_type,
  event_type,
  error_code,
  condition_json,
  priority,
  match_mode,
  target_pool_id,
  target_pool_code,
  requested_skill,
  capability_requirement_mode,
  candidate_pool_mode,
  routing_strategy,
  status,
  metadata_json,
  created_at,
  updated_at,
  issue_sync_policy
from dispatch_policies;
