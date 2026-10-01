-- V40-8.3: Runtime Configuration required-node topology truth and trustworthy presence semantics.
-- Required targets are deliberately separate from apply-state evidence so an expected node can be NOT_SEEN.

create table if not exists runtime_config_required_node_targets (
  config_set_id varchar(128) not null references runtime_config_sets(config_set_id),
  node_id varchar(128) not null,
  node_role varchar(32) not null,
  node_instance_id varchar(128) not null,
  required boolean not null default true,
  registration_source varchar(128) not null,
  registered_at timestamptz not null default now(),
  last_seen_at timestamptz,
  updated_at timestamptz not null default now(),
  primary key(config_set_id,node_id),
  check (node_role in ('CORE','GATEWAY','WORKER'))
);
create index if not exists idx_runtime_config_required_targets_role
  on runtime_config_required_node_targets(config_set_id,required,node_role,node_id);
create index if not exists idx_runtime_config_required_targets_presence
  on runtime_config_required_node_targets(required,last_seen_at);
comment on table runtime_config_required_node_targets is
  'Authoritative convergence denominator for Runtime Configuration. Required targets may exist before first node observation.';

-- Preserve the convergence topology learned before V40-8.3.
insert into runtime_config_required_node_targets(
  config_set_id,node_id,node_role,node_instance_id,required,registration_source,registered_at,last_seen_at,updated_at)
select config_set_id,node_id,node_role,node_instance_id,true,'V40_8_3_BACKFILL',
       coalesce(desired_at,now()),last_seen_at,now()
  from runtime_config_apply_states
on conflict(config_set_id,node_id) do update set
  node_role=excluded.node_role,
  node_instance_id=excluded.node_instance_id,
  required=true,
  last_seen_at=coalesce(runtime_config_required_node_targets.last_seen_at,excluded.last_seen_at),
  updated_at=now();

-- V240 touched last_seen_at on every UPDATE, including control-plane desired-state changes.
-- From V40-8.3 onward last_seen_at is node-observation evidence only and is written explicitly
-- by reconcile/ACK SQL. The trigger only guards APPLIED consistency and updated_at.
create or replace function guard_runtime_config_apply_state() returns trigger language plpgsql as $$
begin
  if new.state='APPLIED' and new.applied_revision_id is distinct from new.desired_revision_id then
    raise exception 'RUNTIME_CONFIG_APPLIED_REVISION_MISMATCH: desired % applied %',new.desired_revision_id,new.applied_revision_id;
  end if;
  if new.state='APPLIED' and new.applied_at is null then new.applied_at=now(); end if;
  new.updated_at=now();
  return new;
end $$;

-- Two new read-only Admin endpoints remain governed by the existing configuration.view permission.
-- The controller source hash is synchronized by this migration so the existing ReBAC inventory stays current.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','v40-8-3-admin-applied-state',true);

insert into permission_entry_point_inventory(
 entry_point_id,entry_point_type,application_id,owner_module,display_name,route_pattern,http_method,authority_state,
 target_permission_code,legacy_authority_type,legacy_authorities,resource_type,resource_resolver_id,exemption_reason,
 migration_deadline,manifest_revision,source_ref,source_hash,last_verified_at,created_by,updated_by)
values
('REST:GET:/api/platform/runtime-configuration/settings/{key}/apply-state','REST','control-plane-app','control-plane-app',
 'RuntimeConfigurationAdminController.applyState','/api/platform/runtime-configuration/settings/{key}/apply-state','GET','TARGET_ONLY',
 'configuration.view',null,'[]'::jsonb,'RUNTIME_CONFIGURATION','R3_ROUTE_RESOURCE_RESOLVER',null,null,
 'v40-8-3-admin-applied-state-2026-09-20','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/configuration/RuntimeConfigurationAdminController.java#applyState',
 '2ddff37692ddb8eea6f7de3da8ee354389cba12f00a7dc86123630a47ed9da79',now(),'v40-8-3-admin-applied-state','v40-8-3-admin-applied-state'),
('REST:GET:/api/platform/runtime-configuration/settings/{key}/rollback-preview','REST','control-plane-app','control-plane-app',
 'RuntimeConfigurationAdminController.rollbackPreview','/api/platform/runtime-configuration/settings/{key}/rollback-preview','GET','TARGET_ONLY',
 'configuration.view',null,'[]'::jsonb,'RUNTIME_CONFIGURATION','R3_ROUTE_RESOURCE_RESOLVER',null,null,
 'v40-8-3-admin-applied-state-2026-09-20','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/configuration/RuntimeConfigurationAdminController.java#rollbackPreview',
 '2ddff37692ddb8eea6f7de3da8ee354389cba12f00a7dc86123630a47ed9da79',now(),'v40-8-3-admin-applied-state','v40-8-3-admin-applied-state')
on conflict(entry_point_id) do update set
 display_name=excluded.display_name,route_pattern=excluded.route_pattern,http_method=excluded.http_method,
 authority_state=excluded.authority_state,target_permission_code=excluded.target_permission_code,legacy_authority_type=null,
 legacy_authorities='[]'::jsonb,resource_type=excluded.resource_type,resource_resolver_id=excluded.resource_resolver_id,
 exemption_reason=null,migration_deadline=null,manifest_revision=excluded.manifest_revision,source_ref=excluded.source_ref,
 source_hash=excluded.source_hash,last_verified_at=now(),updated_at=now(),updated_by='v40-8-3-admin-applied-state',
 version=permission_entry_point_inventory.version+1;

update rbac_policy_versions set policy_version=policy_version+1,updated_at=now(),updated_by='v40-8-3-admin-applied-state';
