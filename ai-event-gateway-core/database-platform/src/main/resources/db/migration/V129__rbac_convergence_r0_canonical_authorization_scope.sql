-- RBAC Convergence R0: make the published Permission Catalog agree with the
-- canonical DEPARTMENT/GROUP authorization guards and scoped People projection.
-- Tenant scope remains valid; scoped grants are explicit and still require organization membership.

select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','rbac-convergence-r0',true);

insert into permission_catalog_revisions(
  revision_id,revision_code,revision_number,status,content_hash,description,supersedes_revision_id,
  created_at,created_by,published_at,published_by,version)
select '00000000-0000-0000-0000-000000000016'::uuid,'RBAC-R0-0.8.2',coalesce(max(revision_number),0)+1,
  'DRAFT','DRAFT:UNPUBLISHED',
  'RBAC Convergence R0 canonical Department, Group and organization-membership authorization scopes.',
  (select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE'),
  now(),'rbac-convergence-r0',null,null,1
from permission_catalog_revisions
on conflict(revision_id) do nothing;

insert into permission_catalog_revision_entries(
  revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,
  lifecycle,allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,
  retired_at,updated_at,updated_by,version)
select '00000000-0000-0000-0000-000000000016'::uuid,e.permission_code,e.owner_module,e.resource_type,
  e.action_code,e.description,e.risk_level,e.risk_lane,e.lifecycle,e.allowed_scope_types,e.system_managed,
  e.replacement_permission_code,e.introduced_at,e.deprecated_at,e.retired_at,now(),'rbac-convergence-r0',1
from permission_catalog_revision_entries e
join permission_catalog_active_revision a on a.singleton_id='ACTIVE' and a.revision_id=e.revision_id
on conflict(revision_id,permission_code) do nothing;

insert into permission_catalog_revision_aliases(
  revision_id,alias_code,canonical_permission_code,alias_type,valid_from,valid_until,reason,created_by,created_at,version)
select '00000000-0000-0000-0000-000000000016'::uuid,a.alias_code,a.canonical_permission_code,
  a.alias_type,a.valid_from,a.valid_until,a.reason,'rbac-convergence-r0',now(),1
from permission_catalog_revision_aliases a
join permission_catalog_active_revision active on active.singleton_id='ACTIVE' and active.revision_id=a.revision_id
on conflict(revision_id,alias_code) do nothing;

update permission_catalog_revision_entries
set allowed_scope_types = case permission_code
      when 'identity.user.read' then array['TENANT','DEPARTMENT','GROUP']::varchar[]
      when 'identity.department.manage' then array['TENANT','DEPARTMENT']::varchar[]
      when 'identity.group.manage' then array['TENANT','GROUP']::varchar[]
      when 'identity.membership.manage' then array['TENANT','DEPARTMENT','GROUP']::varchar[]
      else allowed_scope_types
    end,
    updated_at=now(),updated_by='rbac-convergence-r0',version=version+1
where revision_id='00000000-0000-0000-0000-000000000016'::uuid
  and permission_code in('identity.user.read','identity.department.manage','identity.group.manage','identity.membership.manage');

-- V80 renamed the former permission_point_catalog table to permission_definitions.
-- Do not reference the retired Phase-1 relation here. The canonical revision is
-- projected into permission_definitions below while the publication guard is active.

select set_config('app.permission_catalog_publish_revision_id','00000000-0000-0000-0000-000000000016',true);

insert into permission_definitions(
  permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,
  active,version,owner_module,risk_lane,lifecycle,catalog_revision_id,replacement_permission_code,
  introduced_at,deprecated_at,retired_at,updated_at,updated_by)
select permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,
  lifecycle<>'RETIRED',1,owner_module,risk_lane,lifecycle,revision_id,replacement_permission_code,
  introduced_at,deprecated_at,retired_at,updated_at,updated_by
from permission_catalog_revision_entries
where revision_id='00000000-0000-0000-0000-000000000016'::uuid
on conflict(permission_code) do update set
  resource_type=excluded.resource_type,action_code=excluded.action_code,description=excluded.description,
  risk_level=excluded.risk_level,allowed_scope_types=excluded.allowed_scope_types,
  system_managed=excluded.system_managed,active=excluded.active,owner_module=excluded.owner_module,
  risk_lane=excluded.risk_lane,lifecycle=excluded.lifecycle,catalog_revision_id=excluded.catalog_revision_id,
  replacement_permission_code=excluded.replacement_permission_code,deprecated_at=excluded.deprecated_at,
  retired_at=excluded.retired_at,updated_at=excluded.updated_at,updated_by=excluded.updated_by,
  version=permission_definitions.version+1;

update permission_catalog_revisions set status='SUPERSEDED',version=version+1
where revision_id=(select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE')
  and revision_id<>'00000000-0000-0000-0000-000000000016'::uuid and status='PUBLISHED';

update permission_catalog_revisions set status='PUBLISHED',content_hash=(
  with catalog_lines as (
    select 'P|'||permission_code||'|'||owner_module||'|'||resource_type||'|'||action_code||'|'||description||'|'||
      risk_level||'|'||risk_lane||'|'||lifecycle||'|'||coalesce(array_to_string(allowed_scope_types,','),'')||'|'||
      system_managed::text||'|'||coalesce(replacement_permission_code,'') as line
    from permission_catalog_revision_entries where revision_id='00000000-0000-0000-0000-000000000016'::uuid
    union all
    select 'A|'||alias_code||'|'||canonical_permission_code||'|'||alias_type||'|'||
      coalesce(to_char(valid_from at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||
      coalesce(to_char(valid_until at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||reason
    from permission_catalog_revision_aliases where revision_id='00000000-0000-0000-0000-000000000016'::uuid)
  select 'sha256:'||encode(sha256(convert_to(coalesce(string_agg(line,E'\n' order by line),''),'UTF8')),'hex') from catalog_lines),
  published_at=now(),published_by='rbac-convergence-r0',version=version+1
where revision_id='00000000-0000-0000-0000-000000000016'::uuid and status='DRAFT';

update permission_catalog_active_revision
set revision_id='00000000-0000-0000-0000-000000000016'::uuid,
    activated_at=now(),activated_by='rbac-convergence-r0',version=version+1
where singleton_id='ACTIVE';

insert into permission_catalog_publication_events(
  publication_id,revision_id,previous_revision_id,content_hash,entry_count,alias_count,
  actor_id,audit_reason,correlation_id,published_at)
select '00000000-0000-0000-0000-000000005016'::uuid,r.revision_id,r.supersedes_revision_id,r.content_hash,
  (select count(*)::integer from permission_catalog_revision_entries e where e.revision_id=r.revision_id),
  (select count(*)::integer from permission_catalog_revision_aliases a where a.revision_id=r.revision_id),
  'rbac-convergence-r0','R0 canonical scoped authorization publication','rbac-convergence-r0',coalesce(r.published_at,now())
from permission_catalog_revisions r
where r.revision_id='00000000-0000-0000-0000-000000000016'::uuid
on conflict(publication_id) do nothing;

-- The R0 controller now resolves real Department/Group scopes. Refresh the governed
-- entry-point evidence without rewriting historical migration checksums.
update permission_entry_point_inventory
set manifest_revision='rbac-convergence-r0-canonical-authorization-2026-08-07',
    source_hash='32ec7f2e5c56a90a670f95c73d7977eb874bdf02dfba00720b264884adeae8ee',
    last_verified_at=now(),updated_at=now(),updated_by='rbac-convergence-r0',version=version+1
where source_ref like 'ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/UnifiedAccessManagementController.java#%';

-- Scope support changes alter authorization outcomes; invalidate every cached policy projection.
update rbac_policy_versions
set policy_version=policy_version+1,updated_at=now(),updated_by='rbac-convergence-r0';
