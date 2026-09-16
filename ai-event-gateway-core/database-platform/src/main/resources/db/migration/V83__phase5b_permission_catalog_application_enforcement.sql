-- Phase 5B enforcement: Draft-only mutation and transactional publication guards.

-- Application pre-checks improve error messages; this partial unique index is the concurrency authority.
create unique index if not exists uq_permission_catalog_single_draft
  on permission_catalog_revisions(status) where status='DRAFT';

alter table permission_catalog_revision_entries
  add constraint ck_permission_catalog_entry_risk_lane
    check(risk_lane in('READ','WRITE','EXPORT','ADMIN','CRITICAL')),
  add constraint ck_permission_catalog_entry_lifecycle
    check(lifecycle in('DRAFT','ACTIVE','DEPRECATED','RETIRED')),
  add constraint ck_permission_catalog_entry_scopes
    check(cardinality(allowed_scope_types)>0),
  add constraint ck_permission_catalog_entry_replacement
    check(replacement_permission_code is null or replacement_permission_code<>permission_code),
  add constraint ck_permission_catalog_entry_retirement
    check((lifecycle<>'DEPRECATED' or deprecated_at is not null)
      and (lifecycle<>'RETIRED' or retired_at is not null)),
  add constraint ck_permission_catalog_entry_version check(version>0),
  add constraint fk_permission_catalog_entry_replacement
    foreign key(revision_id,replacement_permission_code)
    references permission_catalog_revision_entries(revision_id,permission_code)
    deferrable initially deferred;

alter table permission_catalog_revision_aliases
  add constraint ck_permission_catalog_revision_alias_type
    check(alias_type in('RENAMED','LEGACY','COMPATIBILITY')),
  add constraint ck_permission_catalog_revision_alias_window
    check(valid_until is null or valid_until>valid_from),
  add constraint ck_permission_catalog_revision_alias_not_self
    check(alias_code<>canonical_permission_code),
  add constraint ck_permission_catalog_revision_alias_version check(version>0);

create or replace function phase5b_guard_draft_catalog_content()
returns trigger language plpgsql as $$
declare state varchar(24); target_revision uuid;
begin
  target_revision=case when tg_op='DELETE' then old.revision_id else new.revision_id end;
  select status into state from permission_catalog_revisions where revision_id=target_revision;
  if state is distinct from 'DRAFT' then
    raise exception 'PERMISSION_CATALOG_REVISION_NOT_DRAFT' using errcode='55000';
  end if;
  if tg_op='DELETE' then return old; end if;
  return new;
end $$;

drop trigger if exists trg_permission_catalog_revision_entry_draft_only on permission_catalog_revision_entries;
create trigger trg_permission_catalog_revision_entry_draft_only
before insert or update or delete on permission_catalog_revision_entries
for each row execute function phase5b_guard_draft_catalog_content();

drop trigger if exists trg_permission_catalog_revision_alias_draft_only on permission_catalog_revision_aliases;
create trigger trg_permission_catalog_revision_alias_draft_only
before insert or update or delete on permission_catalog_revision_aliases
for each row execute function phase5b_guard_draft_catalog_content();

-- Replace the Phase 5A coarse guard with explicit legal state transitions.
create or replace function phase5a_guard_permission_revision()
returns trigger language plpgsql as $$
begin
  if tg_op='DELETE' then
    raise exception 'PERMISSION_CATALOG_REVISION_IMMUTABLE' using errcode='55000';
  end if;
  if old.status='DRAFT' and new.status='DRAFT' then
    return new;
  end if;
  if old.status='DRAFT' and new.status='PUBLISHED'
     and new.revision_id=old.revision_id and new.revision_code=old.revision_code
     and new.revision_number=old.revision_number and new.description=old.description
     and new.supersedes_revision_id is not distinct from old.supersedes_revision_id
     and new.created_at=old.created_at and new.created_by=old.created_by
     and new.content_hash like 'sha256:%'
     and new.published_at is not null and nullif(new.published_by,'') is not null then
    return new;
  end if;
  if old.status='PUBLISHED' and new.status='SUPERSEDED'
     and new.revision_id=old.revision_id and new.revision_code=old.revision_code
     and new.revision_number=old.revision_number and new.content_hash=old.content_hash
     and new.description=old.description and new.supersedes_revision_id is not distinct from old.supersedes_revision_id
     and new.created_at=old.created_at and new.created_by=old.created_by
     and new.published_at=old.published_at and new.published_by=old.published_by then
    return new;
  end if;
  if old.status='SUPERSEDED' and new.status='RETIRED'
     and new.revision_id=old.revision_id and new.revision_code=old.revision_code
     and new.revision_number=old.revision_number and new.content_hash=old.content_hash
     and new.description=old.description and new.supersedes_revision_id is not distinct from old.supersedes_revision_id
     and new.created_at=old.created_at and new.created_by=old.created_by
     and new.published_at=old.published_at and new.published_by=old.published_by then
    return new;
  end if;
  raise exception 'PERMISSION_CATALOG_REVISION_IMMUTABLE' using errcode='55000';
end $$;

-- The migration-owned Phase 5B revision is now active; close the superseded baseline after
-- installing the legal PUBLISHED -> SUPERSEDED transition guard above.
update permission_catalog_revisions
set status='SUPERSEDED',version=version+1
where revision_id='00000000-0000-0000-0000-000000000001'::uuid and status='PUBLISHED';

create or replace function phase5b_guard_active_permission_projection()
returns trigger language plpgsql as $$
declare publishing_revision text;
begin
  publishing_revision=nullif(current_setting('app.permission_catalog_publish_revision_id',true),'');
  if publishing_revision is null then
    raise exception 'PERMISSION_CATALOG_ACTIVE_PROJECTION_WRITE_FORBIDDEN' using errcode='42501';
  end if;
  if tg_op<>'DELETE' and new.catalog_revision_id::text<>publishing_revision then
    raise exception 'PERMISSION_CATALOG_PUBLICATION_CONTEXT_MISMATCH' using errcode='42501';
  end if;
  if tg_op='DELETE' then return old; end if;
  return new;
end $$;

drop trigger if exists trg_permission_catalog_active_projection_guard on permission_definitions;
create trigger trg_permission_catalog_active_projection_guard
before insert or update or delete on permission_definitions
for each row execute function phase5b_guard_active_permission_projection();

create or replace function phase5b_reject_publication_event_mutation()
returns trigger language plpgsql as $$
begin
  raise exception 'PERMISSION_CATALOG_PUBLICATION_EVENT_IMMUTABLE' using errcode='55000';
end $$;
drop trigger if exists trg_permission_catalog_publication_event_immutable on permission_catalog_publication_events;
create trigger trg_permission_catalog_publication_event_immutable
before update or delete on permission_catalog_publication_events
for each row execute function phase5b_reject_publication_event_mutation();
