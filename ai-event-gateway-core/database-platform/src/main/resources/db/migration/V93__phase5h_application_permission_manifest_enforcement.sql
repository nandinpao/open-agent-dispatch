-- Phase 5H enforcement: immutable manifests/evidence and fail-closed runtime drift.

alter table permission_application_manifests
  add constraint chk_phase5h_manifest_schema check(schema_version=1),
  add constraint chk_phase5h_manifest_hash check(manifest_hash~'^sha256:[0-9a-f]{64}$'),
  add constraint chk_phase5h_catalog_hash check(catalog_content_hash~'^sha256:[0-9a-f]{64}$'),
  add constraint chk_phase5h_manifest_status check(status in('REGISTERING','ACTIVE','SUPERSEDED','REJECTED')),
  add constraint chk_phase5h_manifest_counts check(entry_count>0 and protected_entry_count>=0 and covered_entry_count>=0 and covered_entry_count<=entry_count and uncovered_count>=0),
  add constraint chk_phase5h_manifest_coverage check(coverage_percent>=0 and coverage_percent<=100),
  add constraint chk_phase5h_manifest_active_coverage check(status<>'ACTIVE' or (coverage_percent=100.0000 and covered_entry_count=entry_count and uncovered_count=0)),
  add constraint chk_phase5h_manifest_activation check((status='ACTIVE' and activated_at is not null and activated_by is not null) or status<>'ACTIVE'),
  add constraint chk_phase5h_manifest_version check(version>0);

alter table permission_application_manifest_entries
  add constraint chk_phase5h_manifest_entry_type check(entry_point_type in('REST','COMMAND','QUERY','JOB','EVENT_CONSUMER','WEBHOOK','EXPORT','INTERNAL_API')),
  add constraint chk_phase5h_manifest_authority_state check(authority_state in('LEGACY_ONLY','DUAL_SHADOW','TARGET_READY','TARGET_ONLY','EXEMPT')),
  add constraint chk_phase5h_manifest_protection_mode check(protection_mode in('TARGET_PERMISSION','DUAL_SHADOW','LEGACY_AUTHORITY','EXEMPT','INTERNAL_DELEGATED')),
  add constraint chk_phase5h_manifest_coverage_status check(coverage_status in('COVERED','EXEMPT','DELEGATED')),
  add constraint chk_phase5h_manifest_entry_hashes check(source_hash~'^[0-9a-f]{64}$' and descriptor_hash~'^[0-9a-f]{64}$'),
  add constraint chk_phase5h_manifest_legacy_json check(jsonb_typeof(legacy_authorities)='array'),
  add constraint chk_phase5h_manifest_permission_mode check(protection_mode not in('TARGET_PERMISSION','DUAL_SHADOW') or permission_code is not null),
  add constraint chk_phase5h_manifest_legacy_mode check(protection_mode<>'LEGACY_AUTHORITY' or jsonb_array_length(legacy_authorities)>0),
  add constraint chk_phase5h_manifest_exempt_mode check(protection_mode<>'EXEMPT' or length(trim(coalesce(exemption_reason,'')))>=12),
  add constraint chk_phase5h_manifest_resolver check(not scope_required or resource_resolver_id not in('','NONE'));

alter table permission_coverage_evidence
  add constraint chk_phase5h_evidence_type check(evidence_type in('MANIFEST_REGISTERED','COVERAGE_VERIFIED','MANIFEST_ACTIVATED','RUNTIME_DRIFT_DETECTED','MANIFEST_REJECTED')),
  add constraint chk_phase5h_evidence_hashes check(manifest_hash~'^sha256:[0-9a-f]{64}$' and catalog_content_hash~'^sha256:[0-9a-f]{64}$'),
  add constraint chk_phase5h_evidence_coverage check(coverage_percent>=0 and coverage_percent<=100);

create or replace function phase5h_validate_manifest_entry()
returns trigger language plpgsql as $$
declare parent_status varchar(24);lifecycle_value varchar(32);
begin
  if tg_op='DELETE' then raise exception 'PERMISSION_MANIFEST_ENTRY_IMMUTABLE' using errcode='55000'; end if;
  select status into parent_status from permission_application_manifests where manifest_id=new.manifest_id for update;
  if not found then raise exception 'PERMISSION_MANIFEST_NOT_FOUND' using errcode='23503'; end if;
  if parent_status<>'REGISTERING' then raise exception 'PERMISSION_MANIFEST_ENTRY_IMMUTABLE' using errcode='55000'; end if;
  if new.permission_code is not null then
    select lifecycle into lifecycle_value from permission_definitions
    where permission_code=new.permission_code and active=true and lifecycle in('ACTIVE','DEPRECATED');
    if not found then raise exception 'PERMISSION_MANIFEST_UNKNOWN_OR_RETIRED_PERMISSION:%',new.permission_code using errcode='23503'; end if;
  end if;
  if new.scope_required and new.resource_resolver_id in('','NONE') then
    raise exception 'PERMISSION_MANIFEST_MISSING_RESOURCE_RESOLVER:%',new.entry_point_id using errcode='23514';
  end if;
  return new;
end $$;
drop trigger if exists trg_phase5h_validate_manifest_entry on permission_application_manifest_entries;
create trigger trg_phase5h_validate_manifest_entry before insert or update or delete on permission_application_manifest_entries
for each row execute function phase5h_validate_manifest_entry();

create or replace function phase5h_validate_manifest()
returns trigger language plpgsql as $$
declare active_revision uuid;active_code varchar(128);active_hash varchar(71);
begin
  if tg_op='DELETE' then raise exception 'PERMISSION_MANIFEST_IMMUTABLE' using errcode='55000'; end if;
  select r.revision_id,r.revision_code,r.content_hash into active_revision,active_code,active_hash
  from permission_catalog_active_revision a join permission_catalog_revisions r on r.revision_id=a.revision_id
  where a.singleton_id='ACTIVE' and r.status='PUBLISHED';
  if new.catalog_revision_id<>active_revision or new.catalog_revision_code<>active_code or new.catalog_content_hash<>active_hash then
    raise exception 'PERMISSION_MANIFEST_CATALOG_BINDING_MISMATCH' using errcode='23514';
  end if;
  if tg_op='UPDATE' and old.status in('ACTIVE','SUPERSEDED','REJECTED') then
    if old.status='ACTIVE' and new.status='SUPERSEDED' and
       (to_jsonb(new)-'status'-'activated_at'-'activated_by'-'version')=(to_jsonb(old)-'status'-'activated_at'-'activated_by'-'version') then
      new.version=old.version+1;return new;
    end if;
    raise exception 'PERMISSION_MANIFEST_IMMUTABLE' using errcode='55000';
  end if;
  return new;
end $$;
drop trigger if exists trg_phase5h_validate_manifest on permission_application_manifests;
create trigger trg_phase5h_validate_manifest before insert or update or delete on permission_application_manifests
for each row execute function phase5h_validate_manifest();

create or replace function phase5h_reject_coverage_evidence_mutation()
returns trigger language plpgsql as $$ begin raise exception 'PERMISSION_COVERAGE_EVIDENCE_APPEND_ONLY' using errcode='55000'; end $$;
drop trigger if exists trg_phase5h_coverage_evidence_immutable on permission_coverage_evidence;
create trigger trg_phase5h_coverage_evidence_immutable before update or delete on permission_coverage_evidence
for each row execute function phase5h_reject_coverage_evidence_mutation();

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
    select i.entry_point_id from permission_entry_point_inventory i
    full join permission_application_manifest_entries e on e.manifest_id=p_manifest_id and e.entry_point_id=i.entry_point_id
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

create or replace view permission_manifest_entry_runtime_drift with (security_invoker=true) as
with active_manifest as (
 select * from permission_application_manifests where status='ACTIVE'
), manifest_side as (
 select m.manifest_id,m.application_id,m.environment,e.entry_point_id,e.entry_point_type manifest_entry_point_type,
   i.entry_point_type inventory_entry_point_type,e.owner_module,e.display_name,e.permission_code,e.resource_resolver_id,
   e.scope_required,e.source_ref,e.source_hash manifest_source_hash,i.source_hash inventory_source_hash,e.descriptor_hash,
   p.lifecycle permission_lifecycle,p.active permission_active,
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
     'UTF8')),'hex') expected_descriptor_hash,
   case when i.entry_point_id is null then 'STALE_MANIFEST'
        when e.source_hash is distinct from i.source_hash then 'SOURCE_CHANGED'
        when e.descriptor_hash is distinct from encode(sha256(convert_to(
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
          'UTF8')),'hex') then 'DESCRIPTOR_CHANGED'
        when e.permission_code is not null and p.permission_code is null then 'UNKNOWN_PERMISSION'
        when e.permission_code is not null and (p.active=false or p.lifecycle='RETIRED') then 'RETIRED_PERMISSION'
        when e.scope_required and e.resource_resolver_id in('','NONE') then 'MISSING_RESOLVER'
        else 'MATCH' end drift_status
 from active_manifest m
 join permission_application_manifest_entries e on e.manifest_id=m.manifest_id
 left join permission_entry_point_inventory i on i.entry_point_id=e.entry_point_id
 left join permission_definitions p on p.permission_code=e.permission_code
), inventory_only as (
 select m.manifest_id,m.application_id,m.environment,i.entry_point_id,null::varchar manifest_entry_point_type,
   i.entry_point_type inventory_entry_point_type,i.owner_module,i.display_name,null::varchar permission_code,
   i.resource_resolver_id,false scope_required,i.source_ref,null::varchar manifest_source_hash,i.source_hash inventory_source_hash,
   null::varchar descriptor_hash,null::varchar permission_lifecycle,null::boolean permission_active,null::varchar expected_descriptor_hash,
   'UNREGISTERED_SOURCE'::text drift_status
 from active_manifest m cross join permission_entry_point_inventory i
 where not exists(select 1 from permission_application_manifest_entries e where e.manifest_id=m.manifest_id and e.entry_point_id=i.entry_point_id)
)
select *,case drift_status when 'MATCH' then false else true end blocker from manifest_side
union all
select *,true blocker from inventory_only;

create or replace view permission_application_manifest_readiness with (security_invoker=true) as
select m.*,
 count(d.entry_point_id) filter(where d.drift_status='MATCH') matching_entries,
 count(d.entry_point_id) filter(where d.drift_status='SOURCE_CHANGED') source_changed,
 count(d.entry_point_id) filter(where d.drift_status='DESCRIPTOR_CHANGED') descriptor_changed,
 count(d.entry_point_id) filter(where d.drift_status='UNREGISTERED_SOURCE') unregistered_source,
 count(d.entry_point_id) filter(where d.drift_status='STALE_MANIFEST') stale_manifest,
 count(d.entry_point_id) filter(where d.drift_status='UNKNOWN_PERMISSION') unknown_permission,
 count(d.entry_point_id) filter(where d.drift_status='RETIRED_PERMISSION') retired_permission,
 count(d.entry_point_id) filter(where d.drift_status='MISSING_RESOLVER') missing_resolver,
 count(d.entry_point_id) filter(where d.blocker) runtime_drift_blockers
from permission_application_manifests m
left join permission_manifest_entry_runtime_drift d on d.manifest_id=m.manifest_id
group by m.manifest_id;

alter table permission_application_manifests enable row level security;alter table permission_application_manifests force row level security;
create policy phase5h_manifest_instance_scope on permission_application_manifests using(iam_current_tenant_id()='INSTANCE') with check(iam_current_tenant_id()='INSTANCE');
alter table permission_application_manifest_entries enable row level security;alter table permission_application_manifest_entries force row level security;
create policy phase5h_manifest_entry_instance_scope on permission_application_manifest_entries using(iam_current_tenant_id()='INSTANCE') with check(iam_current_tenant_id()='INSTANCE');
alter table permission_coverage_evidence enable row level security;alter table permission_coverage_evidence force row level security;
create policy phase5h_coverage_evidence_instance_scope on permission_coverage_evidence using(iam_current_tenant_id()='INSTANCE') with check(iam_current_tenant_id()='INSTANCE');
