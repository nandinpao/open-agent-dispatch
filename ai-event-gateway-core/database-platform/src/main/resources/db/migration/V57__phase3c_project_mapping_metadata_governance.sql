-- Phase 3C: immutable Project Mapping versions and Provider Metadata cache.
-- Existing enabled mappings retain service continuity through an explicit STALE legacy metadata snapshot.

alter table integration_project_mappings add column if not exists lifecycle_status varchar(32) not null default 'DRAFT';
alter table integration_project_mappings add column if not exists mapping_version integer not null default 1;
alter table integration_project_mappings add column if not exists summary_template text not null default '{{task.title}}';
alter table integration_project_mappings add column if not exists description_template text not null default '{{task.description}}';
alter table integration_project_mappings add column if not exists required_fields_json jsonb not null default '[]'::jsonb;
alter table integration_project_mappings add column if not exists custom_field_mappings_json jsonb not null default '{}'::jsonb;
alter table integration_project_mappings add column if not exists transition_mappings_json jsonb not null default '{}'::jsonb;
alter table integration_project_mappings add column if not exists comment_policy varchar(64) not null default 'APPEND_ONLY';
alter table integration_project_mappings add column if not exists link_policy varchar(64) not null default 'CANONICAL_ONLY';
alter table integration_project_mappings add column if not exists metadata_snapshot_id varchar(160);
alter table integration_project_mappings add column if not exists metadata_schema_hash varchar(128);
alter table integration_project_mappings add column if not exists validated_at timestamptz;
alter table integration_project_mappings add column if not exists published_at timestamptz;
alter table integration_project_mappings add column if not exists supersedes_mapping_version integer;

create table if not exists integration_provider_metadata_snapshots(
 tenant_id varchar(64) not null,
 snapshot_id varchar(160) not null,
 connection_id varchar(128) not null,
 mapping_id varchar(128) not null,
 provider_project_id varchar(255),
 provider_project_key varchar(255),
 payload_json jsonb not null,
 etag varchar(512),
 provider_last_modified varchar(512),
 schema_hash varchar(128) not null,
 metadata_version integer not null,
 cache_status varchar(32) not null,
 probed_at timestamptz not null,
 expires_at timestamptz not null,
 provider_summary text,
 correlation_id varchar(160),
 primary key(tenant_id,snapshot_id),
 unique(tenant_id,mapping_id,metadata_version)
);
alter table integration_provider_metadata_snapshots drop constraint if exists ck_provider_metadata_cache_status_p3c;
alter table integration_provider_metadata_snapshots add constraint ck_provider_metadata_cache_status_p3c
 check(cache_status in('FRESH','STALE','DRIFTED','FAILED'));
create index if not exists idx_provider_metadata_cache_p3c
 on integration_provider_metadata_snapshots(tenant_id,mapping_id,cache_status,expires_at,metadata_version desc);

create table if not exists integration_project_mapping_versions(
 tenant_id varchar(64) not null,
 mapping_id varchar(128) not null,
 mapping_version integer not null,
 lifecycle_status varchar(32) not null,
 configuration_json jsonb not null,
 configuration_hash varchar(128) not null,
 metadata_snapshot_id varchar(160),
 metadata_schema_hash varchar(128),
 created_by varchar(160) not null,
 created_at timestamptz not null,
 primary key(tenant_id,mapping_id,mapping_version)
);

-- Preserve pre-Phase-3C enabled mappings while making their unverified schema explicit.
update integration_project_mappings
set lifecycle_status=case
      when enabled then 'ACTIVE'
      when mapping_status='VALID' then 'VALID'
      when mapping_status='DISABLED' then 'DISABLED'
      else 'DRAFT'
    end,
    metadata_snapshot_id=case when enabled and metadata_snapshot_id is null
      then 'legacy-p3c-'||substr(md5(tenant_id||':'||mapping_id),1,32)
      else metadata_snapshot_id end,
    metadata_schema_hash=case when enabled and metadata_schema_hash is null
      then 'legacy-'||md5(coalesce(external_project_id,'')||':'||coalesce(external_issue_type,''))
      else metadata_schema_hash end,
    validated_at=case when enabled and validated_at is null then updated_at else validated_at end,
    published_at=case when enabled and published_at is null then updated_at else published_at end;

insert into integration_provider_metadata_snapshots(
 tenant_id,snapshot_id,connection_id,mapping_id,provider_project_id,provider_project_key,
 payload_json,etag,provider_last_modified,schema_hash,metadata_version,cache_status,
 probed_at,expires_at,provider_summary,correlation_id)
select m.tenant_id,m.metadata_snapshot_id,m.connection_id,m.mapping_id,m.external_project_id,m.external_project_key,
 jsonb_build_object(
   'tenantId',m.tenant_id,
   'snapshotId',m.metadata_snapshot_id,
   'connectionId',m.connection_id,
   'mappingId',m.mapping_id,
   'providerProjectId',m.external_project_id,
   'providerProjectKey',m.external_project_key,
   'projects',jsonb_build_array(jsonb_build_object(
       'projectId',m.external_project_id,'projectKey',m.external_project_key,
       'displayName',coalesce(m.external_project_key,m.external_project_id),'accessible',true)),
   'issueTypes',case when m.external_issue_type is null then '[]'::jsonb else
       jsonb_build_array(jsonb_build_object('issueTypeId',m.external_issue_type,
       'issueTypeKey',m.external_issue_type,'displayName',m.external_issue_type,
       'requiredFieldIds','[]'::jsonb)) end,
   'fields','[]'::jsonb,'transitions','[]'::jsonb,'linkTypes','[]'::jsonb,
   'users','[]'::jsonb,'groups','[]'::jsonb,
   'permissions',jsonb_build_object('READ','NOT_TESTED','CREATE','NOT_TESTED'),
   'etag',null,'lastModified',null,'schemaHash',m.metadata_schema_hash,
   'metadataVersion',1,'cacheStatus','STALE',
   'probedAt',to_char(coalesce(m.updated_at,now()),'YYYY-MM-DD"T"HH24:MI:SSOF'),
   'expiresAt',to_char(now(),'YYYY-MM-DD"T"HH24:MI:SSOF'),
   'providerSummary','Migrated legacy mapping; live metadata probe is required.',
   'correlationId','phase3c-legacy-backfill'),
 null,null,m.metadata_schema_hash,1,'STALE',coalesce(m.updated_at,now()),now(),
 'Migrated legacy mapping; live metadata probe is required.','phase3c-legacy-backfill'
from integration_project_mappings m
where m.enabled=true and m.metadata_snapshot_id is not null
on conflict(tenant_id,snapshot_id) do nothing;

insert into integration_project_mapping_versions(
 tenant_id,mapping_id,mapping_version,lifecycle_status,configuration_json,configuration_hash,
 metadata_snapshot_id,metadata_schema_hash,created_by,created_at)
select m.tenant_id,m.mapping_id,m.mapping_version,m.lifecycle_status,
 jsonb_build_object(
   'project',m.external_project_id,'projectKey',m.external_project_key,
   'issueType',m.external_issue_type,'trackerId',m.external_tracker_id,
   'summaryTemplate',m.summary_template,'descriptionTemplate',m.description_template,
   'requiredFields',m.required_fields_json,'customFields',m.custom_field_mappings_json,
   'transitions',m.transition_mappings_json,'commentPolicy',m.comment_policy,
   'linkPolicy',m.link_policy),
 'legacy-'||md5(m.tenant_id||':'||m.mapping_id||':'||m.mapping_version::text),
 m.metadata_snapshot_id,m.metadata_schema_hash,'phase3c-migration',coalesce(m.published_at,m.updated_at,now())
from integration_project_mappings m
where m.lifecycle_status in('ACTIVE','DEPRECATED')
on conflict(tenant_id,mapping_id,mapping_version) do nothing;

alter table integration_project_mappings drop constraint if exists ck_integration_mapping_lifecycle_p3c;
alter table integration_project_mappings add constraint ck_integration_mapping_lifecycle_p3c
 check(lifecycle_status in('DRAFT','VALIDATING','VALID','ACTIVE','DEPRECATED','DISABLED'));
alter table integration_project_mappings drop constraint if exists ck_integration_mapping_active_p3c;
alter table integration_project_mappings add constraint ck_integration_mapping_active_p3c
 check((lifecycle_status='ACTIVE' and enabled=true and metadata_snapshot_id is not null and metadata_schema_hash is not null)
       or (lifecycle_status<>'ACTIVE' and enabled=false));
create index if not exists idx_integration_mapping_lifecycle_p3c
 on integration_project_mappings(tenant_id,connection_id,lifecycle_status,mapping_version desc);

create or replace function prevent_project_mapping_version_mutation_p3c()
returns trigger language plpgsql as $$
begin
 raise exception 'PROJECT_MAPPING_VERSION_IMMUTABLE';
end $$;
drop trigger if exists trg_project_mapping_version_immutable_p3c on integration_project_mapping_versions;
create trigger trg_project_mapping_version_immutable_p3c
 before update or delete on integration_project_mapping_versions
 for each row execute function prevent_project_mapping_version_mutation_p3c();

create or replace function protect_published_project_mapping_p3c()
returns trigger language plpgsql as $$
begin
 if old.lifecycle_status in ('ACTIVE','DEPRECATED') then
   if (new.connection_id,new.department_id,new.group_id,new.service_domain_id,new.source_system_id,new.task_type,
       new.external_project_id,new.external_project_key,new.external_issue_type,new.external_tracker_id,
       new.read_principal_id,new.create_principal_id,new.comment_principal_id,new.update_principal_id,
       new.relation_principal_id,new.webhook_principal_id,new.permission_profile_id,new.context_policy_id,
       new.result_sharing_policy_id,new.mapping_version,new.summary_template,new.description_template,
       new.required_fields_json,new.custom_field_mappings_json,new.transition_mappings_json,
       new.comment_policy,new.link_policy,new.metadata_snapshot_id,new.metadata_schema_hash)
      is distinct from
      (old.connection_id,old.department_id,old.group_id,old.service_domain_id,old.source_system_id,old.task_type,
       old.external_project_id,old.external_project_key,old.external_issue_type,old.external_tracker_id,
       old.read_principal_id,old.create_principal_id,old.comment_principal_id,old.update_principal_id,
       old.relation_principal_id,old.webhook_principal_id,old.permission_profile_id,old.context_policy_id,
       old.result_sharing_policy_id,old.mapping_version,old.summary_template,old.description_template,
       old.required_fields_json,old.custom_field_mappings_json,old.transition_mappings_json,
       old.comment_policy,old.link_policy,old.metadata_snapshot_id,old.metadata_schema_hash) then
     raise exception 'PUBLISHED_MAPPING_IMMUTABLE_CREATE_NEW_VERSION';
   end if;
   if not (old.lifecycle_status='ACTIVE' and new.lifecycle_status in('ACTIVE','DEPRECATED')
           or old.lifecycle_status='DEPRECATED' and new.lifecycle_status='DEPRECATED') then
     raise exception 'PUBLISHED_MAPPING_LIFECYCLE_INVALID';
   end if;
 end if;
 return new;
end $$;
drop trigger if exists trg_project_mapping_published_immutable_p3c on integration_project_mappings;
create trigger trg_project_mapping_published_immutable_p3c
 before update on integration_project_mappings
 for each row execute function protect_published_project_mapping_p3c();

alter table issue_projection_states add column if not exists project_mapping_version integer not null default 1;
alter table issue_projection_states add column if not exists project_mapping_schema_hash varchar(128);
comment on column issue_projection_states.project_mapping_version is
 'Immutable Project Mapping version used to render this Projection.';
