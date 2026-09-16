package com.opensocket.aievent.core.iam.api.security;
public final class IamPermissions {
    private IamPermissions() {}
    public static final String TENANT_READ="instance.tenant.read", TENANT_MANAGE="instance.tenant.manage";
    public static final String PLATFORM_USER_READ="identity.platform_user.read", PLATFORM_USER_CREATE="identity.platform_user.create", PLATFORM_USER_UPDATE="identity.platform_user.update", PLATFORM_USER_SECURITY="identity.platform_user.security", PLATFORM_ADMIN_MANAGE="identity.platform_admin.manage";
    public static final String TENANT_MEMBERSHIP_READ="identity.tenant_membership.read", TENANT_MEMBERSHIP_MANAGE="identity.tenant_membership.manage";
    public static final String PLATFORM_ROLE_READ="identity.platform_role.read", PLATFORM_ROLE_MANAGE="identity.platform_role.manage";
    public static final String TENANT_ROLE_READ="identity.tenant_role.read", TENANT_ROLE_MANAGE="identity.tenant_role.manage";
    public static final String ROLE_PERMISSION_MANAGE="identity.role_permission.manage";
    public static final String ROLE_BINDING_READ="identity.role_binding.read", ROLE_BINDING_MANAGE="identity.role_binding.manage";
    public static final String ROLE_ACCESS_REVIEW_MANAGE="identity.role_access_review.manage";
    public static final String ROLE_APPROVAL_READ="identity.role_approval.read", ROLE_APPROVAL_REQUEST="identity.role_approval.request", ROLE_APPROVAL_APPROVE="identity.role_approval.approve";
    public static final String USER_READ="identity.user.read", USER_CREATE="identity.user.create", USER_UPDATE="identity.user.update";
    public static final String DEPARTMENT_READ="identity.department.read", DEPARTMENT_MANAGE="identity.department.manage";
    public static final String GROUP_READ="identity.group.read", GROUP_MANAGE="identity.group.manage";
    public static final String MEMBERSHIP_MANAGE="identity.membership.manage";
    public static final String POLICY_READ="security.policy.read", POLICY_MANAGE="security.policy.manage";
    public static final String SESSION_READ="security.session.read", SESSION_REVOKE="security.session.revoke";
    public static final String TOKEN_READ="security.token.read", TOKEN_MANAGE="security.token.manage", MFA_RESET="security.mfa.reset";
    public static final String AUDIT_READ="audit.identity.read", AUDIT_EXPORT="audit.identity.export";
    public static final String PERMISSION_CATALOG_READ="permission.catalog.read", PERMISSION_CATALOG_MANAGE="permission.catalog.manage", PERMISSION_CATALOG_PUBLISH="permission.catalog.publish";
    public static final String ENTRY_POINT_READ="permission.entry_point.read", ENTRY_POINT_MANAGE="permission.entry_point.manage", LEGACY_MAPPING_MANAGE="permission.legacy_mapping.manage", BYPASS_MANAGE="permission.bypass.manage";
    public static final String PERMISSION_MANIFEST_READ="permission.manifest.read", PERMISSION_MANIFEST_REGISTER="permission.manifest.register", PERMISSION_COVERAGE_READ="permission.coverage.read";
    public static final String SHADOW_READ="permission.shadow.read", SHADOW_MANAGE="permission.shadow.manage";
    public static final String MISMATCH_READ="permission.mismatch.read", MISMATCH_MANAGE="permission.mismatch.manage", MISMATCH_WAIVE="permission.mismatch.waive";
    public static final String DOMAIN_READINESS_READ="permission.domain_readiness.read", DOMAIN_READINESS_EVALUATE="permission.domain_readiness.evaluate";
    public static final String PHASE6_ELIGIBILITY_READ="permission.phase6_eligibility.read", PHASE6_ELIGIBILITY_EVALUATE="permission.phase6_eligibility.evaluate";
    public static final String PIPELINE_READ="permission.pipeline.read", PIPELINE_MANAGE="permission.pipeline.manage";
    public static final String STORAGE_READ="permission.storage.read", STORAGE_MANAGE="permission.storage.manage";
    public static final String RUNTIME_EVIDENCE_READ="permission.runtime_evidence.read", RUNTIME_EVIDENCE_GENERATE="permission.runtime_evidence.generate";
    public static final String ENFORCEMENT_CUTOVER_READ="permission.enforcement_cutover.read";
    public static final String ENFORCEMENT_CUTOVER_PLAN="permission.enforcement_cutover.plan";
    public static final String ENFORCEMENT_CUTOVER_REVIEW="permission.enforcement_cutover.review";
    public static final String ENFORCEMENT_CUTOVER_APPROVE="permission.enforcement_cutover.approve";
    public static final String ENFORCEMENT_CUTOVER_PUBLISH="permission.enforcement_cutover.publish";
    public static final String ENFORCEMENT_WAVE0_READ="permission.enforcement_wave0.read";
    public static final String ENFORCEMENT_WAVE0_OPERATE="permission.enforcement_wave0.operate";

}
