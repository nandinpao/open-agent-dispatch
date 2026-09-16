-- Phase 7 Build Fix 16: repair PostgreSQL advisory lock result mapping and make dashboard reads Tenant-addressed.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','phase7-build-fix16',true);
select set_config('app.current_audit_reason','Repair department advisory lock mapping and canonical Tenant dashboard routes',true);

update permission_entry_point_inventory
set authority_state='EXEMPT', target_permission_code=null, legacy_authority_type=null,
    legacy_authorities='[]'::jsonb, resource_resolver_id='NONE',
    exemption_reason='Superseded by explicit Tenant-addressed dashboard route',
    migration_deadline=null, last_verified_at=now(), updated_at=now(),
    updated_by='phase7-build-fix16', version=version+1
where entry_point_id in (
    'REST:GET:/admin/dashboard/snapshot',
    'REST:GET:/admin/agents/runtime-view',
    'REST:GET:/admin/tasks/runtime-view',
    'REST:GET:/admin/security-events',
    'REST:GET:/admin/agent-governance/summary'
);

insert into permission_entry_point_inventory(
 entry_point_id,entry_point_type,application_id,owner_module,display_name,route_pattern,http_method,authority_state,
 target_permission_code,legacy_authority_type,legacy_authorities,resource_type,resource_resolver_id,exemption_reason,
 migration_deadline,manifest_revision,source_ref,source_hash,last_verified_at,created_by,updated_by)
values
(
 'REST:GET:/admin/tenants/{tenantId}/dashboard/snapshot','REST','control-plane-app','control-plane-app',
 'CoreDashboardController.snapshot','/admin/tenants/{tenantId}/dashboard/snapshot','GET','TARGET_ONLY',
 'admin.core.dashboard.snapshot',null,'[]'::jsonb,'CORE_DASHBOARD','R3_PATH_RESOURCE_RESOLVER',null,
 null,'phase7-build-fix16-dashboard-tenant-routes-2026-08-07',
 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/CoreDashboardController.java#snapshot',
 'f29885c344978b87b5b698bb23635c2d35a530d3094c45ff256128a45c7bce34',now(),'phase7-build-fix16','phase7-build-fix16'
),
(
 'REST:GET:/admin/tenants/{tenantId}/agents/runtime-view','REST','control-plane-app','control-plane-app',
 'CoreDashboardController.agentRuntimeView','/admin/tenants/{tenantId}/agents/runtime-view','GET','TARGET_ONLY',
 'admin.core.dashboard.agent.runtime.view',null,'[]'::jsonb,'CORE_DASHBOARD','R3_PATH_RESOURCE_RESOLVER',null,
 null,'phase7-build-fix16-dashboard-tenant-routes-2026-08-07',
 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/CoreDashboardController.java#agentRuntimeView',
 'f29885c344978b87b5b698bb23635c2d35a530d3094c45ff256128a45c7bce34',now(),'phase7-build-fix16','phase7-build-fix16'
),
(
 'REST:GET:/admin/tenants/{tenantId}/tasks/runtime-view','REST','control-plane-app','control-plane-app',
 'CoreDashboardController.tasksRuntimeView','/admin/tenants/{tenantId}/tasks/runtime-view','GET','TARGET_ONLY',
 'admin.core.dashboard.tasks.runtime.view',null,'[]'::jsonb,'CORE_DASHBOARD','R3_PATH_RESOURCE_RESOLVER',null,
 null,'phase7-build-fix16-dashboard-tenant-routes-2026-08-07',
 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/CoreDashboardController.java#tasksRuntimeView',
 'f29885c344978b87b5b698bb23635c2d35a530d3094c45ff256128a45c7bce34',now(),'phase7-build-fix16','phase7-build-fix16'
),
(
 'REST:GET:/admin/tenants/{tenantId}/security-events','REST','control-plane-app','control-plane-app',
 'CoreDashboardController.securityEvents','/admin/tenants/{tenantId}/security-events','GET','TARGET_ONLY',
 'admin.core.dashboard.security.events',null,'[]'::jsonb,'CORE_DASHBOARD','R3_PATH_RESOURCE_RESOLVER',null,
 null,'phase7-build-fix16-dashboard-tenant-routes-2026-08-07',
 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/CoreDashboardController.java#securityEvents',
 'f29885c344978b87b5b698bb23635c2d35a530d3094c45ff256128a45c7bce34',now(),'phase7-build-fix16','phase7-build-fix16'
),
(
 'REST:GET:/admin/tenants/{tenantId}/agent-governance/summary','REST','control-plane-app','control-plane-app',
 'CoreDashboardController.agentGovernanceSummary','/admin/tenants/{tenantId}/agent-governance/summary','GET','TARGET_ONLY',
 'admin.core.dashboard.agent.governance.summary',null,'[]'::jsonb,'CORE_DASHBOARD','R3_PATH_RESOURCE_RESOLVER',null,
 null,'phase7-build-fix16-dashboard-tenant-routes-2026-08-07',
 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/CoreDashboardController.java#agentGovernanceSummary',
 'f29885c344978b87b5b698bb23635c2d35a530d3094c45ff256128a45c7bce34',now(),'phase7-build-fix16','phase7-build-fix16'
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
set policy_version=policy_version+1, updated_at=now(), updated_by='phase7-build-fix16';
