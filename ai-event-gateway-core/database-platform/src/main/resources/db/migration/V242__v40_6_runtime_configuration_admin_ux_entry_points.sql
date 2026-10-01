-- V40-6: Platform Runtime Configuration Admin UI entry points.
-- Temporary permission bridge only: V40-7 introduces dedicated configuration:* governance permissions.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','v40-6-runtime-configuration-ui',true);

insert into permission_entry_point_inventory(
 entry_point_id,entry_point_type,application_id,owner_module,display_name,route_pattern,http_method,authority_state,
 target_permission_code,legacy_authority_type,legacy_authorities,resource_type,resource_resolver_id,exemption_reason,
 migration_deadline,manifest_revision,source_ref,source_hash,last_verified_at,created_by,updated_by)
values
('REST:GET:/api/platform/runtime-configuration/overview','REST','control-plane-app','control-plane-app','RuntimeConfigurationAdminController.overview','/api/platform/runtime-configuration/overview','GET','TARGET_ONLY','permission.entry_point.read',null,'[]'::jsonb,'IAM_ENTRY_POINT_AUTHORITY','R3_ROUTE_RESOURCE_RESOLVER',null,null,'v40-6-runtime-configuration-ui-2026-09-18','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/configuration/RuntimeConfigurationAdminController.java#overview','2734d1ced8efe667815209228b60864fc79ba5d826659f932ecdb96a0ee1bc91',now(),'v40-6-runtime-configuration-ui','v40-6-runtime-configuration-ui'),
('REST:GET:/api/platform/runtime-configuration/settings/{key}','REST','control-plane-app','control-plane-app','RuntimeConfigurationAdminController.detail','/api/platform/runtime-configuration/settings/{key}','GET','TARGET_ONLY','permission.entry_point.read',null,'[]'::jsonb,'IAM_ENTRY_POINT_AUTHORITY','R3_ROUTE_RESOURCE_RESOLVER',null,null,'v40-6-runtime-configuration-ui-2026-09-18','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/configuration/RuntimeConfigurationAdminController.java#detail','2734d1ced8efe667815209228b60864fc79ba5d826659f932ecdb96a0ee1bc91',now(),'v40-6-runtime-configuration-ui','v40-6-runtime-configuration-ui'),
('REST:POST:/api/platform/runtime-configuration/settings/{key}/publish','REST','control-plane-app','control-plane-app','RuntimeConfigurationAdminController.publish','/api/platform/runtime-configuration/settings/{key}/publish','POST','TARGET_ONLY','permission.entry_point.manage',null,'[]'::jsonb,'IAM_ENTRY_POINT_AUTHORITY','R3_ROUTE_RESOURCE_RESOLVER',null,null,'v40-6-runtime-configuration-ui-2026-09-18','ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/configuration/RuntimeConfigurationAdminController.java#publish','2734d1ced8efe667815209228b60864fc79ba5d826659f932ecdb96a0ee1bc91',now(),'v40-6-runtime-configuration-ui','v40-6-runtime-configuration-ui')
on conflict(entry_point_id) do update set
 display_name=excluded.display_name,route_pattern=excluded.route_pattern,http_method=excluded.http_method,
 authority_state=excluded.authority_state,target_permission_code=excluded.target_permission_code,legacy_authority_type=null,
 legacy_authorities='[]'::jsonb,resource_type=excluded.resource_type,resource_resolver_id=excluded.resource_resolver_id,
 exemption_reason=null,migration_deadline=null,manifest_revision=excluded.manifest_revision,source_ref=excluded.source_ref,
 source_hash=excluded.source_hash,last_verified_at=now(),updated_at=now(),updated_by='v40-6-runtime-configuration-ui',version=permission_entry_point_inventory.version+1;

update rbac_policy_versions set policy_version=policy_version+1,updated_at=now(),updated_by='v40-6-runtime-configuration-ui';
