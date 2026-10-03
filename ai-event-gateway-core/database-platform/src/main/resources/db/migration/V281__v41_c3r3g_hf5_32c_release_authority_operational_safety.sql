-- V41 C3R3G-HF5.32C: Release Authority & Operational Safety Closure.
-- Separates release/cutover authority from Agent Assignment, adds release lifecycle
-- separation-of-duty support, canonical Flow referential integrity and migration identity,
-- and publishes explicit preflight/promote/rollback entry points. No global cutover exists.

select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','v41-c3r3g-hf5-32c-release-authority',true);

-- 1. Release Candidate migration identity is no longer pinned to the historical V208
--    schema-introduction migration. V208 remains the compatibility table suffix only.
alter table a0_production_release_candidates_v208
  add column if not exists migration_baseline varchar(32),
  add column if not exists migration_head varchar(32),
  add column if not exists migration_set_hash varchar(96);

alter table a0_production_release_candidates_v208
  drop constraint if exists a0release_candidate_migration_check;
alter table a0_production_release_candidates_v208
  add constraint a0release_candidate_migration_check check(migration_version ~ '^V[0-9]+$');

update a0_production_release_candidates_v208
   set migration_baseline=coalesce(migration_baseline,'V1'),
       migration_head=coalesce(migration_head,migration_version),
       migration_set_hash=coalesce(migration_set_hash,'legacy:'||migration_version)
 where migration_baseline is null or migration_head is null or migration_set_hash is null;

alter table a0_production_release_candidates_v208
  alter column migration_baseline set not null,
  alter column migration_head set not null,
  alter column migration_set_hash set not null;

alter table a0_production_release_candidates_v208
  drop constraint if exists a0release_candidate_migration_baseline_check;
alter table a0_production_release_candidates_v208
  add constraint a0release_candidate_migration_baseline_check check(migration_baseline ~ '^V[0-9]+$');
alter table a0_production_release_candidates_v208
  drop constraint if exists a0release_candidate_migration_head_check;
alter table a0_production_release_candidates_v208
  add constraint a0release_candidate_migration_head_check check(migration_head ~ '^V[0-9]+$');

-- Certified artifact/migration identity is immutable even for privileged application paths.
create or replace function prevent_a0release_candidate_identity_mutation_v281() returns trigger language plpgsql as $$
begin
  if old.artifact_name is distinct from new.artifact_name
     or old.artifact_sha256 is distinct from new.artifact_sha256
     or old.product_snapshot is distinct from new.product_snapshot
     or old.migration_version is distinct from new.migration_version
     or old.migration_baseline is distinct from new.migration_baseline
     or old.migration_head is distinct from new.migration_head
     or old.migration_set_hash is distinct from new.migration_set_hash
     or old.source_gate_status is distinct from new.source_gate_status
     or old.source_gate_ref is distinct from new.source_gate_ref
     or old.build_status is distinct from new.build_status
     or old.build_ref is distinct from new.build_ref
     or old.environment_ref is distinct from new.environment_ref then
    raise exception 'A0_RELEASE_CANDIDATE_IDENTITY_IS_IMMUTABLE';
  end if;
  return new;
end $$;

drop trigger if exists trg_a0release_candidate_identity_immutable_v281 on a0_production_release_candidates_v208;
create trigger trg_a0release_candidate_identity_immutable_v281
before update on a0_production_release_candidates_v208
for each row execute function prevent_a0release_candidate_identity_mutation_v281();

-- 2. Canonical Flow integrity. NOT VALID preserves historical rows for explicit cleanup,
--    while PostgreSQL still enforces the FK for every new/updated row after this migration.
do $$ begin
  if not exists(select 1 from pg_constraint where conname='fk_flow_routing_state_canonical_flow_v281') then
    alter table flow_routing_migration_state
      add constraint fk_flow_routing_state_canonical_flow_v281
      foreign key(tenant_id,flow_id) references dispatch_flows(tenant_id,flow_id) not valid;
  end if;
  if not exists(select 1 from pg_constraint where conname='fk_flow_routing_event_canonical_flow_v281') then
    alter table flow_routing_migration_events
      add constraint fk_flow_routing_event_canonical_flow_v281
      foreign key(tenant_id,flow_id) references dispatch_flows(tenant_id,flow_id) not valid;
  end if;
end $$;

create or replace view release_flow_integrity_v281 as
select s.tenant_id,s.flow_id,s.migration_state,s.changed_at,
       case when f.flow_id is null then 'PHANTOM_FLOW_STATE' else 'CANONICAL' end integrity_status
  from flow_routing_migration_state s
  left join dispatch_flows f on f.tenant_id=s.tenant_id and f.flow_id=s.flow_id;

-- 3. Dedicated Release Certification permission domain.
insert into permission_catalog_revisions(
 revision_id,revision_code,revision_number,status,content_hash,description,supersedes_revision_id,
 created_at,created_by,published_at,published_by,version)
select '00000000-0000-0000-0000-000000000028'::uuid,
       'V41-C3R3G-HF5-32C-RELEASE-AUTHORITY',coalesce(max(revision_number),0)+1,
       'DRAFT','DRAFT:UNPUBLISHED','Dedicated Release Certification lifecycle and per-Flow production authority permissions.',
       (select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE'),
       now(),'v41-c3r3g-hf5-32c-release-authority',null,null,1
from permission_catalog_revisions
on conflict(revision_id) do nothing;

insert into permission_catalog_revision_entries(
 revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,lifecycle,
 allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by,version)
select '00000000-0000-0000-0000-000000000028'::uuid,e.permission_code,e.owner_module,e.resource_type,e.action_code,e.description,
 e.risk_level,e.risk_lane,e.lifecycle,e.allowed_scope_types,e.system_managed,e.replacement_permission_code,e.introduced_at,e.deprecated_at,
 e.retired_at,now(),'v41-c3r3g-hf5-32c-release-authority',1
from permission_catalog_revision_entries e
join permission_catalog_active_revision a on a.singleton_id='ACTIVE' and a.revision_id=e.revision_id
on conflict(revision_id,permission_code) do nothing;

insert into permission_catalog_revision_aliases(
 revision_id,alias_code,canonical_permission_code,alias_type,valid_from,valid_until,reason,created_by,created_at,version)
select '00000000-0000-0000-0000-000000000028'::uuid,a.alias_code,a.canonical_permission_code,a.alias_type,a.valid_from,a.valid_until,a.reason,
 'v41-c3r3g-hf5-32c-release-authority',now(),1
from permission_catalog_revision_aliases a
join permission_catalog_active_revision active on active.singleton_id='ACTIVE' and active.revision_id=a.revision_id
on conflict(revision_id,alias_code) do nothing;

insert into permission_catalog_revision_entries(
 revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,lifecycle,
 allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by,version)
values
 ('00000000-0000-0000-0000-000000000028','release.certification.read','release-certification','RELEASE_CERTIFICATION','READ','Read release readiness, candidates, canonical Flow authority and impact information.','HIGH','READ','ACTIVE',array['TENANT']::varchar[],true,null,now(),null,null,now(),'v41-c3r3g-hf5-32c-release-authority',1),
 ('00000000-0000-0000-0000-000000000028','release.evidence.record.exception','release-certification','RELEASE_EVIDENCE','RECORD_EXCEPTION','Record exceptional external/manual E2E release evidence.','HIGH','WRITE','ACTIVE',array['TENANT']::varchar[],true,null,now(),null,null,now(),'v41-c3r3g-hf5-32c-release-authority',1),
 ('00000000-0000-0000-0000-000000000028','release.candidate.create','release-certification','RELEASE_CANDIDATE','CREATE','Create a DRAFT release candidate bound to immutable artifact/build/environment/migration identity.','HIGH','WRITE','ACTIVE',array['TENANT']::varchar[],true,null,now(),null,null,now(),'v41-c3r3g-hf5-32c-release-authority',1),
 ('00000000-0000-0000-0000-000000000028','release.candidate.certify','release-certification','RELEASE_CANDIDATE','CERTIFY','Certify a release candidate against current Runtime and E2E evidence; creator/certifier SoD applies.','CRITICAL','CRITICAL','ACTIVE',array['TENANT']::varchar[],true,null,now(),null,null,now(),'v41-c3r3g-hf5-32c-release-authority',1),
 ('00000000-0000-0000-0000-000000000028','release.candidate.preflight','release-certification','RELEASE_CANDIDATE','PREFLIGHT','Preview candidate activation/revocation blockers and production impact.','HIGH','READ','ACTIVE',array['TENANT']::varchar[],true,null,now(),null,null,now(),'v41-c3r3g-hf5-32c-release-authority',1),
 ('00000000-0000-0000-0000-000000000028','release.candidate.activate','release-certification','RELEASE_CANDIDATE','ACTIVATE','Activate a certified candidate after impact confirmation; certifier/activator SoD applies.','CRITICAL','CRITICAL','ACTIVE',array['TENANT']::varchar[],true,null,now(),null,null,now(),'v41-c3r3g-hf5-32c-release-authority',1),
 ('00000000-0000-0000-0000-000000000028','release.candidate.revoke','release-certification','RELEASE_CANDIDATE','REVOKE','Revoke a candidate after reviewing affected production-bound Flows and execution impact.','CRITICAL','CRITICAL','ACTIVE',array['TENANT']::varchar[],true,null,now(),null,null,now(),'v41-c3r3g-hf5-32c-release-authority',1),
 ('00000000-0000-0000-0000-000000000028','release.flow.preflight','release-certification','DISPATCH_FLOW','PREFLIGHT','Preview canonical Flow production promotion/rollback eligibility and impact.','HIGH','READ','ACTIVE',array['TENANT']::varchar[],true,null,now(),null,null,now(),'v41-c3r3g-hf5-32c-release-authority',1),
 ('00000000-0000-0000-0000-000000000028','release.flow.promote','release-certification','DISPATCH_FLOW','PROMOTE','Promote one canonical Flow to NEW_AUTHORITATIVE with an ACTIVE release candidate.','CRITICAL','CRITICAL','ACTIVE',array['TENANT']::varchar[],true,null,now(),null,null,now(),'v41-c3r3g-hf5-32c-release-authority',1),
 ('00000000-0000-0000-0000-000000000028','release.flow.rollback','release-certification','DISPATCH_FLOW','ROLLBACK','Reduce one canonical Flow from production authority to SHADOW or LEGACY_AUTHORITATIVE.','HIGH','WRITE','ACTIVE',array['TENANT']::varchar[],true,null,now(),null,null,now(),'v41-c3r3g-hf5-32c-release-authority',1)
on conflict(revision_id,permission_code) do update set description=excluded.description,risk_level=excluded.risk_level,
 risk_lane=excluded.risk_lane,lifecycle='ACTIVE',allowed_scope_types=excluded.allowed_scope_types,updated_at=now(),
 updated_by='v41-c3r3g-hf5-32c-release-authority',version=permission_catalog_revision_entries.version+1;

select set_config('app.permission_catalog_publish_revision_id','00000000-0000-0000-0000-000000000028',true);
insert into permission_definitions(
 permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,active,version,owner_module,risk_lane,lifecycle,
 catalog_revision_id,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by)
select permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,lifecycle<>'RETIRED',1,owner_module,risk_lane,lifecycle,
 revision_id,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by
from permission_catalog_revision_entries where revision_id='00000000-0000-0000-0000-000000000028'::uuid
on conflict(permission_code) do update set resource_type=excluded.resource_type,action_code=excluded.action_code,description=excluded.description,
 risk_level=excluded.risk_level,allowed_scope_types=excluded.allowed_scope_types,system_managed=excluded.system_managed,active=excluded.active,
 owner_module=excluded.owner_module,risk_lane=excluded.risk_lane,lifecycle=excluded.lifecycle,catalog_revision_id=excluded.catalog_revision_id,
 updated_at=excluded.updated_at,updated_by=excluded.updated_by,version=permission_definitions.version+1;

update permission_catalog_revisions set status='SUPERSEDED',version=version+1
where revision_id=(select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE')
 and revision_id<>'00000000-0000-0000-0000-000000000028'::uuid and status='PUBLISHED';

update permission_catalog_revisions set status='PUBLISHED',content_hash=(
 with catalog_lines as (
   select 'P|'||permission_code||'|'||owner_module||'|'||resource_type||'|'||action_code||'|'||description||'|'||risk_level||'|'||risk_lane||'|'||lifecycle||'|'||coalesce(array_to_string(allowed_scope_types,','),'')||'|'||system_managed::text||'|'||coalesce(replacement_permission_code,'') line
   from permission_catalog_revision_entries where revision_id='00000000-0000-0000-0000-000000000028'::uuid
   union all
   select 'A|'||alias_code||'|'||canonical_permission_code||'|'||alias_type||'|'||coalesce(to_char(valid_from at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||coalesce(to_char(valid_until at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||reason
   from permission_catalog_revision_aliases where revision_id='00000000-0000-0000-0000-000000000028'::uuid)
 select 'sha256:'||encode(sha256(convert_to(coalesce(string_agg(line,E'\n' order by line),''),'UTF8')),'hex') from catalog_lines),
 published_at=now(),published_by='v41-c3r3g-hf5-32c-release-authority',version=version+1
where revision_id='00000000-0000-0000-0000-000000000028'::uuid and status='DRAFT';

update permission_catalog_active_revision set revision_id='00000000-0000-0000-0000-000000000028'::uuid,
 activated_at=now(),activated_by='v41-c3r3g-hf5-32c-release-authority',version=version+1 where singleton_id='ACTIVE';

insert into permission_catalog_publication_events(publication_id,revision_id,previous_revision_id,content_hash,entry_count,alias_count,actor_id,audit_reason,correlation_id,published_at)
select '00000000-0000-0000-0000-000000005028'::uuid,r.revision_id,r.supersedes_revision_id,r.content_hash,
 (select count(*)::integer from permission_catalog_revision_entries e where e.revision_id=r.revision_id),
 (select count(*)::integer from permission_catalog_revision_aliases a where a.revision_id=r.revision_id),
 'v41-c3r3g-hf5-32c-release-authority','HF5.32C dedicated Release Certification authority publication','v41-c3r3g-hf5-32c-release-authority',coalesce(r.published_at,now())
from permission_catalog_revisions r where r.revision_id='00000000-0000-0000-0000-000000000028'::uuid
on conflict(publication_id) do nothing;

-- Project existing grants from the exact legacy capabilities used by V208. This does not
-- broaden who can release; it separates the permissions so Responsibilities can split SoD.
insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version)
select 'hf532c-read-'||substr(md5(existing.role_id||':'||p.permission_code),1,27),existing.tenant_id,existing.role_id,p.permission_code,now(),'v41-c3r3g-hf5-32c-release-authority',1
from rbac_role_permissions existing
join permission_definitions p on p.permission_code in('release.certification.read','release.candidate.preflight')
where existing.permission_point='api.governance.contract.evidence'
on conflict(role_id,permission_point) do nothing;

insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version)
select 'hf532c-life-'||substr(md5(existing.role_id||':'||p.permission_code),1,27),existing.tenant_id,existing.role_id,p.permission_code,now(),'v41-c3r3g-hf5-32c-release-authority',1
from rbac_role_permissions existing
join permission_definitions p on p.permission_code in('release.evidence.record.exception','release.candidate.create','release.candidate.certify','release.candidate.activate','release.candidate.revoke')
where existing.permission_point='admin.agent.assignment.upsert.capability'
on conflict(role_id,permission_point) do nothing;

insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version)
select 'hf532c-flow-'||substr(md5(existing.role_id||':'||p.permission_code),1,27),existing.tenant_id,existing.role_id,p.permission_code,now(),'v41-c3r3g-hf5-32c-release-authority',1
from rbac_role_permissions existing
join permission_definitions p on p.permission_code in('release.flow.preflight','release.flow.promote','release.flow.rollback')
where existing.permission_point='admin.agent.assignment.capabilities'
on conflict(role_id,permission_point) do nothing;

-- 4. Canonical HTTP entry points. Existing V208 entries are re-pointed to Release permissions;
--    new high-risk operations get explicit preflight/promote/rollback routes.
insert into permission_entry_point_inventory(
 entry_point_id,entry_point_type,application_id,owner_module,display_name,route_pattern,http_method,authority_state,target_permission_code,
 legacy_authority_type,legacy_authorities,resource_type,resource_resolver_id,exemption_reason,migration_deadline,manifest_revision,source_ref,source_hash,
 last_verified_at,created_by,updated_by)
values
('REST:GET:/admin/production-foundation/summary','REST','control-plane-app','release-certification','ProductionFoundationReleaseController.summary','/admin/production-foundation/summary','GET','TARGET_ONLY','release.certification.read',null,'[]'::jsonb,'RELEASE_CERTIFICATION','R3_TENANT_RESOURCE_RESOLVER',null,null,'v41-c3r3g-hf5-32c-2026-10-02','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ProductionFoundationReleaseController.java#summary','cf30bb2f9771555bc3194054f309111e67d2dcd7e79c412b679da2f801bbf6fd',now(),'v41-c3r3g-hf5-32c-release-authority','v41-c3r3g-hf5-32c-release-authority'),
('REST:POST:/admin/production-foundation/cutover-runs','REST','control-plane-app','release-certification','ProductionFoundationReleaseController.recordRun','/admin/production-foundation/cutover-runs','POST','TARGET_ONLY','release.evidence.record.exception',null,'[]'::jsonb,'RELEASE_EVIDENCE','R3_TENANT_RESOURCE_RESOLVER',null,null,'v41-c3r3g-hf5-32c-2026-10-02','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ProductionFoundationReleaseController.java#recordRun','cf30bb2f9771555bc3194054f309111e67d2dcd7e79c412b679da2f801bbf6fd',now(),'v41-c3r3g-hf5-32c-release-authority','v41-c3r3g-hf5-32c-release-authority'),
('REST:POST:/admin/production-foundation/candidates','REST','control-plane-app','release-certification','ProductionFoundationReleaseController.createCandidate','/admin/production-foundation/candidates','POST','TARGET_ONLY','release.candidate.create',null,'[]'::jsonb,'RELEASE_CANDIDATE','R3_TENANT_RESOURCE_RESOLVER',null,null,'v41-c3r3g-hf5-32c-2026-10-02','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ProductionFoundationReleaseController.java#createCandidate','cf30bb2f9771555bc3194054f309111e67d2dcd7e79c412b679da2f801bbf6fd',now(),'v41-c3r3g-hf5-32c-release-authority','v41-c3r3g-hf5-32c-release-authority'),
('REST:POST:/admin/production-foundation/candidates/{candidateId}/certify','REST','control-plane-app','release-certification','ProductionFoundationReleaseController.certifyCandidate','/admin/production-foundation/candidates/{candidateId}/certify','POST','TARGET_ONLY','release.candidate.certify',null,'[]'::jsonb,'RELEASE_CANDIDATE','R3_PATH_RESOURCE_RESOLVER',null,null,'v41-c3r3g-hf5-32c-2026-10-02','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ProductionFoundationReleaseController.java#certifyCandidate','cf30bb2f9771555bc3194054f309111e67d2dcd7e79c412b679da2f801bbf6fd',now(),'v41-c3r3g-hf5-32c-release-authority','v41-c3r3g-hf5-32c-release-authority'),
('REST:POST:/admin/production-foundation/candidates/{candidateId}/preflight','REST','control-plane-app','release-certification','ProductionFoundationReleaseController.candidatePreflight','/admin/production-foundation/candidates/{candidateId}/preflight','POST','TARGET_ONLY','release.candidate.preflight',null,'[]'::jsonb,'RELEASE_CANDIDATE','R3_PATH_RESOURCE_RESOLVER',null,null,'v41-c3r3g-hf5-32c-2026-10-02','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ProductionFoundationReleaseController.java#candidatePreflight','cf30bb2f9771555bc3194054f309111e67d2dcd7e79c412b679da2f801bbf6fd',now(),'v41-c3r3g-hf5-32c-release-authority','v41-c3r3g-hf5-32c-release-authority'),
('REST:POST:/admin/production-foundation/candidates/{candidateId}/activate','REST','control-plane-app','release-certification','ProductionFoundationReleaseController.activateCandidate','/admin/production-foundation/candidates/{candidateId}/activate','POST','TARGET_ONLY','release.candidate.activate',null,'[]'::jsonb,'RELEASE_CANDIDATE','R3_PATH_RESOURCE_RESOLVER',null,null,'v41-c3r3g-hf5-32c-2026-10-02','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ProductionFoundationReleaseController.java#activateCandidate','cf30bb2f9771555bc3194054f309111e67d2dcd7e79c412b679da2f801bbf6fd',now(),'v41-c3r3g-hf5-32c-release-authority','v41-c3r3g-hf5-32c-release-authority'),
('REST:POST:/admin/production-foundation/candidates/{candidateId}/revoke','REST','control-plane-app','release-certification','ProductionFoundationReleaseController.revokeCandidate','/admin/production-foundation/candidates/{candidateId}/revoke','POST','TARGET_ONLY','release.candidate.revoke',null,'[]'::jsonb,'RELEASE_CANDIDATE','R3_PATH_RESOURCE_RESOLVER',null,null,'v41-c3r3g-hf5-32c-2026-10-02','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ProductionFoundationReleaseController.java#revokeCandidate','cf30bb2f9771555bc3194054f309111e67d2dcd7e79c412b679da2f801bbf6fd',now(),'v41-c3r3g-hf5-32c-release-authority','v41-c3r3g-hf5-32c-release-authority'),
('REST:POST:/admin/production-foundation/flows/{flowId}/preflight','REST','control-plane-app','release-certification','ProductionFoundationReleaseController.flowPreflight','/admin/production-foundation/flows/{flowId}/preflight','POST','TARGET_ONLY','release.flow.preflight',null,'[]'::jsonb,'DISPATCH_FLOW','R3_PATH_RESOURCE_RESOLVER',null,null,'v41-c3r3g-hf5-32c-2026-10-02','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ProductionFoundationReleaseController.java#flowPreflight','cf30bb2f9771555bc3194054f309111e67d2dcd7e79c412b679da2f801bbf6fd',now(),'v41-c3r3g-hf5-32c-release-authority','v41-c3r3g-hf5-32c-release-authority'),
('REST:POST:/admin/production-foundation/flows/{flowId}/promote','REST','control-plane-app','release-certification','ProductionFoundationReleaseController.promoteFlow','/admin/production-foundation/flows/{flowId}/promote','POST','TARGET_ONLY','release.flow.promote',null,'[]'::jsonb,'DISPATCH_FLOW','R3_PATH_RESOURCE_RESOLVER',null,null,'v41-c3r3g-hf5-32c-2026-10-02','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ProductionFoundationReleaseController.java#promoteFlow','cf30bb2f9771555bc3194054f309111e67d2dcd7e79c412b679da2f801bbf6fd',now(),'v41-c3r3g-hf5-32c-release-authority','v41-c3r3g-hf5-32c-release-authority'),
('REST:POST:/admin/production-foundation/flows/{flowId}/rollback','REST','control-plane-app','release-certification','ProductionFoundationReleaseController.rollbackFlow','/admin/production-foundation/flows/{flowId}/rollback','POST','TARGET_ONLY','release.flow.rollback',null,'[]'::jsonb,'DISPATCH_FLOW','R3_PATH_RESOURCE_RESOLVER',null,null,'v41-c3r3g-hf5-32c-2026-10-02','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ProductionFoundationReleaseController.java#rollbackFlow','cf30bb2f9771555bc3194054f309111e67d2dcd7e79c412b679da2f801bbf6fd',now(),'v41-c3r3g-hf5-32c-release-authority','v41-c3r3g-hf5-32c-release-authority'),
('REST:PUT:/admin/production-foundation/flows/{flowId}/authority','REST','control-plane-app','release-certification','ProductionFoundationReleaseController.setFlowAuthorityLegacyRollbackOnly','/admin/production-foundation/flows/{flowId}/authority','PUT','TARGET_ONLY','release.flow.rollback',null,'[]'::jsonb,'DISPATCH_FLOW','R3_PATH_RESOURCE_RESOLVER','Compatibility URI retained for discovery only; all authority mutations require /preflight then /promote or /rollback.','2026-12-31','v41-c3r3g-hf5-32c-2026-10-02','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ProductionFoundationReleaseController.java#setFlowAuthorityLegacyRollbackOnly','cf30bb2f9771555bc3194054f309111e67d2dcd7e79c412b679da2f801bbf6fd',now(),'v41-c3r3g-hf5-32c-release-authority','v41-c3r3g-hf5-32c-release-authority')
on conflict(entry_point_id) do update set display_name=excluded.display_name,route_pattern=excluded.route_pattern,http_method=excluded.http_method,
 authority_state=excluded.authority_state,target_permission_code=excluded.target_permission_code,legacy_authority_type=null,legacy_authorities='[]'::jsonb,
 resource_type=excluded.resource_type,resource_resolver_id=excluded.resource_resolver_id,exemption_reason=excluded.exemption_reason,migration_deadline=excluded.migration_deadline,
 manifest_revision=excluded.manifest_revision,source_ref=excluded.source_ref,source_hash=excluded.source_hash,last_verified_at=now(),updated_at=now(),
 updated_by='v41-c3r3g-hf5-32c-release-authority',version=permission_entry_point_inventory.version+1;

update rbac_policy_versions set policy_version=policy_version+1,updated_at=now(),updated_by='v41-c3r3g-hf5-32c-release-authority';

-- 5. Migration self-checks.
do $$
declare v_permissions integer; v_routes integer;
begin
  select count(*) into v_permissions from permission_definitions
   where permission_code in('release.certification.read','release.evidence.record.exception','release.candidate.create','release.candidate.certify','release.candidate.preflight','release.candidate.activate','release.candidate.revoke','release.flow.preflight','release.flow.promote','release.flow.rollback')
     and active=true and lifecycle='ACTIVE';
  if v_permissions<>10 then raise exception 'HF5_32C_RELEASE_PERMISSION_CATALOG_INCOMPLETE expected=10 actual=%',v_permissions; end if;

  select count(*) into v_routes from permission_entry_point_inventory
   where route_pattern like '/admin/production-foundation/%' and target_permission_code like 'release.%' and authority_state='TARGET_ONLY';
  if v_routes<11 then raise exception 'HF5_32C_RELEASE_ROUTE_MAPPING_INCOMPLETE expected_at_least=11 actual=%',v_routes; end if;
end $$;
