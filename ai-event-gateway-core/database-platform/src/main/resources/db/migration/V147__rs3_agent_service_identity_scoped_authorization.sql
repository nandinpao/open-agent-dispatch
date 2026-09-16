-- RS3: Agents + Service Identity scoped authorization.
-- Agent is both a governed business resource and the source of an AGENT_SERVICE runtime principal projection.
-- Organization membership never grants Agent visibility; IAM Responsibility + binding scope remains authority.

select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','rs3-agent-service-scope',true);

create index if not exists idx_agent_profiles_scope_department
  on agent_profiles(tenant_id,owner_department_id,approval_status,updated_at desc);
create index if not exists idx_agent_profiles_scope_group
  on agent_profiles(tenant_id,owner_group_id,approval_status,updated_at desc);

-- Agent ownership is business-resource ownership. Department / Group retirement must re-scope active Agents first.
create or replace function p23b_guard_department_resource_scope_status()
returns trigger language plpgsql security invoker as $$
begin
  if new.status in ('DISABLED','DELETED') and old.status is distinct from new.status then
    if exists (select 1 from source_systems s where s.tenant_id=new.tenant_id and s.owner_department_id=new.department_id and s.status<>'RETIRED')
       or exists (select 1 from dispatch_flows f where f.tenant_id=new.tenant_id and f.owner_department_id=new.department_id and f.status<>'RETIRED')
       or exists (select 1 from agent_pools p where p.tenant_id=new.tenant_id and p.owner_department_id=new.department_id and p.status<>'RETIRED')
       or exists (select 1 from agent_profiles a where a.tenant_id=new.tenant_id and a.owner_department_id=new.department_id
                    and a.owner_department_id<>'UNASSIGNED' and a.approval_status not in ('REVOKED','REJECTED')) then
      if new.status='DELETED' then
        raise exception 'DEPARTMENT_DELETE_BLOCKED: re-scope active Source Systems, Dispatch Flows, Agent Pools, and Agents first' using errcode='23514';
      else
        raise exception 'DEPARTMENT_DISABLE_BLOCKED_BY_RESOURCES' using errcode='23514';
      end if;
    end if;
    if exists (select 1 from a2a_policies a where a.tenant_id=new.tenant_id and (a.source_department_id=new.department_id or a.target_department_id=new.department_id)) then
      if new.status='DELETED' then raise exception 'DEPARTMENT_DELETE_BLOCKED: re-scope A2A Policies first' using errcode='23514';
      else raise exception 'DEPARTMENT_DISABLE_BLOCKED_BY_A2A_POLICIES' using errcode='23514'; end if;
    end if;
  end if;
  return new;
end $$;

create or replace function p23b_guard_group_resource_scope_status()
returns trigger language plpgsql security invoker as $$
begin
  if new.status in ('DISABLED','DELETED') and old.status is distinct from new.status then
    if exists (select 1 from source_systems s where s.tenant_id=new.tenant_id and s.owner_group_id=new.group_id and s.status<>'RETIRED')
       or exists (select 1 from dispatch_flows f where f.tenant_id=new.tenant_id and f.owner_group_id=new.group_id and f.status<>'RETIRED')
       or exists (select 1 from agent_pools p where p.tenant_id=new.tenant_id and p.owner_group_id=new.group_id and p.status<>'RETIRED')
       or exists (select 1 from agent_profiles a where a.tenant_id=new.tenant_id and a.owner_group_id=new.group_id
                    and a.approval_status not in ('REVOKED','REJECTED')) then
      if new.status='DELETED' then
        raise exception 'GROUP_DELETE_BLOCKED: re-scope active Source Systems, Dispatch Flows, Agent Pools, and Agents first' using errcode='23514';
      else
        raise exception 'GROUP_DISABLE_BLOCKED_BY_RESOURCES' using errcode='23514';
      end if;
    end if;
    if exists (select 1 from a2a_policies a where a.tenant_id=new.tenant_id and (a.source_group_id=new.group_id or a.target_group_id=new.group_id)) then
      if new.status='DELETED' then raise exception 'GROUP_DELETE_BLOCKED: re-scope A2A Policies first' using errcode='23514';
      else raise exception 'GROUP_DISABLE_BLOCKED_BY_A2A_POLICIES' using errcode='23514'; end if;
    end if;
  end if;
  return new;
end $$;

-- Ownership mutation invalidates AGENT, service-scope and credential-subresource decisions together.
create or replace function rs3_touch_agent_scope_epoch()
returns trigger language plpgsql as $$
declare actor varchar;
begin
  if old.owner_department_id is not distinct from new.owner_department_id
     and old.owner_group_id is not distinct from new.owner_group_id
     and old.tenant_id is not distinct from new.tenant_id then return new; end if;
  if old.tenant_id is distinct from new.tenant_id then
    raise exception 'RS3_AGENT_TENANT_IMMUTABLE: create a new Agent identity in the target Tenant' using errcode='23514';
  end if;
  actor=coalesce(nullif(current_setting('app.current_actor_id',true),''),'rs3-agent-owner-change');
  perform p4ra_advance_policy_revision(new.tenant_id,actor);
  insert into resource_security_epochs(tenant_id,resource_type,resource_id,resource_security_epoch,updated_at,updated_by)
  select new.tenant_id,t,new.agent_id,1,now(),actor from unnest(array['AGENT','AGENT_SERVICE_SCOPE','AGENT_CREDENTIAL_METADATA']::varchar[]) t
  on conflict(tenant_id,resource_type,resource_id) do update
    set resource_security_epoch=resource_security_epochs.resource_security_epoch+1,updated_at=now(),updated_by=actor;
  return new;
end $$;

drop trigger if exists trg_rs3_agent_scope_epoch on agent_profiles;
create trigger trg_rs3_agent_scope_epoch
after update of tenant_id,owner_department_id,owner_group_id on agent_profiles
for each row execute function rs3_touch_agent_scope_epoch();

insert into resource_catalog(resource_type,category,descriptor_authority,ownership_supported,participants_supported,
  field_visibility_supported,runtime_lease_supported,default_sensitivity,description)
values
 ('AGENT','AGENT','AGENT_CONTROL',true,false,true,false,'RESTRICTED','Governed Agent profile with Department/Group ownership.'),
 ('AGENT_SERVICE_SCOPE','AGENT','AGENT_CONTROL',true,false,true,false,'RESTRICTED','Runtime Agent service-principal scope resolved from the governed Agent profile.'),
 ('AGENT_CREDENTIAL_METADATA','SECURITY','AGENT_CONTROL',true,false,true,false,'SECRET','Agent credential metadata; never implied by ordinary Agent read access.')
on conflict(resource_type) do update set category=excluded.category,descriptor_authority=excluded.descriptor_authority,
 ownership_supported=excluded.ownership_supported,participants_supported=excluded.participants_supported,
 field_visibility_supported=excluded.field_visibility_supported,runtime_lease_supported=excluded.runtime_lease_supported,
 default_sensitivity=excluded.default_sensitivity,description=excluded.description,status='ACTIVE',
 catalog_version=resource_catalog.catalog_version+1,updated_at=now();

-- Permission Catalog revision 020: only Agent-bound operations gain Department/Group scope.
-- Dispatch policy and fleet/global Agent operations remain Tenant-only until their dedicated phases.
insert into permission_catalog_revisions(
 revision_id,revision_code,revision_number,status,content_hash,description,supersedes_revision_id,
 created_at,created_by,published_at,published_by,version)
select '00000000-0000-0000-0000-000000000020'::uuid,'RS3-AGENT-SERVICE-SCOPE-0.8.2',coalesce(max(revision_number),0)+1,
 'DRAFT','DRAFT:UNPUBLISHED','RS3 Agent resource and service-principal organizational authorization.',
 (select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE'),
 now(),'rs3-agent-service-scope',null,null,1 from permission_catalog_revisions
on conflict(revision_id) do nothing;

insert into permission_catalog_revision_entries(
 revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,lifecycle,
 allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by,version)
select '00000000-0000-0000-0000-000000000020'::uuid,e.permission_code,e.owner_module,e.resource_type,e.action_code,e.description,
 e.risk_level,e.risk_lane,e.lifecycle,e.allowed_scope_types,e.system_managed,e.replacement_permission_code,e.introduced_at,e.deprecated_at,
 e.retired_at,now(),'rs3-agent-service-scope',1
from permission_catalog_revision_entries e join permission_catalog_active_revision a on a.singleton_id='ACTIVE' and a.revision_id=e.revision_id
on conflict(revision_id,permission_code) do nothing;

insert into permission_catalog_revision_aliases(
 revision_id,alias_code,canonical_permission_code,alias_type,valid_from,valid_until,reason,created_by,created_at,version)
select '00000000-0000-0000-0000-000000000020'::uuid,a.alias_code,a.canonical_permission_code,a.alias_type,a.valid_from,a.valid_until,a.reason,
 'rs3-agent-service-scope',now(),1
from permission_catalog_revision_aliases a join permission_catalog_active_revision active on active.singleton_id='ACTIVE' and active.revision_id=a.revision_id
on conflict(revision_id,alias_code) do nothing;

update permission_catalog_revision_entries
set allowed_scope_types=array['TENANT','DEPARTMENT','DEPARTMENT_SUBTREE','GROUP']::varchar[],
    updated_at=now(),updated_by='rs3-agent-service-scope',version=version+1
where revision_id='00000000-0000-0000-0000-000000000020'::uuid and permission_code in (
 'admin.agent.governance.search.agents','admin.agent.governance.get.agent','admin.agent.governance.update.agent',
 'admin.agent.governance.approve.agent','admin.agent.governance.approve.enrollment',
 'admin.agent.governance.enable.agent','admin.agent.governance.disable.agent','admin.agent.governance.suspend.agent','admin.agent.governance.revoke.agent',
 'admin.agent.governance.issue.credential','admin.agent.governance.disconnect.agent.runtime','admin.agent.governance.disconnect.all.agent.runtime.sessions',
 'admin.agent.governance.enforce.duplicate.runtime.security','admin.agent.governance.resolve.duplicate.runtime.security',
 'admin.agent.governance.get.agent.security.enforcement.policy','admin.agent.governance.update.agent.security.enforcement.policy',
 'admin.agent.governance.search.security.events','admin.agent.governance.latest.auth.failure','admin.agent.governance.connection.repair.actions',
 'admin.agent.governance.execute.connection.repair.action','admin.agent.setup.get.setup.readiness','admin.agent.setup.get.operational.view','admin.agent.setup.setup.agent',
 'admin.agent.assignment.agent.quality.daily','admin.agent.assignment.agent.quality.windows','admin.agent.assignment.upsert.agent.quality.window',
 'admin.agent.assignment.agent.capabilities','admin.agent.assignment.request.agent.capability','admin.agent.assignment.approve.agent.capability',
 'admin.agent.assignment.suspend.agent.capability','admin.agent.assignment.resume.agent.capability','admin.agent.assignment.revoke.agent.capability',
 'admin.agent.assignment.remove.agent.capability','admin.agent.assignment.runtime.bindings','admin.agent.assignment.create.runtime.binding',
 'admin.agent.assignment.upsert.runtime.binding','admin.agent.assignment.transition.runtime.binding','admin.agent.assignment.runtime.feature.observations',
 'admin.agent.assignment.runtime.feature.trusts','admin.agent.assignment.observe.runtime.feature','admin.agent.assignment.verify.runtime.feature',
 'admin.agent.assignment.trust.runtime.feature','admin.agent.assignment.suspend.runtime.feature.trust','admin.agent.assignment.resume.runtime.feature.trust',
 'admin.agent.assignment.revoke.runtime.feature.trust','admin.dispatch.eligibility.agent.dispatch.eligibility',
 'admin.agent.remediation.preview.agent.remediation','admin.agent.remediation.list.agent.remediation.workflows',
 'admin.agent.remediation.get.agent.remediation.workflow','admin.agent.remediation.create.agent.remediation.proposal',
 'admin.agent.remediation.create.agent.remediation.workflow','admin.agent.remediation.approve.agent.remediation.workflow',
 'admin.agent.remediation.reject.agent.remediation.workflow','admin.agent.remediation.cancel.agent.remediation.workflow',
 'admin.agent.remediation.execute.agent.remediation.workflow','admin.agent.skill.registry.detect.agent.skill.drift',
 'admin.agent.skill.registry.propose.agent.skill.remediation','admin.agent.skill.registry.get.approved.skills',
 'admin.agent.skill.registry.replace.approved.skills','admin.agent.skill.registry.sync.approved.skills.and.capabilities','admin.agent.skill.registry.evaluate.agent.skill'
);

select set_config('app.permission_catalog_publish_revision_id','00000000-0000-0000-0000-000000000020',true);

insert into permission_definitions(
 permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,active,version,owner_module,risk_lane,lifecycle,
 catalog_revision_id,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by)
select permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,lifecycle<>'RETIRED',1,owner_module,risk_lane,lifecycle,
 revision_id,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by
from permission_catalog_revision_entries where revision_id='00000000-0000-0000-0000-000000000020'::uuid
on conflict(permission_code) do update set resource_type=excluded.resource_type,action_code=excluded.action_code,description=excluded.description,
 risk_level=excluded.risk_level,allowed_scope_types=excluded.allowed_scope_types,system_managed=excluded.system_managed,active=excluded.active,
 owner_module=excluded.owner_module,risk_lane=excluded.risk_lane,lifecycle=excluded.lifecycle,catalog_revision_id=excluded.catalog_revision_id,
 replacement_permission_code=excluded.replacement_permission_code,deprecated_at=excluded.deprecated_at,retired_at=excluded.retired_at,
 updated_at=excluded.updated_at,updated_by=excluded.updated_by,version=permission_definitions.version+1;

update permission_catalog_revisions set status='SUPERSEDED',version=version+1
where revision_id=(select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE')
 and revision_id<>'00000000-0000-0000-0000-000000000020'::uuid and status='PUBLISHED';

update permission_catalog_revisions set status='PUBLISHED',content_hash=(
 with catalog_lines as (
   select 'P|'||permission_code||'|'||owner_module||'|'||resource_type||'|'||action_code||'|'||description||'|'||risk_level||'|'||risk_lane||'|'||lifecycle||'|'||coalesce(array_to_string(allowed_scope_types,','),'')||'|'||system_managed::text||'|'||coalesce(replacement_permission_code,'') line
   from permission_catalog_revision_entries where revision_id='00000000-0000-0000-0000-000000000020'::uuid
   union all
   select 'A|'||alias_code||'|'||canonical_permission_code||'|'||alias_type||'|'||coalesce(to_char(valid_from at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||coalesce(to_char(valid_until at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||reason
   from permission_catalog_revision_aliases where revision_id='00000000-0000-0000-0000-000000000020'::uuid)
 select 'sha256:'||encode(sha256(convert_to(coalesce(string_agg(line,E'\n' order by line),''),'UTF8')),'hex') from catalog_lines),
 published_at=now(),published_by='rs3-agent-service-scope',version=version+1
where revision_id='00000000-0000-0000-0000-000000000020'::uuid and status='DRAFT';

update permission_catalog_active_revision set revision_id='00000000-0000-0000-0000-000000000020'::uuid,
 activated_at=now(),activated_by='rs3-agent-service-scope',version=version+1 where singleton_id='ACTIVE';

insert into permission_catalog_publication_events(publication_id,revision_id,previous_revision_id,content_hash,entry_count,alias_count,actor_id,audit_reason,correlation_id,published_at)
select '00000000-0000-0000-0000-000000005020'::uuid,r.revision_id,r.supersedes_revision_id,r.content_hash,
 (select count(*)::integer from permission_catalog_revision_entries e where e.revision_id=r.revision_id),
 (select count(*)::integer from permission_catalog_revision_aliases a where a.revision_id=r.revision_id),
 'rs3-agent-service-scope','RS3 Agent + Service Identity scoped authorization publication','rs3-agent-service-scope',coalesce(r.published_at,now())
from permission_catalog_revisions r where r.revision_id='00000000-0000-0000-0000-000000000020'::uuid
on conflict(publication_id) do nothing;

-- R3 entry-point source hashes are refreshed after controller scope enforcement changes.
update permission_entry_point_inventory set source_hash='5e101cb68d380f5850728e51120965dea827b2bc565ce7e187734a380440c6be',manifest_revision='rs3-agent-entrypoints-2026-08-12',
 last_verified_at=now(),updated_at=now(),updated_by='rs3-agent-service-scope',version=version+1
where source_ref like 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentGovernanceController.java#%';
update permission_entry_point_inventory set source_hash='fc92171805fbba513db28979065a26e7de9de5a29f4f6dd747043142e1b6472d',manifest_revision='rs3-agent-entrypoints-2026-08-12',
 last_verified_at=now(),updated_at=now(),updated_by='rs3-agent-service-scope',version=version+1
where source_ref like 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentAssignmentController.java#%';
update permission_entry_point_inventory set source_hash='4445f9f0fbab7dd97f7ebdffe3f3938436e68507eead44c028565851f6ef5040',manifest_revision='rs3-agent-entrypoints-2026-08-12',
 last_verified_at=now(),updated_at=now(),updated_by='rs3-agent-service-scope',version=version+1
where source_ref like 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentSetupController.java#%';
update permission_entry_point_inventory set source_hash='981013a493e3930d111899a3fb1a94e7daaad27010ef69f74e0f364640c423b6',manifest_revision='rs3-agent-entrypoints-2026-08-12',
 last_verified_at=now(),updated_at=now(),updated_by='rs3-agent-service-scope',version=version+1
where source_ref like 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentRemediationController.java#%';
update permission_entry_point_inventory set source_hash='90ab33f9b0498fe41a430d8b07732426c49e8928b71e6a546fda1f4ed5d5ad91',manifest_revision='rs3-agent-entrypoints-2026-08-12',
 last_verified_at=now(),updated_at=now(),updated_by='rs3-agent-service-scope',version=version+1
where source_ref like 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentSkillRegistryController.java#%';
update permission_entry_point_inventory set source_hash='994255cc340dd3751bacd4fd05d0d07cf3b3313aab0bd44be28df49500a7ea07',manifest_revision='rs3-agent-entrypoints-2026-08-12',
 last_verified_at=now(),updated_at=now(),updated_by='rs3-agent-service-scope',version=version+1
where source_ref like 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchEligibilityController.java#%';
