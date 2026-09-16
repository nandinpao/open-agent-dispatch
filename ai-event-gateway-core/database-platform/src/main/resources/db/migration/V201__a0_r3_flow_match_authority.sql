-- A0-R3 — Flow Match Authority
-- Contract: MRS v5.2.1 A0-C frozen contract §§10A, 10B, 10C, 33, 51, 134.4, 154(57-60).
--
-- A0-R3 makes deterministic Flow Rule evaluation authoritative while explicitly NOT
-- changing Capability Routing, Binding, Pool, Agent, Session, or ExecutionAssignment authority.
-- Canonical result algorithm:
--   1. Evaluate ACTIVE rules deterministically against registered/indexable attributes.
--   2. Collect all exact/wildcard matches.
--   3. Find the minimum priority among matches.
--   4. 0 matches => NO_MATCH; 1 rule in minimum-priority bucket => MATCHED;
--      >1 rules in that bucket => AMBIGUOUS. No updated_at/rule_id winner tie-break is allowed.
-- Source default pools remain compatibility configuration only and MUST NOT be represented
-- as a MATCHED Flow Rule decision.

-- -----------------------------------------------------------------------------
-- 1. Physical schema reality: dispatch_policies remains the Flow Rule table.
--    Add the frozen business output without conflating rule_code with serviceCode.
-- -----------------------------------------------------------------------------
alter table dispatch_policies add column if not exists service_code varchar(160);

create index if not exists idx_a0r3_dispatch_policies_match_priority
  on dispatch_policies(tenant_id,source_system,event_stage,status,priority,flow_id,policy_id);

comment on column dispatch_policies.service_code is
  'A0-R3 business problem/service classification emitted by a deterministic Flow Rule. policy_code/rule_code is not a Service Code.';

-- -----------------------------------------------------------------------------
-- 2. Typed/indexable Flow Match Attribute Registry.
--    Core physical columns are the initial authoritative set. Arbitrary condition_json
--    is NOT authoritative matching input unless normalized through a registered attribute.
-- -----------------------------------------------------------------------------
create table if not exists flow_match_attribute_registry (
  attribute_code varchar(96) primary key,
  data_type varchar(24) not null,
  allowed_operators_json jsonb not null default '["EQ","IN","EXISTS"]'::jsonb,
  source_field varchar(96) not null,
  index_strategy varchar(32) not null,
  authoritative boolean not null default true,
  status varchar(24) not null default 'ACTIVE',
  version int not null default 1,
  updated_at timestamptz not null default now(),
  constraint chk_a0r3_match_attribute_type check(data_type in ('STRING','INTEGER','DECIMAL','BOOLEAN','TIMESTAMP')),
  constraint chk_a0r3_match_attribute_index check(index_strategy in ('BTREE','HASH','EXPRESSION','NONE')),
  constraint chk_a0r3_match_attribute_status check(status in ('ACTIVE','DISABLED','RETIRED')),
  constraint chk_a0r3_match_attribute_ops_array check(jsonb_typeof(allowed_operators_json)='array')
);

insert into flow_match_attribute_registry(attribute_code,data_type,allowed_operators_json,source_field,index_strategy,authoritative,status,version) values
('SOURCE_SYSTEM','STRING','["EQ","IN","EXISTS"]','source_system','BTREE',true,'ACTIVE',1),
('ORIGIN_SOURCE_SYSTEM','STRING','["EQ","IN","EXISTS"]','origin_source_system','BTREE',true,'ACTIVE',1),
('TARGET_SYSTEM','STRING','["EQ","IN","EXISTS"]','target_system','BTREE',true,'ACTIVE',1),
('EVENT_STAGE','STRING','["EQ","IN","EXISTS"]','event_stage','BTREE',true,'ACTIVE',1),
('EVENT_TYPE','STRING','["EQ","IN","EXISTS"]','event_type','BTREE',true,'ACTIVE',1),
('OBJECT_TYPE','STRING','["EQ","IN","EXISTS"]','object_type','BTREE',true,'ACTIVE',1),
('ERROR_CODE','STRING','["EQ","IN","EXISTS"]','error_code','BTREE',true,'ACTIVE',1),
('SEVERITY','STRING','["EQ","IN","EXISTS"]','severity','BTREE',true,'ACTIVE',1)
on conflict(attribute_code) do nothing;

-- Future tenant-defined criteria are normalized here; V201 does not allow raw condition_json
-- to silently become runtime authority.
create table if not exists flow_rule_attribute_criteria (
  tenant_id varchar(64) not null,
  flow_id varchar(128) not null,
  rule_id varchar(128) not null,
  criterion_id varchar(160) not null,
  attribute_code varchar(96) not null,
  operator varchar(24) not null,
  typed_value_json jsonb,
  required boolean not null default true,
  status varchar(24) not null default 'ACTIVE',
  version int not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key(tenant_id,criterion_id),
  constraint fk_a0r3_rule_attr_flow foreign key(tenant_id,flow_id) references dispatch_flows(tenant_id,flow_id) on delete cascade,
  constraint fk_a0r3_rule_attr_definition foreign key(attribute_code) references flow_match_attribute_registry(attribute_code) on delete restrict,
  constraint chk_a0r3_rule_attr_operator check(operator in ('EQ','IN','GT','GTE','LT','LTE','EXISTS')),
  constraint chk_a0r3_rule_attr_status check(status in ('ACTIVE','DISABLED','RETIRED'))
);
create index if not exists idx_a0r3_rule_attr_rule on flow_rule_attribute_criteria(tenant_id,flow_id,rule_id,status,attribute_code);

create or replace function validate_a0r3_registered_rule_criterion()
returns trigger language plpgsql as $$
declare attr record;
begin
  if not exists(select 1 from dispatch_policies p where p.policy_id=new.rule_id and p.tenant_id=new.tenant_id and p.flow_id=new.flow_id) then
    raise exception 'A0_R3_RULE_CRITERION_RULE_SCOPE_MISMATCH';
  end if;
  select * into attr from flow_match_attribute_registry where attribute_code=new.attribute_code;
  if attr.attribute_code is null then raise exception 'A0_R3_MATCH_ATTRIBUTE_NOT_REGISTERED'; end if;
  if new.status='ACTIVE' and (attr.status<>'ACTIVE' or attr.authoritative<>true or attr.index_strategy='NONE') then
    raise exception 'A0_R3_MATCH_ATTRIBUTE_NOT_RUNTIME_AUTHORITATIVE';
  end if;
  if new.status='ACTIVE' and not (attr.allowed_operators_json ? new.operator) then
    raise exception 'A0_R3_MATCH_ATTRIBUTE_OPERATOR_NOT_ALLOWED';
  end if;
  return new;
end $$;
drop trigger if exists trg_a0r3_validate_registered_rule_criterion on flow_rule_attribute_criteria;
create trigger trg_a0r3_validate_registered_rule_criterion
before insert or update on flow_rule_attribute_criteria
for each row execute function validate_a0r3_registered_rule_criterion();

alter table flow_rule_attribute_criteria enable row level security;
drop policy if exists tenant_isolation on flow_rule_attribute_criteria;
create policy tenant_isolation on flow_rule_attribute_criteria
  using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());

-- -----------------------------------------------------------------------------
-- 3. Externalized full evaluation set. The hot decision stores only summary refs.
-- -----------------------------------------------------------------------------
create table if not exists flow_evaluation_sets (
  tenant_id varchar(64) not null,
  evaluation_set_id varchar(160) not null,
  task_id varchar(128) not null,
  flow_id varchar(128),
  flow_version varchar(64),
  input_snapshot_json jsonb not null default '{}'::jsonb,
  evaluated_rules_json jsonb not null default '[]'::jsonb,
  closest_rules_json jsonb not null default '[]'::jsonb,
  evaluated_rule_count int not null,
  match_result varchar(24) not null,
  minimum_matched_priority int,
  evaluation_digest varchar(128) not null,
  evaluator_version varchar(80) not null,
  created_at timestamptz not null default now(),
  primary key(tenant_id,evaluation_set_id),
  constraint fk_a0r3_eval_task foreign key(tenant_id,task_id) references tasks(tenant_id,task_id) on delete cascade,
  constraint chk_a0r3_eval_result check(match_result in ('MATCHED','NO_MATCH','AMBIGUOUS')),
  constraint chk_a0r3_eval_input_object check(jsonb_typeof(input_snapshot_json)='object'),
  constraint chk_a0r3_eval_rules_array check(jsonb_typeof(evaluated_rules_json)='array'),
  constraint chk_a0r3_eval_closest_array check(jsonb_typeof(closest_rules_json)='array'),
  constraint chk_a0r3_eval_count check(evaluated_rule_count>=0)
);
create index if not exists idx_a0r3_eval_task on flow_evaluation_sets(tenant_id,task_id,created_at desc);
create index if not exists idx_a0r3_eval_flow on flow_evaluation_sets(tenant_id,flow_id,match_result,created_at desc);
alter table flow_evaluation_sets enable row level security;
drop policy if exists tenant_isolation on flow_evaluation_sets;
create policy tenant_isolation on flow_evaluation_sets
  using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());

create or replace function prevent_a0r3_evidence_mutation()
returns trigger language plpgsql as $$
begin raise exception 'A0_R3_FLOW_MATCH_EVIDENCE_IS_APPEND_ONLY'; end $$;
drop trigger if exists trg_a0r3_flow_evaluation_set_immutable on flow_evaluation_sets;
create trigger trg_a0r3_flow_evaluation_set_immutable
before update or delete on flow_evaluation_sets
for each row execute function prevent_a0r3_evidence_mutation();

-- -----------------------------------------------------------------------------
-- 4. Promote flow_match_decisions from V188 projection target to A0-R3 authority.
-- -----------------------------------------------------------------------------
alter table flow_match_decisions add column if not exists flow_evaluation_set_ref varchar(160);
alter table flow_match_decisions add column if not exists flow_evaluation_set_digest varchar(128);
alter table flow_match_decisions add column if not exists closest_rule_id varchar(128);
alter table flow_match_decisions add column if not exists closest_rule_priority int;
alter table flow_match_decisions add column if not exists closest_rule_failed_criterion varchar(96);
alter table flow_match_decisions add column if not exists closest_rule_match_ratio numeric(8,6);
alter table flow_match_decisions add column if not exists decision_authority_version varchar(64);

create unique index if not exists uq_a0r3_authoritative_flow_match_per_task
  on flow_match_decisions(tenant_id,task_id)
  where decision_source='A0_R3_FLOW_MATCH_AUTHORITY';

create index if not exists idx_a0r3_flow_match_result
  on flow_match_decisions(tenant_id,match_result,decided_at desc)
  where decision_source='A0_R3_FLOW_MATCH_AUTHORITY';

-- V188 rows are historical projection evidence and remain immutable history. New authority rows are
-- append-only as well; retries use ON CONFLICT DO NOTHING against the per-task authority index.
drop trigger if exists trg_a0r3_flow_match_decision_immutable on flow_match_decisions;
create trigger trg_a0r3_flow_match_decision_immutable
before update or delete on flow_match_decisions
for each row execute function prevent_a0r3_evidence_mutation();

comment on table flow_match_decisions is
  'A0-R3 authoritative deterministic Flow Match decision ledger. V188 LEGACY_TASK_PROJECTION rows are historical only; A0_R3_FLOW_MATCH_AUTHORITY is fixed once per Task.';

-- -----------------------------------------------------------------------------
-- 5. Decision-to-Task state bridge. This is Flow Match authority only.
--    It does NOT select Capability Provider, Binding, Pool, Agent, or Assignment.
-- -----------------------------------------------------------------------------
insert into task_condition_resolution_policies(scope_type,scope_id,condition_type,blocking,resolution,timeout_seconds,timeout_action,timeout_terminalization_reason,status,version)
values('SYSTEM','*','TRIAGE_REQUIRED',true,'WAIT',null,'NONE',null,'ACTIVE',1)
on conflict(scope_type,scope_id,condition_type) do nothing;

create or replace function a0r3_apply_authoritative_flow_match()
returns trigger language plpgsql as $$
begin
  if new.decision_source <> 'A0_R3_FLOW_MATCH_AUTHORITY' then return new; end if;

  if new.match_result='MATCHED' then
    update tasks
       set task_phase='PLANNING',
           matched_flow_id=new.flow_id,
           matched_rule_id=new.matched_rule_id,
           task_type_code=coalesce(new.output_service_code,task_type_code),
           required_capabilities_json=case
             when jsonb_array_length(new.output_capability_requirements_json)>0 then new.output_capability_requirements_json
             else required_capabilities_json end,
           routing_policy='FLOW_RULE',
           routing_path='FLOW_RULE',
           updated_at=now()
     where tenant_id=new.tenant_id and task_id=new.task_id and task_lifecycle<>'CLOSED';
    update task_conditions set condition_state='RESOLVED',resolved_by='A0_R3_FLOW_MATCH_AUTHORITY',resolved_at=now(),resolution_note='Deterministic Flow Rule matched',version=version+1
     where tenant_id=new.tenant_id and task_id=new.task_id and condition_type in ('TRIAGE_REQUIRED','FLOW_CONFIGURATION_AMBIGUOUS') and condition_state='ACTIVE';

  elsif new.match_result='NO_MATCH' then
    update tasks set task_phase='TRIAGE',task_lifecycle='WAITING',routing_policy='FLOW_RULE',routing_path='FLOW_RULE_NO_MATCH_TRIAGE',updated_at=now()
     where tenant_id=new.tenant_id and task_id=new.task_id and task_lifecycle not in ('FINALIZING','CLOSED');
    insert into task_conditions(tenant_id,task_id,condition_type,condition_state,blocking,resolution,timeout_at,timeout_action,timeout_terminalization_reason,reason_code,reason,source_ref,raised_by,raised_at,version)
    values(new.tenant_id,new.task_id,'TRIAGE_REQUIRED','ACTIVE',true,'WAIT',null,'NONE',null,'FLOW_RULE_NO_MATCH','No deterministic Flow Rule matched; semantic triage is required.',new.decision_id,'A0_R3_FLOW_MATCH_AUTHORITY',now(),1)
    on conflict(tenant_id,task_id,condition_type) do update set condition_state='ACTIVE',blocking=true,resolution='WAIT',timeout_at=null,timeout_action='NONE',timeout_terminalization_reason=null,reason_code='FLOW_RULE_NO_MATCH',reason=excluded.reason,source_ref=excluded.source_ref,raised_by=excluded.raised_by,raised_at=excluded.raised_at,resolved_by=null,resolved_at=null,resolution_note=null,version=task_conditions.version+1;

  elsif new.match_result='AMBIGUOUS' then
    update tasks set task_phase='FLOW_MATCH',task_lifecycle='WAITING',routing_policy='FLOW_RULE',routing_path='FLOW_RULE_AMBIGUOUS',updated_at=now()
     where tenant_id=new.tenant_id and task_id=new.task_id and task_lifecycle not in ('FINALIZING','CLOSED');
    insert into task_conditions(tenant_id,task_id,condition_type,condition_state,blocking,resolution,timeout_at,timeout_action,timeout_terminalization_reason,reason_code,reason,source_ref,raised_by,raised_at,version)
    values(new.tenant_id,new.task_id,'FLOW_CONFIGURATION_AMBIGUOUS','ACTIVE',true,'REQUIRE_HUMAN',now()+interval '1 hour','FAIL','FLOW_CONFIGURATION_ERROR','FLOW_RULE_SAME_PRIORITY_AMBIGUOUS','Multiple matching Flow Rules share the authoritative minimum priority; no rule was selected.',new.decision_id,'A0_R3_FLOW_MATCH_AUTHORITY',now(),1)
    on conflict(tenant_id,task_id,condition_type) do update set condition_state='ACTIVE',blocking=true,resolution='REQUIRE_HUMAN',timeout_at=excluded.timeout_at,timeout_action='FAIL',timeout_terminalization_reason='FLOW_CONFIGURATION_ERROR',reason_code='FLOW_RULE_SAME_PRIORITY_AMBIGUOUS',reason=excluded.reason,source_ref=excluded.source_ref,raised_by=excluded.raised_by,raised_at=excluded.raised_at,resolved_by=null,resolved_at=null,resolution_note=null,version=task_conditions.version+1;
  end if;
  return new;
end $$;

drop trigger if exists trg_a0r3_apply_authoritative_flow_match on flow_match_decisions;
create trigger trg_a0r3_apply_authoritative_flow_match
after insert on flow_match_decisions
for each row execute function a0r3_apply_authoritative_flow_match();

-- -----------------------------------------------------------------------------
-- 6. Design-time deterministic conflict projection. Runtime ambiguity remains fail closed.
-- -----------------------------------------------------------------------------
create or replace view flow_rule_conflicts_v201 as
select a.tenant_id,a.flow_id as flow_id,a.flow_id as left_flow_id,b.flow_id as right_flow_id,
       a.policy_id as left_rule_id,b.policy_id as right_rule_id,a.priority,
       'SAME_PRIORITY_OVERLAPPING_DETERMINISTIC_CRITERIA'::varchar(64) as conflict_type,
       jsonb_build_object(
         'leftFlowId',a.flow_id,'rightFlowId',b.flow_id,
         'sourceSystemLeft',coalesce(a.source_system,'*'),'sourceSystemRight',coalesce(b.source_system,'*'),
         'originSourceSystemLeft',coalesce(a.origin_source_system,'*'),'originSourceSystemRight',coalesce(b.origin_source_system,'*'),
         'eventStageLeft',coalesce(a.event_stage,'*'),'eventStageRight',coalesce(b.event_stage,'*'),
         'eventTypeLeft',coalesce(a.event_type,'*'),'eventTypeRight',coalesce(b.event_type,'*'),
         'objectTypeLeft',coalesce(a.object_type,'*'),'objectTypeRight',coalesce(b.object_type,'*'),
         'errorCodeLeft',coalesce(a.error_code,'*'),'errorCodeRight',coalesce(b.error_code,'*'),
         'targetSystemLeft',coalesce(a.target_system,'*'),'targetSystemRight',coalesce(b.target_system,'*')) as evidence
  from dispatch_policies a
  join dispatch_policies b
    on b.tenant_id=a.tenant_id and b.policy_id>a.policy_id
   and b.priority=a.priority
   and upper(coalesce(b.status,'DRAFT')) in ('ACTIVE','ENABLED')
 where upper(coalesce(a.status,'DRAFT')) in ('ACTIVE','ENABLED')
   and (upper(coalesce(a.source_system,'*'))='*' or upper(coalesce(b.source_system,'*'))='*' or upper(a.source_system)=upper(b.source_system))
   and (upper(coalesce(a.origin_source_system,'*'))='*' or upper(coalesce(b.origin_source_system,'*'))='*' or upper(a.origin_source_system)=upper(b.origin_source_system))
   and (upper(coalesce(a.event_stage,'*'))='*' or upper(coalesce(b.event_stage,'*'))='*' or upper(a.event_stage)=upper(b.event_stage))
   and (upper(coalesce(a.event_type,'*'))='*' or upper(coalesce(b.event_type,'*'))='*' or upper(a.event_type)=upper(b.event_type))
   and (upper(coalesce(a.object_type,'*'))='*' or upper(coalesce(b.object_type,'*'))='*' or upper(a.object_type)=upper(b.object_type))
   and (upper(coalesce(a.error_code,'*'))='*' or upper(coalesce(b.error_code,'*'))='*' or upper(a.error_code)=upper(b.error_code))
   and (upper(coalesce(a.target_system,'*'))='*' or upper(coalesce(b.target_system,'*'))='*' or upper(a.target_system)=upper(b.target_system))
   -- If both rules constrain the same registered attribute with required EQ values that are
   -- demonstrably different, those criteria are disjoint and the pair is not an overlap.
   and not exists (
       select 1
         from flow_rule_attribute_criteria ca
         join flow_rule_attribute_criteria cb
           on cb.tenant_id=ca.tenant_id and cb.attribute_code=ca.attribute_code
          and cb.rule_id=b.policy_id and cb.status='ACTIVE' and cb.required=true and cb.operator='EQ'
        where ca.tenant_id=a.tenant_id and ca.rule_id=a.policy_id and ca.status='ACTIVE' and ca.required=true and ca.operator='EQ'
          and ca.typed_value_json is distinct from cb.typed_value_json
   );

comment on view flow_rule_conflicts_v201 is
  'A0-R3 pre-publish conflict diagnostics across applicable Flows for same-priority overlapping deterministic criteria. Required registered EQ criteria with distinct values are treated as disjoint. Runtime actual-input evaluation remains final authority.';

insert into schema_contract_authority(contract_id,contract_family,authority_note,created_at,schema_version)
values('a0-r3-flow-match-authority-v1','A0_R3_FLOW_MATCH_AUTHORITY',
       'AUTHORITATIVE: deterministic Flow Rules only; minimum-priority bucket semantics; zero=NO_MATCH; one=MATCHED; many=AMBIGUOUS; source default is not a Flow Rule; no Capability/Binding/Agent/Assignment authority change.',
       now(),'V201')
on conflict(contract_id) do nothing;

-- -----------------------------------------------------------------------------
-- Flyway INSTANCE governance context for FORCE-RLS IAM/catalog DML below.
-- set_config(..., true) is transaction-local; earlier tenant-data backfills remain context-neutral.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','a0-r3-flow-match-migration',true);

-- 7. Human Admin fail-closed route inventory for A0-R3 operator evidence.
-- -----------------------------------------------------------------------------
insert into permission_entry_point_inventory(
 entry_point_id,entry_point_type,application_id,owner_module,display_name,route_pattern,http_method,authority_state,
 target_permission_code,legacy_authority_type,legacy_authorities,resource_type,resource_resolver_id,exemption_reason,
 migration_deadline,manifest_revision,source_ref,source_hash,last_verified_at,created_by,updated_by)
values
('REST:GET:/admin/tasks/{taskId}/flow-match-authority','REST','control-plane-app','control-plane-app','TaskFlowMatchAuthorityController.view','/admin/tasks/{taskId}/flow-match-authority','GET','TARGET_ONLY','task.read',null,'[]'::jsonb,'TASK','R3_PATH_RESOURCE_RESOLVER',null,null,'a0-r3-flow-match-authority-2026-08-24','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/TaskFlowMatchAuthorityController.java#view','1e035ce5f735fb4a84a5cdf66b0f9413fbaac8258567912a44c70b37da9d8ede',now(),'a0-r3-flow-match-authority','a0-r3-flow-match-authority'),
('REST:GET:/admin/dispatch-flows/{flowId}/rule-conflicts','REST','control-plane-app','control-plane-app','DispatchFlowController.ruleConflicts','/admin/dispatch-flows/{flowId}/rule-conflicts','GET','TARGET_ONLY','admin.dispatch.flow.rules',null,'[]'::jsonb,'DISPATCH_FLOW','R3_PATH_RESOURCE_RESOLVER',null,null,'a0-r3-flow-match-authority-2026-08-24','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchFlowController.java#ruleConflicts','23db195b40a45f91c342f99f305291e2f86cd9d8e712a47492f74f7d8fab29a9',now(),'a0-r3-flow-match-authority','a0-r3-flow-match-authority')
on conflict(entry_point_id) do update set
 display_name=excluded.display_name,route_pattern=excluded.route_pattern,http_method=excluded.http_method,
 authority_state=excluded.authority_state,target_permission_code=excluded.target_permission_code,legacy_authority_type=null,
 legacy_authorities='[]'::jsonb,resource_type=excluded.resource_type,resource_resolver_id=excluded.resource_resolver_id,
 exemption_reason=null,migration_deadline=null,manifest_revision=excluded.manifest_revision,source_ref=excluded.source_ref,
 source_hash=excluded.source_hash,last_verified_at=now(),updated_at=now(),updated_by='a0-r3-flow-match-authority',
 version=permission_entry_point_inventory.version+1;

update rbac_policy_versions set policy_version=policy_version+1,updated_at=now(),updated_by='a0-r3-flow-match-authority';
