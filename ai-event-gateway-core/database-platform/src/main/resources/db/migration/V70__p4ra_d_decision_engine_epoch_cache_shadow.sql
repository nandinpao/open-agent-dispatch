-- P4RA-D: formal Resource Authorization decision evidence, composite security epochs and shadow comparison.
-- Business API enforcement remains disabled; decisions are exposed only through the disabled-by-default decision API and shadow lane.

create table if not exists resource_access_tenant_security_epochs (
  tenant_id varchar(64) primary key,
  security_epoch bigint not null default 0,
  updated_at timestamptz not null default now(),
  updated_by varchar(128) not null default 'p4ra-policy',
  foreign key (tenant_id) references tenants(tenant_id),
  check (security_epoch >= 0)
);

create table if not exists resource_access_principal_security_epochs (
  tenant_id varchar(64) not null,
  principal_id varchar(128) not null,
  security_epoch bigint not null default 0,
  updated_at timestamptz not null default now(),
  updated_by varchar(128) not null default 'p4ra-policy',
  primary key (tenant_id,principal_id),
  foreign key (tenant_id) references tenants(tenant_id),
  check (security_epoch >= 0)
);

insert into resource_access_tenant_security_epochs(tenant_id)
select tenant_id from tenants on conflict do nothing;

-- resource_access_policy_revisions is already FORCE RLS from V67. Flyway intentionally has no
-- runtime tenant context, so seed each tenant under a transaction-local context instead of bypassing RLS.
do $$
declare
  tenant_value varchar(64);
  previous_tenant text := current_setting('app.current_tenant_id', true);
begin
  for tenant_value in select tenant_id from tenants order by tenant_id loop
    perform set_config('app.current_tenant_id', tenant_value, true);
    insert into resource_access_policy_revisions(
      tenant_id,policy_revision,catalog_version,content_hash,status,activated_at,created_at,created_by
    )
    select tenant_value,1,coalesce((select max(catalog_version) from resource_catalog),1),
           md5('P4RA-D:'||tenant_value||':1'),'ACTIVE',now(),now(),'p4ra-d-migration'
    where not exists(
      select 1 from resource_access_policy_revisions r
      where r.tenant_id=tenant_value and r.status='ACTIVE'
    );
  end loop;
  perform set_config('app.current_tenant_id', coalesce(previous_tenant,''), true);
exception when others then
  perform set_config('app.current_tenant_id', coalesce(previous_tenant,''), true);
  raise;
end $$;

create table if not exists resource_authorization_decisions (
  tenant_id varchar(64) not null,
  decision_id varchar(128) not null,
  principal_type varchar(32) not null,
  principal_id varchar(128) not null,
  permission_code varchar(160) not null,
  resource_type varchar(64) not null,
  resource_id varchar(128) not null,
  requested_visibility varchar(32) not null,
  granted_visibility varchar(32) not null,
  effect varchar(32) not null,
  decision_mode varchar(32) not null,
  shadow_only boolean not null,
  matched_role_binding_ids text not null default '',
  matched_scope_grant_ids text not null default '',
  matched_participant_ids text not null default '',
  matched_ownership_evidence text not null default '',
  matched_deny_ids text not null default '',
  matched_scope_sources text not null default '',
  reason_codes text not null,
  policy_catalog_version bigint not null,
  policy_revision bigint not null,
  policy_content_hash varchar(128) not null default '',
  global_security_epoch bigint not null,
  tenant_security_epoch bigint not null,
  principal_security_epoch bigint not null,
  resource_security_epoch bigint not null,
  department_tree_revision bigint not null,
  descriptor_hash varchar(128) not null,
  request_channel varchar(32) not null,
  operation_phase varchar(32) not null,
  purpose varchar(256) not null,
  correlation_id varchar(128) not null,
  cacheable boolean not null,
  cache_ttl_ms bigint not null,
  evaluated_at timestamptz not null,
  created_at timestamptz not null default now(),
  primary key (tenant_id,decision_id),
  foreign key (tenant_id) references tenants(tenant_id),
  check (effect in ('ALLOW','DENY','CONDITIONAL','NOT_APPLICABLE','ERROR')),
  check (decision_mode in ('FORMAL','EXPLAIN','SIMULATION','SHADOW')),
  check (requested_visibility in ('NONE','METADATA','SUMMARY','STANDARD','SENSITIVE','FULL','SECRET_METADATA')),
  check (granted_visibility in ('NONE','METADATA','SUMMARY','STANDARD','SENSITIVE','FULL','SECRET_METADATA')),
  check (effect='ALLOW' or granted_visibility='NONE'),
  check ((decision_mode='FORMAL' and not shadow_only) or (decision_mode<>'FORMAL' and shadow_only)),
  check (policy_catalog_version>=0 and policy_revision>=0),
  check (global_security_epoch>=0 and tenant_security_epoch>=0 and principal_security_epoch>=0 and resource_security_epoch>=0 and department_tree_revision>=0),
  check (cache_ttl_ms>=0),
  check ((cacheable and cache_ttl_ms>0) or (not cacheable and cache_ttl_ms=0)),
  check (not cacheable or (decision_mode='FORMAL' and effect='ALLOW'))
);

create table if not exists resource_shadow_decision_comparisons (
  tenant_id varchar(64) not null,
  comparison_id varchar(128) not null,
  resource_type varchar(64) not null,
  resource_id varchar(128) not null,
  permission_code varchar(160) not null,
  legacy_effect varchar(32) not null,
  legacy_reason_code varchar(128) not null,
  legacy_decision_id varchar(128),
  resource_effect varchar(32) not null,
  resource_decision_id varchar(128) not null,
  mismatch_category varchar(64) not null,
  correlation_id varchar(128) not null,
  compared_at timestamptz not null,
  created_at timestamptz not null default now(),
  primary key (tenant_id,comparison_id),
  foreign key (tenant_id,resource_decision_id) references resource_authorization_decisions(tenant_id,decision_id),
  check (legacy_effect in ('ALLOW','DENY','NOT_AVAILABLE','ERROR')),
  check (resource_effect in ('ALLOW','DENY','CONDITIONAL','NOT_APPLICABLE','ERROR')),
  check (mismatch_category in ('MATCH_ALLOW','MATCH_DENY','LEGACY_ALLOW_RESOURCE_DENY','LEGACY_DENY_RESOURCE_ALLOW','LEGACY_NOT_AVAILABLE','LEGACY_ERROR','RESOURCE_ERROR'))
);

create index if not exists idx_resource_authorization_decisions_subject
 on resource_authorization_decisions(tenant_id,principal_id,permission_code,evaluated_at desc);
create index if not exists idx_resource_authorization_decisions_resource
 on resource_authorization_decisions(tenant_id,resource_type,resource_id,evaluated_at desc);
create index if not exists idx_resource_authorization_decisions_reason
 on resource_authorization_decisions(tenant_id,effect,decision_mode,evaluated_at desc);
create index if not exists idx_resource_shadow_mismatch
 on resource_shadow_decision_comparisons(tenant_id,mismatch_category,compared_at desc);

create or replace function p4ra_advance_policy_revision(p_tenant_id varchar,p_actor varchar)
returns void language plpgsql as $$
declare next_revision bigint; next_catalog bigint;
begin
  perform pg_advisory_xact_lock(hashtextextended(p_tenant_id,0));
  insert into resource_access_tenant_security_epochs(tenant_id,security_epoch,updated_at,updated_by)
  values(p_tenant_id,1,now(),coalesce(nullif(p_actor,''),'p4ra-policy'))
  on conflict(tenant_id) do update set security_epoch=resource_access_tenant_security_epochs.security_epoch+1,updated_at=excluded.updated_at,updated_by=excluded.updated_by;
  select coalesce(max(policy_revision),0)+1 into next_revision from resource_access_policy_revisions where tenant_id=p_tenant_id;
  select coalesce(max(catalog_version),1) into next_catalog from resource_catalog;
  update resource_access_policy_revisions set status='RETIRED' where tenant_id=p_tenant_id and status='ACTIVE';
  insert into resource_access_policy_revisions(tenant_id,policy_revision,catalog_version,content_hash,status,activated_at,created_at,created_by)
  values(p_tenant_id,next_revision,next_catalog,md5(p_tenant_id||':'||next_revision||':'||clock_timestamp()::text),'ACTIVE',now(),now(),coalesce(nullif(p_actor,''),'p4ra-policy'));
end $$;

create or replace function p4ra_touch_principal_policy_epoch()
returns trigger language plpgsql as $$
declare tenant_value varchar; principal_value varchar; actor_value varchar;
begin
 if TG_OP='DELETE' then tenant_value=old.tenant_id;principal_value=old.principal_id; else tenant_value=new.tenant_id;principal_value=new.principal_id; end if;actor_value='p4ra-policy-trigger';
 perform p4ra_advance_policy_revision(tenant_value,actor_value);
 insert into resource_access_principal_security_epochs(tenant_id,principal_id,security_epoch,updated_at,updated_by)
 values(tenant_value,principal_value,1,now(),actor_value)
 on conflict(tenant_id,principal_id) do update set security_epoch=resource_access_principal_security_epochs.security_epoch+1,updated_at=excluded.updated_at,updated_by=excluded.updated_by;
 if TG_OP='DELETE' then return old; else return new; end if;
end $$;

create or replace function p4ra_touch_tenant_policy_epoch()
returns trigger language plpgsql as $$
declare tenant_value varchar;
begin if TG_OP='DELETE' then tenant_value=old.tenant_id; else tenant_value=new.tenant_id; end if;perform p4ra_advance_policy_revision(tenant_value,'p4ra-policy-trigger');if TG_OP='DELETE' then return old; else return new; end if;end $$;

-- Current-state policy changes advance the tenant namespace; principal-targeted policies also advance the principal namespace.
drop trigger if exists trg_p4ra_grant_epoch on resource_scope_grants;
create trigger trg_p4ra_grant_epoch after insert or update or delete on resource_scope_grants for each row execute function p4ra_touch_principal_policy_epoch();
drop trigger if exists trg_p4ra_deny_epoch on resource_scope_denies;
create trigger trg_p4ra_deny_epoch after insert or update or delete on resource_scope_denies for each row execute function p4ra_touch_principal_policy_epoch();
drop trigger if exists trg_p4ra_clearance_epoch on resource_principal_clearances;
create trigger trg_p4ra_clearance_epoch after insert or update or delete on resource_principal_clearances for each row execute function p4ra_touch_principal_policy_epoch();
drop trigger if exists trg_p4ra_visibility_epoch on resource_visibility_policies;
create trigger trg_p4ra_visibility_epoch after insert or update or delete on resource_visibility_policies for each row execute function p4ra_touch_tenant_policy_epoch();
drop trigger if exists trg_p4ra_visibility_field_epoch on resource_visibility_fields;
create trigger trg_p4ra_visibility_field_epoch after insert or update or delete on resource_visibility_fields for each row execute function p4ra_touch_tenant_policy_epoch();

-- Decision and comparison ledgers are immutable.
drop trigger if exists trg_resource_authorization_decision_immutable on resource_authorization_decisions;
create trigger trg_resource_authorization_decision_immutable before update or delete on resource_authorization_decisions for each row execute function p4ra_reject_append_only_mutation();
drop trigger if exists trg_resource_shadow_comparison_immutable on resource_shadow_decision_comparisons;
create trigger trg_resource_shadow_comparison_immutable before update or delete on resource_shadow_decision_comparisons for each row execute function p4ra_reject_append_only_mutation();

do $$
declare table_name text;
begin
 foreach table_name in array array['resource_access_tenant_security_epochs','resource_access_principal_security_epochs','resource_authorization_decisions','resource_shadow_decision_comparisons'] loop
  execute format('alter table %I enable row level security',table_name);
  execute format('alter table %I force row level security',table_name);
  execute format('drop policy if exists tenant_isolation on %I',table_name);
  execute format('create policy tenant_isolation on %I using (tenant_id=iam_current_tenant_id()) with check (tenant_id=iam_current_tenant_id())',table_name);
 end loop;
end $$;

insert into permission_point_catalog(permission_point,resource_type,action_code,description,risk_level,allowed_scope_types,system_managed)
values
 ('resource.decision.evaluate','RESOURCE_DECISION','EXECUTE','Evaluate the authenticated principal against a Resource.','HIGH',array['TENANT'],true),
 ('resource.decision.explain','RESOURCE_DECISION','READ','Explain a Resource Authorization decision.','HIGH',array['TENANT'],true),
 ('resource.decision.simulate','RESOURCE_DECISION','EXECUTE','Simulate another principal Resource decision without issuing an execution token.','CRITICAL',array['TENANT'],true),
 ('resource.shadow.read','RESOURCE_DECISION','READ','Read legacy/new Resource Access shadow mismatch evidence.','CRITICAL',array['TENANT'],true)
on conflict(permission_point) do update set resource_type=excluded.resource_type,action_code=excluded.action_code,description=excluded.description,risk_level=excluded.risk_level,allowed_scope_types=excluded.allowed_scope_types,system_managed=true,active=true,version=permission_point_catalog.version+1;

insert into reason_code_catalog(reason_code,http_status,category,retryable,message_template)
values
 ('RESOURCE_DESCRIPTOR_NOT_FOUND',404,'AUTHORIZATION',false,'The Resource descriptor is unavailable.'),
 ('PERMISSION_NOT_GRANTED',403,'AUTHORIZATION',false,'The required permission is not granted.'),
 ('RESOURCE_SCOPE_NOT_MATCHED',403,'AUTHORIZATION',false,'No Resource Scope evidence matched.'),
 ('RESOURCE_PARTICIPANT_MATCHED',200,'AUTHORIZATION',false,'A Resource participant matched.'),
 ('RESOURCE_OWNER_SCOPE_MATCHED',200,'AUTHORIZATION',false,'Resource ownership scope matched.'),
 ('EXPLICIT_GRANT_MATCHED',200,'AUTHORIZATION',false,'An explicit Resource grant matched.'),
 ('TENANT_ROLE_FALLBACK_MATCHED',200,'AUTHORIZATION',false,'A Tenant role fallback matched.'),
 ('RESOURCE_QUARANTINED',403,'AUTHORIZATION',false,'The Resource is quarantined.'),
 ('RESOURCE_LEGAL_HOLD_RESTRICTED',403,'AUTHORIZATION',false,'Legal hold blocks the requested operation.'),
 ('RESOURCE_INVESTIGATION_RESTRICTED',403,'AUTHORIZATION',false,'Investigation state blocks the requested operation.'),
 ('RESOURCE_ARCHIVED_READ_ONLY',403,'AUTHORIZATION',false,'Archived Resource is read only.'),
 ('RESOURCE_DELETED',404,'AUTHORIZATION',false,'The Resource is unavailable.'),
 ('RESOURCE_ORPHANED',403,'AUTHORIZATION',false,'The Resource has no valid owner.'),
 ('VISIBILITY_LEVEL_INSUFFICIENT',403,'AUTHORIZATION',false,'Requested visibility exceeds the effective cap.'),
 ('SENSITIVITY_CLEARANCE_INSUFFICIENT',403,'AUTHORIZATION',false,'Principal sensitivity clearance is insufficient.'),
 ('RESOURCE_SECURITY_EPOCH_STALE',409,'AUTHORIZATION',true,'Runtime authorization epoch is stale.'),
 ('STALE_EPOCH_ALLOW_REJECTED',409,'AUTHORIZATION',true,'A cached allow from an old epoch was rejected.'),
 ('AUTHORIZATION_ALLOWED',200,'AUTHORIZATION',false,'Resource Authorization requirements passed.'),
 ('AUTHORIZATION_CACHE_HIT',200,'AUTHORIZATION',false,'Authorization was reused from the current epoch namespace.'),
 ('SIMULATION_ONLY',200,'AUTHORIZATION',false,'Simulation result is not an execution token.'),
 ('SHADOW_ONLY',200,'AUTHORIZATION',false,'Shadow result does not enforce business access.')
on conflict(reason_code) do update set http_status=excluded.http_status,category=excluded.category,retryable=excluded.retryable,message_template=excluded.message_template,active=true,version=reason_code_catalog.version+1;

comment on table resource_authorization_decisions is 'Immutable P4RA-D formal/explain/simulation/shadow Resource decision evidence.';
comment on table resource_shadow_decision_comparisons is 'Immutable legacy versus Resource Access shadow comparison evidence.';
comment on table resource_access_tenant_security_epochs is 'Resource Access policy epoch combined with IAM tenant epoch in versioned cache keys.';
comment on table resource_access_principal_security_epochs is 'Principal-targeted Resource Access policy epoch combined with IAM principal epoch.';
