-- Phase 7 Build Fix 18: make invited Tenant users visible to administration and align Wave 0 reads with canonical INSTANCE authority.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','phase7-build-fix18',true);
select set_config('app.current_audit_reason','Repair invited user administration projection and Wave 0 INSTANCE route contract',true);

-- The trailing-slash overview route is not the Spring MVC contract and must remain retired.
update permission_entry_point_inventory
set authority_state='EXEMPT', target_permission_code=null, legacy_authority_type=null,
    legacy_authorities='[]'::jsonb, resource_resolver_id='NONE',
    exemption_reason='Superseded by exact no-trailing-slash Wave 0 overview route',
    migration_deadline=null, last_verified_at=now(), updated_at=now(),
    updated_by='phase7-build-fix18', version=version+1
where entry_point_id='REST:GET:/api/platform/enforcement-activation/wave0/'
   or route_pattern='/api/platform/enforcement-activation/wave0/';

insert into permission_entry_point_inventory(
 entry_point_id,entry_point_type,application_id,owner_module,display_name,route_pattern,http_method,authority_state,
 target_permission_code,legacy_authority_type,legacy_authorities,resource_type,resource_resolver_id,exemption_reason,
 migration_deadline,manifest_revision,source_ref,source_hash,last_verified_at,created_by,updated_by)
values
(
 'REST:GET:/api/platform/enforcement-activation/wave0','REST','control-plane-app','control-plane-app',
 'Wave0ReadPilotController.overview','/api/platform/enforcement-activation/wave0','GET','TARGET_ONLY',
 'permission.enforcement_wave0.read',null,'[]'::jsonb,'WAVE0_READ_PILOT','R3_ROUTE_RESOURCE_RESOLVER',null,
 null,'phase7-build-fix18-wave0-instance-read-2026-08-07',
 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/Wave0ReadPilotController.java#overview',
 '3293882bb60c054c69d8a3085e62de26b0dc2d4cef6d61289f274b5bd1dc3925',now(),'phase7-build-fix18','phase7-build-fix18'
),
(
 'REST:GET:/api/platform/enforcement-activation/wave0/observations','REST','control-plane-app','control-plane-app',
 'Wave0ReadPilotController.observations','/api/platform/enforcement-activation/wave0/observations','GET','TARGET_ONLY',
 'permission.enforcement_wave0.read',null,'[]'::jsonb,'WAVE0_READ_PILOT','R3_ROUTE_RESOURCE_RESOLVER',null,
 null,'phase7-build-fix18-wave0-instance-read-2026-08-07',
 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/Wave0ReadPilotController.java#observations',
 '3293882bb60c054c69d8a3085e62de26b0dc2d4cef6d61289f274b5bd1dc3925',now(),'phase7-build-fix18','phase7-build-fix18'
),
(
 'REST:GET:/api/platform/enforcement-activation/wave0/read/permission-catalog','REST','control-plane-app','control-plane-app',
 'Wave0ReadPilotController.permissionCatalog','/api/platform/enforcement-activation/wave0/read/permission-catalog','GET','TARGET_ONLY',
 'permission.enforcement_wave0.read',null,'[]'::jsonb,'WAVE0_READ_PILOT','R3_ROUTE_RESOURCE_RESOLVER',null,
 null,'phase7-build-fix18-wave0-instance-read-2026-08-07',
 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/Wave0ReadPilotController.java#permissionCatalog',
 '3293882bb60c054c69d8a3085e62de26b0dc2d4cef6d61289f274b5bd1dc3925',now(),'phase7-build-fix18','phase7-build-fix18'
),
(
 'REST:GET:/api/platform/enforcement-activation/wave0/read/readiness-evidence/{type}/{evidenceId}','REST','control-plane-app','control-plane-app',
 'Wave0ReadPilotController.readinessEvidence','/api/platform/enforcement-activation/wave0/read/readiness-evidence/{type}/{evidenceId}','GET','TARGET_ONLY',
 'permission.enforcement_wave0.read',null,'[]'::jsonb,'WAVE0_READ_PILOT','R3_PATH_RESOURCE_RESOLVER',null,
 null,'phase7-build-fix18-wave0-instance-read-2026-08-07',
 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/Wave0ReadPilotController.java#readinessEvidence',
 '3293882bb60c054c69d8a3085e62de26b0dc2d4cef6d61289f274b5bd1dc3925',now(),'phase7-build-fix18','phase7-build-fix18'
),
(
 'REST:GET:/api/platform/enforcement-activation/wave0/read/runtime-status','REST','control-plane-app','control-plane-app',
 'Wave0ReadPilotController.runtimeStatus','/api/platform/enforcement-activation/wave0/read/runtime-status','GET','TARGET_ONLY',
 'permission.enforcement_wave0.read',null,'[]'::jsonb,'WAVE0_READ_PILOT','R3_ROUTE_RESOURCE_RESOLVER',null,
 null,'phase7-build-fix18-wave0-instance-read-2026-08-07',
 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/Wave0ReadPilotController.java#runtimeStatus',
 '3293882bb60c054c69d8a3085e62de26b0dc2d4cef6d61289f274b5bd1dc3925',now(),'phase7-build-fix18','phase7-build-fix18'
)
on conflict(entry_point_id) do update set
 display_name=excluded.display_name, route_pattern=excluded.route_pattern, http_method=excluded.http_method,
 authority_state=excluded.authority_state, target_permission_code=excluded.target_permission_code,
 legacy_authority_type=excluded.legacy_authority_type, legacy_authorities=excluded.legacy_authorities,
 resource_type=excluded.resource_type, resource_resolver_id=excluded.resource_resolver_id,
 exemption_reason=excluded.exemption_reason, migration_deadline=excluded.migration_deadline,
 manifest_revision=excluded.manifest_revision, source_ref=excluded.source_ref, source_hash=excluded.source_hash,
 last_verified_at=excluded.last_verified_at, updated_at=now(), updated_by=excluded.updated_by,
 version=permission_entry_point_inventory.version+1;

update rbac_policy_versions
set policy_version=policy_version+1, updated_at=now(), updated_by='phase7-build-fix18';
