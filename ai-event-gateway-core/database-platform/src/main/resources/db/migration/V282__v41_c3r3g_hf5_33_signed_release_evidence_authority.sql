-- V41 C3R3G-HF5.33: Signed Release Evidence Authority.
-- Browser/API candidate creation no longer declares source/build PASS. Trusted automated executors
-- sign candidate-bound evidence that is revalidated through certification, activation, Flow promote,
-- DB lifecycle guards and the pre-network production safety gate.

select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','v41-c3r3g-hf5-33-signed-release-evidence',true);

-- 1. Trust roots are public verification material only. Private keys never enter OpenDispatch.
create table if not exists release_evidence_trusted_executors_v282 (
  tenant_id varchar(64) not null,
  executor_id varchar(180) not null,
  key_id varchar(180) not null,
  display_name varchar(255) not null,
  algorithm varchar(24) not null default 'RS256',
  public_key_pem text not null,
  public_key_fingerprint_sha256 varchar(71) not null,
  allowed_evidence_types varchar(32)[] not null,
  status varchar(24) not null default 'ACTIVE',
  created_by varchar(255) not null,
  created_at timestamptz not null default now(),
  revoked_by varchar(255),
  revoked_at timestamptz,
  revoke_reason text,
  primary key(tenant_id,executor_id,key_id),
  constraint release_executor_algorithm_v282 check(algorithm='RS256'),
  constraint release_executor_status_v282 check(status in('ACTIVE','REVOKED')),
  constraint release_executor_fingerprint_v282 check(public_key_fingerprint_sha256 ~ '^sha256:[0-9a-f]{64}$'),
  constraint release_executor_types_v282 check(allowed_evidence_types <@ array['SOURCE_GATE','BUILD_GATE','RUNTIME_GATE','E2E_GATE']::varchar[] and cardinality(allowed_evidence_types)>0)
);

create table if not exists release_evidence_policy_v282 (
  evidence_type varchar(32) primary key,
  max_ttl_seconds integer not null,
  description text not null,
  constraint release_evidence_policy_type_v282 check(evidence_type in('SOURCE_GATE','BUILD_GATE','RUNTIME_GATE','E2E_GATE')),
  constraint release_evidence_policy_ttl_v282 check(max_ttl_seconds between 300 and 1209600)
);
insert into release_evidence_policy_v282(evidence_type,max_ttl_seconds,description) values
 ('SOURCE_GATE',604800,'Source-control / source-policy gate evidence; maximum validity seven days.'),
 ('BUILD_GATE',604800,'Reproducible build/artifact gate evidence; maximum validity seven days.'),
 ('RUNTIME_GATE',86400,'Runtime safety certification evidence; maximum validity twenty-four hours.'),
 ('E2E_GATE',86400,'Integrated E2E production-path evidence; maximum validity twenty-four hours.')
on conflict(evidence_type) do update set max_ttl_seconds=excluded.max_ttl_seconds,description=excluded.description;

create table if not exists release_signed_evidence_attestations_v282 (
  tenant_id varchar(64) not null,
  attestation_id varchar(180) not null,
  release_candidate_id varchar(180) not null,
  evidence_type varchar(32) not null,
  result varchar(16) not null,
  artifact_sha256 varchar(64) not null,
  environment_ref varchar(255) not null,
  migration_baseline varchar(32) not null,
  migration_head varchar(32) not null,
  migration_set_hash varchar(96) not null,
  pipeline_run_id varchar(255) not null,
  evidence_uri varchar(2048) not null,
  evidence_digest_sha256 varchar(71) not null,
  executor_id varchar(180) not null,
  key_id varchar(180) not null,
  signature_algorithm varchar(24) not null,
  signature_base64 text not null,
  canonical_payload_sha256 varchar(71) not null,
  executed_at timestamptz not null,
  valid_until timestamptz not null,
  verification_status varchar(24) not null,
  verified_at timestamptz not null,
  received_by varchar(255) not null,
  received_at timestamptz not null default now(),
  primary key(tenant_id,attestation_id),
  constraint fk_release_attestation_candidate_v282 foreign key(tenant_id,release_candidate_id) references a0_production_release_candidates_v208(tenant_id,release_candidate_id),
  constraint fk_release_attestation_executor_v282 foreign key(tenant_id,executor_id,key_id) references release_evidence_trusted_executors_v282(tenant_id,executor_id,key_id),
  constraint release_attestation_type_v282 check(evidence_type in('SOURCE_GATE','BUILD_GATE','RUNTIME_GATE','E2E_GATE')),
  constraint release_attestation_result_v282 check(result in('PASS','FAIL')),
  constraint release_attestation_sha_v282 check(artifact_sha256 ~ '^[0-9a-f]{64}$'),
  constraint release_attestation_evidence_digest_v282 check(evidence_digest_sha256 ~ '^sha256:[0-9a-f]{64}$'),
  constraint release_attestation_payload_digest_v282 check(canonical_payload_sha256 ~ '^sha256:[0-9a-f]{64}$'),
  constraint release_attestation_algorithm_v282 check(signature_algorithm='RS256'),
  constraint release_attestation_verification_v282 check(verification_status='VERIFIED'),
  constraint release_attestation_time_v282 check(valid_until>executed_at)
);
create index if not exists idx_release_attestation_candidate_v282 on release_signed_evidence_attestations_v282(tenant_id,release_candidate_id,evidence_type,executed_at desc);
create index if not exists idx_release_attestation_pipeline_v282 on release_signed_evidence_attestations_v282(tenant_id,pipeline_run_id);

-- Trust key material is immutable. Rotation means register a new key_id and revoke the old key.
create or replace function release_executor_identity_immutable_v282() returns trigger language plpgsql as $$
begin
  if old.tenant_id is distinct from new.tenant_id or old.executor_id is distinct from new.executor_id or old.key_id is distinct from new.key_id
     or old.algorithm is distinct from new.algorithm or old.public_key_pem is distinct from new.public_key_pem
     or old.public_key_fingerprint_sha256 is distinct from new.public_key_fingerprint_sha256
     or old.allowed_evidence_types is distinct from new.allowed_evidence_types or old.created_at is distinct from new.created_at
     or old.created_by is distinct from new.created_by then
    raise exception 'RELEASE_TRUSTED_EXECUTOR_IDENTITY_IMMUTABLE';
  end if;
  if old.status='REVOKED' and new.status<>'REVOKED' then raise exception 'RELEASE_TRUSTED_EXECUTOR_REVOCATION_TERMINAL'; end if;
  return new;
end $$;
drop trigger if exists trg_release_executor_identity_immutable_v282 on release_evidence_trusted_executors_v282;
create trigger trg_release_executor_identity_immutable_v282 before update on release_evidence_trusted_executors_v282
for each row execute function release_executor_identity_immutable_v282();

drop trigger if exists trg_release_attestation_append_only_v282 on release_signed_evidence_attestations_v282;
create trigger trg_release_attestation_append_only_v282 before update or delete on release_signed_evidence_attestations_v282
for each row execute function prevent_a0release_append_only_mutation();

alter table release_evidence_trusted_executors_v282 enable row level security;
drop policy if exists tenant_isolation on release_evidence_trusted_executors_v282;
create policy tenant_isolation on release_evidence_trusted_executors_v282 using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());
alter table release_signed_evidence_attestations_v282 enable row level security;
drop policy if exists tenant_isolation on release_signed_evidence_attestations_v282;
create policy tenant_isolation on release_signed_evidence_attestations_v282 using(tenant_id=iam_current_tenant_id()) with check(tenant_id=iam_current_tenant_id());

-- 2. Latest evidence is authoritative even when FAIL/expired/revoked; an older PASS never resurfaces.
create or replace view release_signed_evidence_latest_v282 as
with ranked as (
  select a.*,row_number() over(partition by a.tenant_id,a.release_candidate_id,a.evidence_type order by a.executed_at desc,a.verified_at desc,a.attestation_id desc) rn
    from release_signed_evidence_attestations_v282 a
), latest as (
  select * from ranked where rn=1
)
select l.*,
       e.status executor_status,
       case
         when e.status<>'ACTIVE' then 'UNTRUSTED'
         when l.executed_at>now()+interval '5 minutes' then 'INVALID_TIME'
         when l.valid_until<=now() then 'EXPIRED'
         when l.result='PASS' then 'PASS'
         else 'FAIL'
       end effective_status
  from latest l
  join release_evidence_trusted_executors_v282 e on e.tenant_id=l.tenant_id and e.executor_id=l.executor_id and e.key_id=l.key_id;

create or replace view release_candidate_signed_evidence_status_v282 as
select c.tenant_id,c.release_candidate_id,
       coalesce(max(l.effective_status) filter(where l.evidence_type='SOURCE_GATE'),'MISSING') source_gate_status,
       coalesce(max(l.effective_status) filter(where l.evidence_type='BUILD_GATE'),'MISSING') build_gate_status,
       coalesce(max(l.effective_status) filter(where l.evidence_type='RUNTIME_GATE'),'MISSING') runtime_gate_status,
       coalesce(max(l.effective_status) filter(where l.evidence_type='E2E_GATE'),'MISSING') e2e_gate_status,
       max(l.attestation_id) filter(where l.evidence_type='SOURCE_GATE') source_attestation_id,
       max(l.attestation_id) filter(where l.evidence_type='BUILD_GATE') build_attestation_id,
       max(l.attestation_id) filter(where l.evidence_type='RUNTIME_GATE') runtime_attestation_id,
       max(l.attestation_id) filter(where l.evidence_type='E2E_GATE') e2e_attestation_id,
       case when count(*) filter(where l.effective_status='PASS' and l.evidence_type in('SOURCE_GATE','BUILD_GATE','RUNTIME_GATE','E2E_GATE'))=4 then true else false end all_required_pass,
       'sha256:'||encode(sha256(convert_to(coalesce(string_agg(l.evidence_type||':'||l.attestation_id,'|' order by l.evidence_type),'MISSING'),'UTF8')),'hex') evidence_set_hash
  from a0_production_release_candidates_v208 c
  left join release_signed_evidence_latest_v282 l on l.tenant_id=c.tenant_id and l.release_candidate_id=c.release_candidate_id
 group by c.tenant_id,c.release_candidate_id;

-- 3. Replace compatibility client PASS fields with signed evidence in all DB/runtime gates.
create or replace function a0release_guard_candidate_transition() returns trigger language plpgsql as $$
declare r8_status text; cutover_status text; signed_pass boolean;
begin
  if TG_OP='INSERT' then
    if new.status<>'DRAFT' then raise exception 'A0_RELEASE_CANDIDATE_MUST_START_DRAFT'; end if;
    if new.source_gate_status<>'NOT_RUN' or new.build_status<>'NOT_RUN' then raise exception 'RELEASE_BROWSER_GATE_CLAIMS_NOT_ACCEPTED'; end if;
    return new;
  end if;
  if new.tenant_id<>old.tenant_id or new.release_candidate_id<>old.release_candidate_id or new.created_at<>old.created_at then raise exception 'A0_RELEASE_CANDIDATE_IDENTITY_IMMUTABLE'; end if;
  if old.status='REVOKED' and new.status<>'REVOKED' then raise exception 'A0_RELEASE_REVOKED_IS_TERMINAL'; end if;
  if old.status='DRAFT' and new.status not in('DRAFT','CERTIFIED','REVOKED') then raise exception 'A0_RELEASE_INVALID_CANDIDATE_TRANSITION'; end if;
  if old.status='CERTIFIED' and new.status not in('CERTIFIED','ACTIVE','REVOKED') then raise exception 'A0_RELEASE_INVALID_CANDIDATE_TRANSITION'; end if;
  if old.status='ACTIVE' and new.status not in('ACTIVE','REVOKED') then raise exception 'A0_RELEASE_INVALID_CANDIDATE_TRANSITION'; end if;
  if new.status in('CERTIFIED','ACTIVE') and old.status is distinct from new.status then
    select gate_status into r8_status from a0_r8_release_gate_v207 where tenant_id=new.tenant_id;
    select gate_status into cutover_status from a0_release_cutover_gate_v208 where tenant_id=new.tenant_id;
    select all_required_pass into signed_pass from release_candidate_signed_evidence_status_v282 where tenant_id=new.tenant_id and release_candidate_id=new.release_candidate_id;
    if coalesce(r8_status,'NOT_CERTIFIED')<>'PASS' then raise exception 'A0_RELEASE_R8_GATE_NOT_PASS'; end if;
    if coalesce(cutover_status,'NOT_CERTIFIED')<>'PASS' then raise exception 'A0_RELEASE_E2E_CUTOVER_GATE_NOT_PASS'; end if;
    if coalesce(signed_pass,false)<>true then raise exception 'RELEASE_SIGNED_EVIDENCE_GATE_NOT_PASS'; end if;
  end if;
  return new;
end $$;

-- Production Foundation projection also fails closed when the ACTIVE candidate's signed evidence
-- expires, is superseded by FAIL, or its trusted executor key is revoked.
create or replace view a0_production_foundation_gate_v208 as
with tenants as (
  select tenant_id from a0_r8_acceptance_runs_v207
  union select tenant_id from a0_release_cutover_runs_v208
  union select tenant_id from a0_production_release_candidates_v208
  union select tenant_id from flow_routing_migration_state
), flow_health as (
  select tenant_id,
         count(*) filter(where migration_state='NEW_AUTHORITATIVE' and production_release_candidate_id is null) controlled_live_unbound_flows,
         count(*) filter(where migration_state='NEW_AUTHORITATIVE' and production_release_candidate_id is not null) production_bound_flows,
         count(*) filter(where migration_state='NEW_AUTHORITATIVE' and production_release_candidate_id is not null and not exists(
           select 1 from a0_production_release_candidates_v208 c where c.tenant_id=flow_routing_migration_state.tenant_id and c.release_candidate_id=flow_routing_migration_state.production_release_candidate_id and c.status='ACTIVE')) invalid_production_bound_flows
    from flow_routing_migration_state group by tenant_id
), active_candidate as (
  select c.tenant_id,c.release_candidate_id,c.artifact_sha256,c.environment_ref,c.activated_at,e.all_required_pass,e.evidence_set_hash
    from a0_production_release_candidates_v208 c
    left join release_candidate_signed_evidence_status_v282 e on e.tenant_id=c.tenant_id and e.release_candidate_id=c.release_candidate_id
   where c.status='ACTIVE'
)
select t.tenant_id,
       coalesce(r.gate_status,'NOT_CERTIFIED') r8_gate_status,coalesce(r.required_count,20) r8_required_count,coalesce(r.passed_count,0) r8_passed_count,coalesce(r.blocking_count,20) r8_blocking_count,
       coalesce(c.gate_status,'NOT_CERTIFIED') cutover_gate_status,coalesce(c.required_count,5) cutover_required_count,coalesce(c.passed_count,0) cutover_passed_count,coalesce(c.blocking_count,5) cutover_blocking_count,
       a.release_candidate_id active_release_candidate_id,a.artifact_sha256 active_artifact_sha256,a.environment_ref active_environment_ref,a.activated_at active_candidate_activated_at,
       coalesce(f.controlled_live_unbound_flows,0) controlled_live_unbound_flows,coalesce(f.production_bound_flows,0) production_bound_flows,coalesce(f.invalid_production_bound_flows,0) invalid_production_bound_flows,
       case when coalesce(r.gate_status,'NOT_CERTIFIED')='PASS' and coalesce(c.gate_status,'NOT_CERTIFIED')='PASS' and a.release_candidate_id is not null and coalesce(a.all_required_pass,false)=true and coalesce(f.invalid_production_bound_flows,0)=0 then 'PASS' else 'NOT_CERTIFIED' end::varchar(24) gate_status,
       case when coalesce(r.gate_status,'NOT_CERTIFIED')='PASS' and coalesce(c.gate_status,'NOT_CERTIFIED')='PASS' and a.release_candidate_id is not null and coalesce(a.all_required_pass,false)=true and coalesce(f.invalid_production_bound_flows,0)=0 then true else false end production_foundation_ready,
       -- PostgreSQL CREATE OR REPLACE VIEW may only append columns; existing V208 columns must retain
       -- their original names and ordinal positions. Signed-evidence columns are therefore appended.
       coalesce(a.all_required_pass,false) signed_evidence_ready,a.evidence_set_hash signed_evidence_set_hash
  from tenants t
  left join a0_r8_release_gate_v207 r on r.tenant_id=t.tenant_id
  left join a0_release_cutover_gate_v208 c on c.tenant_id=t.tenant_id
  left join active_candidate a on a.tenant_id=t.tenant_id
  left join flow_health f on f.tenant_id=t.tenant_id;

-- 4. Signed-evidence permission domain extension. Ingest/trust management are NOT auto-granted to
-- human roles; they require an explicit machine/admin grant. Evidence read follows release read.
insert into permission_catalog_revisions(revision_id,revision_code,revision_number,status,content_hash,description,supersedes_revision_id,created_at,created_by,published_at,published_by,version)
select '00000000-0000-0000-0000-000000000029'::uuid,'V41-C3R3G-HF5-33-SIGNED-RELEASE-EVIDENCE',coalesce(max(revision_number),0)+1,'DRAFT','DRAFT:UNPUBLISHED','Signed release evidence trust roots, ingestion and candidate-bound verification.',(select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE'),now(),'v41-c3r3g-hf5-33-signed-release-evidence',null,null,1 from permission_catalog_revisions
on conflict(revision_id) do nothing;

insert into permission_catalog_revision_entries(revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,lifecycle,allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by,version)
select '00000000-0000-0000-0000-000000000029'::uuid,e.permission_code,e.owner_module,e.resource_type,e.action_code,e.description,e.risk_level,e.risk_lane,e.lifecycle,e.allowed_scope_types,e.system_managed,e.replacement_permission_code,e.introduced_at,e.deprecated_at,e.retired_at,now(),'v41-c3r3g-hf5-33-signed-release-evidence',1
from permission_catalog_revision_entries e join permission_catalog_active_revision a on a.singleton_id='ACTIVE' and a.revision_id=e.revision_id
on conflict(revision_id,permission_code) do nothing;
insert into permission_catalog_revision_aliases(revision_id,alias_code,canonical_permission_code,alias_type,valid_from,valid_until,reason,created_by,created_at,version)
select '00000000-0000-0000-0000-000000000029'::uuid,a.alias_code,a.canonical_permission_code,a.alias_type,a.valid_from,a.valid_until,a.reason,'v41-c3r3g-hf5-33-signed-release-evidence',now(),1
from permission_catalog_revision_aliases a join permission_catalog_active_revision active on active.singleton_id='ACTIVE' and active.revision_id=a.revision_id
on conflict(revision_id,alias_code) do nothing;

insert into permission_catalog_revision_entries(revision_id,permission_code,owner_module,resource_type,action_code,description,risk_level,risk_lane,lifecycle,allowed_scope_types,system_managed,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by,version) values
 ('00000000-0000-0000-0000-000000000029','release.evidence.read','release-certification','RELEASE_EVIDENCE','READ','Read signed evidence status and trusted executor public metadata.','HIGH','READ','ACTIVE',array['TENANT']::varchar[],true,null,now(),null,null,now(),'v41-c3r3g-hf5-33-signed-release-evidence',1),
 ('00000000-0000-0000-0000-000000000029','release.evidence.ingest.signed','release-certification','RELEASE_EVIDENCE','INGEST_SIGNED','Submit an RS256 signed candidate-bound release evidence attestation from a trusted automated executor.','CRITICAL','CRITICAL','ACTIVE',array['TENANT']::varchar[],true,null,now(),null,null,now(),'v41-c3r3g-hf5-33-signed-release-evidence',1),
 ('00000000-0000-0000-0000-000000000029','release.evidence.trust.manage','release-certification','RELEASE_EVIDENCE_TRUST','MANAGE','Register or revoke trusted release evidence executor public keys. Private keys never enter OpenDispatch.','CRITICAL','CRITICAL','ACTIVE',array['TENANT']::varchar[],true,null,now(),null,null,now(),'v41-c3r3g-hf5-33-signed-release-evidence',1)
on conflict(revision_id,permission_code) do update set description=excluded.description,risk_level=excluded.risk_level,risk_lane=excluded.risk_lane,lifecycle='ACTIVE',allowed_scope_types=excluded.allowed_scope_types,updated_at=now(),updated_by='v41-c3r3g-hf5-33-signed-release-evidence',version=permission_catalog_revision_entries.version+1;

select set_config('app.permission_catalog_publish_revision_id','00000000-0000-0000-0000-000000000029',true);
insert into permission_definitions(permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,active,version,owner_module,risk_lane,lifecycle,catalog_revision_id,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by)
select permission_code,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed,lifecycle<>'RETIRED',1,owner_module,risk_lane,lifecycle,revision_id,replacement_permission_code,introduced_at,deprecated_at,retired_at,updated_at,updated_by from permission_catalog_revision_entries where revision_id='00000000-0000-0000-0000-000000000029'::uuid
on conflict(permission_code) do update set resource_type=excluded.resource_type,action_code=excluded.action_code,description=excluded.description,risk_level=excluded.risk_level,allowed_scope_types=excluded.allowed_scope_types,system_managed=excluded.system_managed,active=excluded.active,owner_module=excluded.owner_module,risk_lane=excluded.risk_lane,lifecycle=excluded.lifecycle,catalog_revision_id=excluded.catalog_revision_id,updated_at=excluded.updated_at,updated_by=excluded.updated_by,version=permission_definitions.version+1;
update permission_catalog_revisions set status='SUPERSEDED',version=version+1 where revision_id=(select revision_id from permission_catalog_active_revision where singleton_id='ACTIVE') and revision_id<>'00000000-0000-0000-0000-000000000029'::uuid and status='PUBLISHED';
update permission_catalog_revisions set status='PUBLISHED',content_hash=(with catalog_lines as (select 'P|'||permission_code||'|'||owner_module||'|'||resource_type||'|'||action_code||'|'||description||'|'||risk_level||'|'||risk_lane||'|'||lifecycle||'|'||coalesce(array_to_string(allowed_scope_types,','),'')||'|'||system_managed::text||'|'||coalesce(replacement_permission_code,'') line from permission_catalog_revision_entries where revision_id='00000000-0000-0000-0000-000000000029'::uuid union all select 'A|'||alias_code||'|'||canonical_permission_code||'|'||alias_type||'|'||coalesce(to_char(valid_from at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||coalesce(to_char(valid_until at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'')||'|'||reason from permission_catalog_revision_aliases where revision_id='00000000-0000-0000-0000-000000000029'::uuid) select 'sha256:'||encode(sha256(convert_to(coalesce(string_agg(line,E'\n' order by line),''),'UTF8')),'hex') from catalog_lines),published_at=now(),published_by='v41-c3r3g-hf5-33-signed-release-evidence',version=version+1 where revision_id='00000000-0000-0000-0000-000000000029'::uuid and status='DRAFT';
update permission_catalog_active_revision set revision_id='00000000-0000-0000-0000-000000000029'::uuid,activated_at=now(),activated_by='v41-c3r3g-hf5-33-signed-release-evidence',version=version+1 where singleton_id='ACTIVE';
insert into permission_catalog_publication_events(publication_id,revision_id,previous_revision_id,content_hash,entry_count,alias_count,actor_id,audit_reason,correlation_id,published_at)
select '00000000-0000-0000-0000-000000005029'::uuid,r.revision_id,r.supersedes_revision_id,r.content_hash,(select count(*)::integer from permission_catalog_revision_entries e where e.revision_id=r.revision_id),(select count(*)::integer from permission_catalog_revision_aliases a where a.revision_id=r.revision_id),'v41-c3r3g-hf5-33-signed-release-evidence','HF5.33 signed Release Evidence authority publication','v41-c3r3g-hf5-33-signed-release-evidence',coalesce(r.published_at,now()) from permission_catalog_revisions r where r.revision_id='00000000-0000-0000-0000-000000000029'::uuid
on conflict(publication_id) do nothing;

insert into rbac_role_permissions(grant_id,tenant_id,role_id,permission_point,created_at,created_by,version)
select 'hf533-read-'||substr(md5(existing.role_id||':release.evidence.read'),1,28),existing.tenant_id,existing.role_id,'release.evidence.read',now(),'v41-c3r3g-hf5-33-signed-release-evidence',1 from rbac_role_permissions existing where existing.permission_point='release.certification.read'
on conflict(role_id,permission_point) do nothing;

insert into permission_entry_point_inventory(entry_point_id,entry_point_type,application_id,owner_module,display_name,route_pattern,http_method,authority_state,target_permission_code,legacy_authority_type,legacy_authorities,resource_type,resource_resolver_id,exemption_reason,migration_deadline,manifest_revision,source_ref,source_hash,last_verified_at,created_by,updated_by) values
 ('REST:GET:/admin/production-foundation/evidence/trusted-executors','REST','control-plane-app','release-certification','ProductionFoundationReleaseController.trustedExecutors','/admin/production-foundation/evidence/trusted-executors','GET','TARGET_ONLY','release.evidence.read',null,'[]'::jsonb,'RELEASE_EVIDENCE_TRUST','R3_TENANT_RESOURCE_RESOLVER',null,null,'v41-c3r3g-hf5-33-2026-10-02','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ProductionFoundationReleaseController.java#trustedExecutors','9c96df8a0b9751adddd55ef9792d205f9cdd20effc43d46445942e5f98f84210',now(),'v41-c3r3g-hf5-33-signed-release-evidence','v41-c3r3g-hf5-33-signed-release-evidence'),
 ('REST:POST:/admin/production-foundation/evidence/trusted-executors','REST','control-plane-app','release-certification','ProductionFoundationReleaseController.registerTrustedExecutor','/admin/production-foundation/evidence/trusted-executors','POST','TARGET_ONLY','release.evidence.trust.manage',null,'[]'::jsonb,'RELEASE_EVIDENCE_TRUST','R3_TENANT_RESOURCE_RESOLVER',null,null,'v41-c3r3g-hf5-33-2026-10-02','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ProductionFoundationReleaseController.java#registerTrustedExecutor','9c96df8a0b9751adddd55ef9792d205f9cdd20effc43d46445942e5f98f84210',now(),'v41-c3r3g-hf5-33-signed-release-evidence','v41-c3r3g-hf5-33-signed-release-evidence'),
 ('REST:POST:/admin/production-foundation/evidence/trusted-executors/{executorId}/{keyId}/revoke','REST','control-plane-app','release-certification','ProductionFoundationReleaseController.revokeTrustedExecutor','/admin/production-foundation/evidence/trusted-executors/{executorId}/{keyId}/revoke','POST','TARGET_ONLY','release.evidence.trust.manage',null,'[]'::jsonb,'RELEASE_EVIDENCE_TRUST','R3_PATH_RESOURCE_RESOLVER',null,null,'v41-c3r3g-hf5-33-2026-10-02','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ProductionFoundationReleaseController.java#revokeTrustedExecutor','9c96df8a0b9751adddd55ef9792d205f9cdd20effc43d46445942e5f98f84210',now(),'v41-c3r3g-hf5-33-signed-release-evidence','v41-c3r3g-hf5-33-signed-release-evidence'),
 ('REST:POST:/admin/production-foundation/evidence/attestations','REST','control-plane-app','release-certification','ProductionFoundationReleaseController.ingestSignedEvidence','/admin/production-foundation/evidence/attestations','POST','TARGET_ONLY','release.evidence.ingest.signed',null,'[]'::jsonb,'RELEASE_EVIDENCE','R3_TENANT_RESOURCE_RESOLVER',null,null,'v41-c3r3g-hf5-33-2026-10-02','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ProductionFoundationReleaseController.java#ingestSignedEvidence','9c96df8a0b9751adddd55ef9792d205f9cdd20effc43d46445942e5f98f84210',now(),'v41-c3r3g-hf5-33-signed-release-evidence','v41-c3r3g-hf5-33-signed-release-evidence'),
 ('REST:GET:/admin/production-foundation/candidates/{candidateId}/evidence','REST','control-plane-app','release-certification','ProductionFoundationReleaseController.candidateEvidence','/admin/production-foundation/candidates/{candidateId}/evidence','GET','TARGET_ONLY','release.evidence.read',null,'[]'::jsonb,'RELEASE_CANDIDATE','R3_PATH_RESOURCE_RESOLVER',null,null,'v41-c3r3g-hf5-33-2026-10-02','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/ProductionFoundationReleaseController.java#candidateEvidence','9c96df8a0b9751adddd55ef9792d205f9cdd20effc43d46445942e5f98f84210',now(),'v41-c3r3g-hf5-33-signed-release-evidence','v41-c3r3g-hf5-33-signed-release-evidence')
on conflict(entry_point_id) do update set display_name=excluded.display_name,route_pattern=excluded.route_pattern,http_method=excluded.http_method,authority_state=excluded.authority_state,target_permission_code=excluded.target_permission_code,legacy_authority_type=null,legacy_authorities='[]'::jsonb,resource_type=excluded.resource_type,resource_resolver_id=excluded.resource_resolver_id,exemption_reason=excluded.exemption_reason,migration_deadline=excluded.migration_deadline,manifest_revision=excluded.manifest_revision,source_ref=excluded.source_ref,source_hash=excluded.source_hash,last_verified_at=now(),updated_at=now(),updated_by='v41-c3r3g-hf5-33-signed-release-evidence',version=permission_entry_point_inventory.version+1;

update rbac_policy_versions set policy_version=policy_version+1,updated_at=now(),updated_by='v41-c3r3g-hf5-33-signed-release-evidence';

-- 5. Migration self-checks.
do $$ declare v_permissions integer; v_routes integer; begin
  select count(*) into v_permissions from permission_definitions where permission_code in('release.evidence.read','release.evidence.ingest.signed','release.evidence.trust.manage') and active=true and lifecycle='ACTIVE';
  if v_permissions<>3 then raise exception 'HF5_33_RELEASE_EVIDENCE_PERMISSION_CATALOG_INCOMPLETE expected=3 actual=%',v_permissions; end if;
  select count(*) into v_routes from permission_entry_point_inventory where manifest_revision='v41-c3r3g-hf5-33-2026-10-02' and authority_state='TARGET_ONLY';
  if v_routes<>5 then raise exception 'HF5_33_RELEASE_EVIDENCE_ROUTE_MAPPING_INCOMPLETE expected=5 actual=%',v_routes; end if;
end $$;
