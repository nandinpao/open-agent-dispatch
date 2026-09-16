-- RS9 Build Fix 2: refresh R3 entry-point source evidence after qualified Maven compile repairs.
-- No permission, scope, role or business-resource semantics change in this migration.
select set_config('app.current_tenant_id','INSTANCE',true);
select set_config('app.current_actor_id','rs9-build-fix2',true);
select set_config('app.current_audit_reason','Refresh R3 controller source hashes after RS9 qualified-build compile repair',true);

update permission_entry_point_inventory
set source_hash='223fd5ce741a24ea456d6d027faa32095339a88245c4049dacbb38f8a4f18f19',
    manifest_revision='rs9-build-fix2-controller-source-hash-2026-08-12',
    last_verified_at=now(), updated_at=now(), updated_by='rs9-build-fix2', version=version+1
where source_ref like 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentGovernanceController.java#%';

update permission_entry_point_inventory
set source_hash='f7732f105cd0a2ef5b510ad6012a1f007e9538b409f56981335f9c0c7fe52205',
    manifest_revision='rs9-build-fix2-controller-source-hash-2026-08-12',
    last_verified_at=now(), updated_at=now(), updated_by='rs9-build-fix2', version=version+1
where source_ref like 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentRemediationController.java#%';

update permission_entry_point_inventory
set source_hash='9a1128cf0f0ceac7df8b2736a587f11c71d8aa973907e51fb1d22354fc27fc55',
    manifest_revision='rs9-build-fix2-controller-source-hash-2026-08-12',
    last_verified_at=now(), updated_at=now(), updated_by='rs9-build-fix2', version=version+1
where source_ref like 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/IssueProjectionStateController.java#%';

-- Source-evidence changes participate in policy-version invalidation so stale policy snapshots cannot survive a build-fix cutover.
update rbac_policy_versions
set policy_version=policy_version+1, updated_at=now(), updated_by='rs9-build-fix2';
