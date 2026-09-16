-- P1 IAM User Lifecycle: register the transactional onboarding entry point and
-- publish a manifest that exactly matches the post-P1 source inventory.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','p1-user-lifecycle-migration',true);


-- Fix the Phase 5H manifest trigger before superseding the V92 active manifest.
-- The V93 trigger validates every UPDATE against the *current* active catalog
-- revision before it evaluates the only legal immutable transition
-- ACTIVE -> SUPERSEDED.  Between V93 and V104 the active permission catalog can
-- advance, so the historical active manifest remains correctly bound to its
-- original catalog revision and must still be supersedable.  Evaluate the
-- immutable lifecycle transition first; retain current-catalog validation for
-- INSERTs and all mutations of a REGISTERING candidate, including activation.
create or replace function phase5h_validate_manifest()
returns trigger language plpgsql as $$
declare active_revision uuid;active_code varchar(128);active_hash varchar(71);
begin
  if tg_op='DELETE' then
    raise exception 'PERMISSION_MANIFEST_IMMUTABLE' using errcode='55000';
  end if;

  if tg_op='UPDATE' then
    if old.status in('ACTIVE','SUPERSEDED','REJECTED') then
      if old.status='ACTIVE' and new.status='SUPERSEDED' and
         (to_jsonb(new)-'status'-'activated_at'-'activated_by'-'version')=
         (to_jsonb(old)-'status'-'activated_at'-'activated_by'-'version') then
        new.version=old.version+1;
        return new;
      end if;
      raise exception 'PERMISSION_MANIFEST_IMMUTABLE' using errcode='55000';
    end if;
  end if;

  select r.revision_id,r.revision_code,r.content_hash
  into active_revision,active_code,active_hash
  from permission_catalog_active_revision a
  join permission_catalog_revisions r on r.revision_id=a.revision_id
  where a.singleton_id='ACTIVE' and r.status='PUBLISHED';

  if new.catalog_revision_id is distinct from active_revision
     or new.catalog_revision_code is distinct from active_code
     or new.catalog_content_hash is distinct from active_hash then
    raise exception 'PERMISSION_MANIFEST_CATALOG_BINDING_MISMATCH' using errcode='23514';
  end if;
  return new;
end $$;


-- Fix the Phase 5H activation comparison before the first post-V103 manifest is
-- activated. The V93 implementation placed e.manifest_id=p_manifest_id inside
-- a FULL JOIN condition. FULL JOIN preserves unmatched rows from every older
-- manifest, so the 811 entries from the V92 active manifest were incorrectly
-- reported as runtime drift when V104 attempted to activate its replacement.
-- Scope both sides before joining: inventory by application, entries by the
-- candidate manifest.
create or replace function phase5h_activate_manifest(p_manifest_id uuid,p_actor_id varchar,p_correlation_id varchar,p_audit_reason varchar)
returns void language plpgsql as $$
declare m permission_application_manifests%rowtype;actual_count integer;actual_covered integer;blockers integer;computed_hash varchar(71);
begin
  perform pg_advisory_xact_lock(hashtextextended('permission-manifest:'||p_manifest_id::text,0));
  select * into m from permission_application_manifests where manifest_id=p_manifest_id for update;
  if not found then raise exception 'PERMISSION_MANIFEST_NOT_FOUND' using errcode='23503'; end if;
  if m.status<>'REGISTERING' then raise exception 'PERMISSION_MANIFEST_NOT_REGISTERING' using errcode='23514'; end if;
  select count(*),count(*) filter(where coverage_status in('COVERED','EXEMPT','DELEGATED')),
         'sha256:'||encode(sha256(convert_to(coalesce(string_agg(entry_point_id||'|'||descriptor_hash,E'\n' order by entry_point_id),''),'UTF8')),'hex')
  into actual_count,actual_covered,computed_hash from permission_application_manifest_entries where manifest_id=p_manifest_id;
  if actual_count<>m.entry_count or actual_covered<>m.covered_entry_count or actual_covered<>actual_count or m.uncovered_count<>0 or m.coverage_percent<>100.0000 then
    raise exception 'PERMISSION_MANIFEST_COVERAGE_NOT_100' using errcode='23514';
  end if;
  if computed_hash<>m.manifest_hash then raise exception 'PERMISSION_MANIFEST_HASH_MISMATCH' using errcode='23514'; end if;
  select count(*) into blockers from (
    select coalesce(i.entry_point_id,e.entry_point_id) as entry_point_id
    from (
      select * from permission_entry_point_inventory where application_id=m.application_id
    ) i
    full join (
      select * from permission_application_manifest_entries where manifest_id=p_manifest_id
    ) e on e.entry_point_id=i.entry_point_id
    left join permission_definitions p on p.permission_code=e.permission_code
    where i.entry_point_id is null or e.entry_point_id is null or i.source_hash is distinct from e.source_hash
       or (e.permission_code is not null and (p.permission_code is null or p.active=false or p.lifecycle='RETIRED'))
       or (e.scope_required and e.resource_resolver_id in('','NONE'))
  ) drift;
  if blockers>0 then raise exception 'PERMISSION_MANIFEST_RUNTIME_DRIFT:%',blockers using errcode='23514'; end if;
  update permission_application_manifests set status='SUPERSEDED'
  where application_id=m.application_id and environment=m.environment and status='ACTIVE' and manifest_id<>p_manifest_id;
  update permission_application_manifests set status='ACTIVE',activated_at=now(),activated_by=p_actor_id,version=version+1 where manifest_id=p_manifest_id;
  insert into permission_coverage_evidence(evidence_id,manifest_id,evidence_type,manifest_hash,catalog_revision_id,catalog_content_hash,
    entry_count,covered_entry_count,coverage_percent,source_inventory_revision,details,actor_id,correlation_id,occurred_at)
  values(gen_random_uuid(),p_manifest_id,'MANIFEST_ACTIVATED',m.manifest_hash,m.catalog_revision_id,m.catalog_content_hash,
    m.entry_count,m.covered_entry_count,m.coverage_percent,m.source_inventory_revision,
    jsonb_build_object('auditReason',p_audit_reason,'runtimeDriftBlockers',0),p_actor_id,p_correlation_id,now());
end $$;

-- IamUserController gained mandatory audit-reason enforcement. Refresh all of
-- its generated source descriptors so runtime drift remains fail closed.
update permission_entry_point_inventory
set source_hash='3cd12f234dac8d1eb94a9a004f8f0ed6fbed6f134436a7295a2fe28a13559bea',
    manifest_revision='p1-iam-user-lifecycle-source-inventory-2026-08-02',
    last_verified_at=now(),
    updated_at=now(),
    updated_by='p1-user-lifecycle-migration',
    version=version+1
where source_ref like 'ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/IamUserController.java#%';

insert into permission_entry_point_inventory(
 entry_point_id,entry_point_type,application_id,owner_module,display_name,route_pattern,http_method,
 authority_state,target_permission_code,legacy_authority_type,legacy_authorities,resource_type,resource_resolver_id,
 exemption_reason,migration_deadline,manifest_revision,source_ref,source_hash,last_verified_at,created_by,updated_by)
values(
 'REST:POST:/api/identity/user-onboarding','REST','control-plane-app','iam-api',
 'IamUserLifecycleController.onboard','/api/identity/user-onboarding','POST','TARGET_ONLY',
 'identity.user.create',null,'[]'::jsonb,'IDENTITY','NONE',null,null,
 'p1-iam-user-lifecycle-source-inventory-2026-08-02',
 'ai-event-gateway-core/iam-api/src/main/java/com/opensocket/aievent/core/iam/api/controller/IamUserLifecycleController.java#onboard',
 'd72101512527802195d7ef67b564441a743691092ca3bc9339d5600a008021b5',now(),
 'p1-user-lifecycle-migration','p1-user-lifecycle-migration')
on conflict(entry_point_id) do update set
 entry_point_type=excluded.entry_point_type,application_id=excluded.application_id,owner_module=excluded.owner_module,
 display_name=excluded.display_name,route_pattern=excluded.route_pattern,http_method=excluded.http_method,
 authority_state=excluded.authority_state,target_permission_code=excluded.target_permission_code,
 legacy_authority_type=excluded.legacy_authority_type,legacy_authorities=excluded.legacy_authorities,
 resource_type=excluded.resource_type,resource_resolver_id=excluded.resource_resolver_id,
 exemption_reason=excluded.exemption_reason,migration_deadline=excluded.migration_deadline,
 manifest_revision=excluded.manifest_revision,source_ref=excluded.source_ref,source_hash=excluded.source_hash,
 last_verified_at=excluded.last_verified_at,updated_at=now(),updated_by=excluded.updated_by,
 version=permission_entry_point_inventory.version+1;

insert into permission_application_manifests(
 manifest_id,application_id,environment,build_version,manifest_revision,schema_version,manifest_hash,
 catalog_revision_id,catalog_revision_code,catalog_content_hash,source_inventory_revision,entry_count,
 protected_entry_count,covered_entry_count,coverage_percent,target_permission_count,legacy_authority_count,
 exempt_count,delegated_count,uncovered_count,status,registered_at,registered_by,activated_at,activated_by,version)
select
 '00000000-0000-0000-0000-000000010401'::uuid,'control-plane-app','default',
 '0.8.2-SNAPSHOT-p1-user-lifecycle','p1-control-plane-permission-manifest-2026-08-02',1,
 'sha256:'||repeat('0',64),r.revision_id,r.revision_code,r.content_hash,
 'p1-iam-user-lifecycle-source-inventory-2026-08-02',
 count(*)::integer,
 count(*) filter(where i.entry_point_type not in('COMMAND','QUERY'))::integer,
 count(*)::integer,100.0000,
 count(*) filter(where i.target_permission_code is not null and i.entry_point_type not in('COMMAND','QUERY'))::integer,
 count(*) filter(where i.entry_point_type not in('COMMAND','QUERY') and i.authority_state<>'EXEMPT' and i.target_permission_code is null)::integer,
 count(*) filter(where i.authority_state='EXEMPT')::integer,
 count(*) filter(where i.entry_point_type in('COMMAND','QUERY'))::integer,
 0,'REGISTERING',now(),'p1-user-lifecycle-migration',null,null,1
from permission_entry_point_inventory i
cross join permission_catalog_active_revision a
join permission_catalog_revisions r on r.revision_id=a.revision_id and r.status='PUBLISHED'
where a.singleton_id='ACTIVE' and i.application_id='control-plane-app'
group by r.revision_id,r.revision_code,r.content_hash
on conflict(application_id,environment,manifest_revision) do nothing;

insert into permission_application_manifest_entries(
 manifest_id,entry_point_id,entry_point_type,owner_module,display_name,route_pattern,http_method,authority_state,
 protection_mode,coverage_status,permission_code,legacy_authorities,resource_type,resource_resolver_id,scope_required,
 exemption_reason,source_ref,source_hash,descriptor_hash)
select '00000000-0000-0000-0000-000000010401'::uuid,i.entry_point_id,i.entry_point_type,i.owner_module,i.display_name,
 i.route_pattern,i.http_method,i.authority_state,
 case when i.entry_point_type in('COMMAND','QUERY') then 'INTERNAL_DELEGATED'
      when i.authority_state='EXEMPT' then 'EXEMPT'
      when i.target_permission_code is not null and i.authority_state='DUAL_SHADOW' then 'DUAL_SHADOW'
      when i.target_permission_code is not null then 'TARGET_PERMISSION'
      else 'LEGACY_AUTHORITY' end,
 case when i.entry_point_type in('COMMAND','QUERY') then 'DELEGATED'
      when i.authority_state='EXEMPT' then 'EXEMPT' else 'COVERED' end,
 i.target_permission_code,i.legacy_authorities,i.resource_type,i.resource_resolver_id,
 (i.target_permission_code is not null and i.resource_type not in('IDENTITY','SECURITY','AUDIT','INSTANCE','PERMISSION','GLOBAL','INTERNAL_MESSAGE')),
 i.exemption_reason,i.source_ref,i.source_hash,
 encode(sha256(convert_to(
   coalesce(i.entry_point_id,'')||'|'||coalesce(i.entry_point_type,'')||'|'||coalesce(i.application_id,'')||'|'||
   coalesce(i.owner_module,'')||'|'||coalesce(i.display_name,'')||'|'||coalesce(i.route_pattern,'')||'|'||
   coalesce(i.http_method,'')||'|'||coalesce(i.authority_state,'')||'|'||coalesce(i.target_permission_code,'')||'|'||
   coalesce(i.legacy_authority_type,'')||'|'||coalesce(i.resource_type,'')||'|'||coalesce(i.resource_resolver_id,'')||'|'||
   coalesce(i.exemption_reason,'')||'|'||coalesce(i.source_ref,'')||'|'||coalesce(i.source_hash,'')||'|'||
   (case when i.entry_point_type in('COMMAND','QUERY') then 'INTERNAL_DELEGATED'
         when i.authority_state='EXEMPT' then 'EXEMPT'
         when i.target_permission_code is not null and i.authority_state='DUAL_SHADOW' then 'DUAL_SHADOW'
         when i.target_permission_code is not null then 'TARGET_PERMISSION' else 'LEGACY_AUTHORITY' end)||'|'||
   (case when i.entry_point_type in('COMMAND','QUERY') then 'DELEGATED'
         when i.authority_state='EXEMPT' then 'EXEMPT' else 'COVERED' end)||'|'||
   (case when i.target_permission_code is not null and i.resource_type not in('IDENTITY','SECURITY','AUDIT','INSTANCE','PERMISSION','GLOBAL','INTERNAL_MESSAGE') then 'true' else 'false' end),
   'UTF8')),'hex')
from permission_entry_point_inventory i
where i.application_id='control-plane-app'
  and exists(select 1 from permission_application_manifests m
             where m.manifest_id='00000000-0000-0000-0000-000000010401'::uuid and m.status='REGISTERING')
on conflict(manifest_id,entry_point_id) do nothing;

update permission_application_manifests m
set manifest_hash=(
      select 'sha256:'||encode(sha256(convert_to(
        coalesce(string_agg(e.entry_point_id||'|'||e.descriptor_hash,E'\n' order by e.entry_point_id),''),'UTF8')),'hex')
      from permission_application_manifest_entries e where e.manifest_id=m.manifest_id),
    version=m.version+1
where m.manifest_id='00000000-0000-0000-0000-000000010401'::uuid
  and m.status='REGISTERING';

select phase5h_activate_manifest(
 '00000000-0000-0000-0000-000000010401'::uuid,
 'p1-user-lifecycle-migration',
 'p1-user-lifecycle-migration',
 'Activate P1 IAM User Lifecycle permission manifest after exact source inventory registration');
