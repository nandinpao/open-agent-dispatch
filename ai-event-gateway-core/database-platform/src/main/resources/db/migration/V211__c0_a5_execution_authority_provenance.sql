-- C0-A5 — Execution Authority Provenance.
-- DispatchRequest now carries an immutable snapshot of the execution authority that created it.
-- Current A0-R7 requests therefore fail closed when canonical authority evidence disappears; absence is never reinterpreted as legacy.

alter table dispatch_requests add column if not exists execution_authority_version varchar(32);
alter table dispatch_requests add column if not exists canonical_execution_assignment_id varchar(180);
alter table dispatch_requests add column if not exists authority_provenance varchar(32);

-- Backfill from the compatibility assignment authority introduced by V206.
update dispatch_requests dr
   set execution_authority_version = case when ta.execution_authority_version='A0-R7-V206' then 'A0-R7-V206' else 'LEGACY' end,
       canonical_execution_assignment_id = case when ta.execution_authority_version='A0-R7-V206' then ta.canonical_execution_assignment_id else null end,
       authority_provenance = case when ta.execution_authority_version='A0-R7-V206' then 'A0_R7_CANONICAL' else 'LEGACY_COMPATIBILITY' end
  from task_assignments ta
 where ta.tenant_id=dr.tenant_id
   and ta.assignment_id=dr.assignment_id
   and (dr.execution_authority_version is null or dr.authority_provenance is null);

-- Referential-integrity history should make this fallback unnecessary, but never leave an unclassified request.
update dispatch_requests
   set execution_authority_version='LEGACY',
       canonical_execution_assignment_id=null,
       authority_provenance='LEGACY_COMPATIBILITY'
 where execution_authority_version is null or authority_provenance is null;

alter table dispatch_requests alter column execution_authority_version set not null;
alter table dispatch_requests alter column authority_provenance set not null;

alter table dispatch_requests drop constraint if exists ck_dispatch_authority_provenance_c0a5;
alter table dispatch_requests add constraint ck_dispatch_authority_provenance_c0a5 check (
    (authority_provenance='LEGACY_COMPATIBILITY'
        and execution_authority_version='LEGACY'
        and canonical_execution_assignment_id is null)
    or
    (authority_provenance='A0_R7_CANONICAL'
        and execution_authority_version='A0-R7-V206'
        and canonical_execution_assignment_id is not null)
);

create index if not exists idx_dispatch_requests_authority_provenance_c0a5
    on dispatch_requests(tenant_id,authority_provenance,canonical_execution_assignment_id)
    where authority_provenance='A0_R7_CANONICAL';

create or replace function prevent_dispatch_authority_provenance_mutation_c0a5() returns trigger language plpgsql as $$
begin
  if old.execution_authority_version is distinct from new.execution_authority_version
     or old.canonical_execution_assignment_id is distinct from new.canonical_execution_assignment_id
     or old.authority_provenance is distinct from new.authority_provenance then
    raise exception 'C0_A5_DISPATCH_AUTHORITY_PROVENANCE_IMMUTABLE' using errcode='55000';
  end if;
  return new;
end $$;

drop trigger if exists trg_dispatch_authority_provenance_immutable_c0a5 on dispatch_requests;
create trigger trg_dispatch_authority_provenance_immutable_c0a5
before update of execution_authority_version,canonical_execution_assignment_id,authority_provenance on dispatch_requests
for each row execute function prevent_dispatch_authority_provenance_mutation_c0a5();

comment on column dispatch_requests.execution_authority_version is
'C0-A5 immutable DispatchRequest authority snapshot. LEGACY or A0-R7-V206; never inferred from row visibility at send time.';
comment on column dispatch_requests.canonical_execution_assignment_id is
'C0-A5 immutable canonical ExecutionAssignment pointer for A0_R7_CANONICAL requests; NULL for explicit legacy compatibility requests.';
comment on column dispatch_requests.authority_provenance is
'C0-A5 authority discriminator: LEGACY_COMPATIBILITY or A0_R7_CANONICAL. Current authority missing evidence must fail closed.';
