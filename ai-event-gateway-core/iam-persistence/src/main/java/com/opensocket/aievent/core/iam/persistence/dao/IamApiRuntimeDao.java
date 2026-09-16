package com.opensocket.aievent.core.iam.persistence.dao;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.ibatis.annotations.Param;

/** Mapper boundary used only by the Phase 1A-7 runtime composition adapters. */
public interface IamApiRuntimeDao {
    int insertInstanceIdempotency(@Param("row") Map<String,Object> row);
    Map<String,Object> lockInstanceIdempotency(@Param("scopeId") String scopeId,@Param("actorId") String actorId,@Param("operation") String operation,@Param("key") String key);
    int completeInstanceIdempotency(@Param("row") Map<String,Object> row);
    int insertTenantIdempotency(@Param("row") Map<String,Object> row);
    Map<String,Object> lockTenantIdempotency(@Param("tenantId") String tenantId,@Param("actorId") String actorId,@Param("operation") String operation,@Param("key") String key);
    int completeTenantIdempotency(@Param("row") Map<String,Object> row);

    int insertLoginChallenge(@Param("row") Map<String,Object> row);
    Map<String,Object> findLoginChallenge(@Param("challengeId") String challengeId,@Param("challengeHash") String challengeHash,@Param("at") Instant at);
    Map<String,Object> consumeLoginChallenge(@Param("challengeId") String challengeId,@Param("challengeHash") String challengeHash,@Param("consumedAt") Instant consumedAt);

    List<Map<String,Object>> listTenants(@Param("text") String text,@Param("status") String status,@Param("offset") int offset,@Param("limit") int limit);
    List<Map<String,Object>> listRootTenantChoices();
    Map<String,Object> tenantWorkspaceSummary(@Param("tenantId") String tenantId);
    List<Map<String,Object>> listPlatformUsers(@Param("text") String text,@Param("status") String status,@Param("tenantId") String tenantId,@Param("afterUserId") String afterUserId,@Param("limit") int limit);
    Map<String,Object> findPlatformUser(@Param("userId") String userId);
    List<Map<String,Object>> listPlatformMemberships(@Param("userId") String userId,@Param("afterTenantId") String afterTenantId,@Param("limit") int limit);
    List<Map<String,Object>> listUsers(@Param("tenantId") String tenantId,@Param("text") String text,@Param("status") String status,@Param("membershipStatus") String membershipStatus,@Param("roleId") String roleId,@Param("departmentId") String departmentId,@Param("groupId") String groupId,@Param("signInState") String signInState,@Param("afterUserId") String afterUserId,@Param("limit") int limit);
    List<Map<String,Object>> listUsersWithin(@Param("tenantId") String tenantId,@Param("departmentIds") Set<String> departmentIds,@Param("groupIds") Set<String> groupIds,@Param("text") String text,@Param("status") String status,@Param("membershipStatus") String membershipStatus,@Param("roleId") String roleId,@Param("departmentId") String departmentId,@Param("groupId") String groupId,@Param("signInState") String signInState,@Param("afterUserId") String afterUserId,@Param("limit") int limit);
    List<Map<String,Object>> listAvailableUsers(@Param("tenantId") String tenantId,@Param("text") String text,@Param("afterUserId") String afterUserId,@Param("limit") int limit);
    Map<String,Object> findTenantUser(@Param("tenantId") String tenantId,@Param("userId") String userId);
    List<Map<String,Object>> findActiveUserOrganizationScopes(@Param("tenantId") String tenantId,@Param("userId") String userId);
    Map<String,Object> findUserAuthenticationReadiness(@Param("tenantId") String tenantId,@Param("userId") String userId);
    Map<String,Object> findMachineOwnershipImpact(@Param("tenantId") String tenantId,@Param("userId") String userId);
    Map<String,Object> transferMachineOwnership(@Param("tenantId") String tenantId,@Param("fromUserId") String fromUserId,@Param("toUserId") String toUserId,@Param("actorId") String actorId,@Param("reason") String reason);
    int insertActivationDelivery(@Param("row") Map<String,Object> row);
    int updateActivationDelivery(@Param("row") Map<String,Object> row);
    Map<String,Object> findLatestActivationDelivery(@Param("tenantId") String tenantId,@Param("userId") String userId);
    List<Map<String,Object>> listDepartments(@Param("tenantId") String tenantId,@Param("text") String text,@Param("status") String status,@Param("offset") int offset,@Param("limit") int limit);
    List<Map<String,Object>> listDepartmentsWithin(@Param("tenantId") String tenantId,@Param("departmentIds") Set<String> departmentIds,@Param("text") String text,@Param("status") String status,@Param("offset") int offset,@Param("limit") int limit);
    List<String> listDepartmentIdsWithinSubtrees(@Param("tenantId") String tenantId,@Param("rootDepartmentIds") Set<String> rootDepartmentIds);
    List<Map<String,Object>> listGroups(@Param("tenantId") String tenantId,@Param("text") String text,@Param("type") String type,@Param("status") String status,@Param("offset") int offset,@Param("limit") int limit);
    List<Map<String,Object>> listGroupsWithin(@Param("tenantId") String tenantId,@Param("groupIds") Set<String> groupIds,@Param("text") String text,@Param("type") String type,@Param("status") String status,@Param("offset") int offset,@Param("limit") int limit);
    Map<String,Object> departmentRetirementPreview(@Param("tenantId") String tenantId,@Param("departmentId") String departmentId);
    Map<String,Object> groupRetirementPreview(@Param("tenantId") String tenantId,@Param("groupId") String groupId);
    List<Map<String,Object>> listMemberships(@Param("tenantId") String tenantId,@Param("userId") String userId,@Param("afterMembershipId") String afterMembershipId,@Param("limit") int limit);
    Map<String,Object> findMembership(@Param("tenantId") String tenantId,@Param("membershipId") String membershipId);
    List<Map<String,Object>> listTenantMemberships(@Param("tenantId") String tenantId,@Param("afterMembershipId") String afterMembershipId,@Param("limit") int limit);
    List<Map<String,Object>> listDepartmentMembers(@Param("tenantId") String tenantId,@Param("departmentId") String departmentId,@Param("afterMembershipId") String afterMembershipId,@Param("limit") int limit);
    List<Map<String,Object>> listGroupMembers(@Param("tenantId") String tenantId,@Param("groupId") String groupId,@Param("afterMembershipId") String afterMembershipId,@Param("limit") int limit);
    List<Map<String,Object>> listEligibleDepartmentManagers(@Param("tenantId") String tenantId,@Param("departmentId") String departmentId,@Param("text") String text,@Param("afterUserId") String afterUserId,@Param("limit") int limit);
    List<Map<String,Object>> listRoles(@Param("tenantId") String tenantId,@Param("text") String text,@Param("type") String type,@Param("status") String status,@Param("offset") int offset,@Param("limit") int limit);
    List<Map<String,Object>> listResponsibilityTemplates(@Param("tenantId") String tenantId,@Param("text") String text,@Param("status") String status,@Param("riskLevel") String riskLevel,@Param("scopeType") String scopeType,@Param("offset") int offset,@Param("limit") int limit);
    List<Map<String,Object>> listAccessAssignments(@Param("tenantId") String tenantId,@Param("text") String text,@Param("status") String status,@Param("principalType") String principalType,@Param("scopeType") String scopeType,@Param("lifecycle") String lifecycle,@Param("afterBindingId") String afterBindingId,@Param("limit") int limit);
    Map<String,Object> accessLifecycleSummary(@Param("tenantId") String tenantId);
    List<Map<String,Object>> listAccessReviewCandidates(@Param("tenantId") String tenantId,@Param("reason") String reason,@Param("riskLevel") String riskLevel,@Param("afterBindingId") String afterBindingId,@Param("limit") int limit);
    Map<String,Object> securityWorkspaceSummary(@Param("tenantId") String tenantId);
    List<Map<String,Object>> listCredentialApiProducts();
    List<Map<String,Object>> listCredentialAudiences();
    List<Map<String,Object>> listCredentialMachineScopes();
    List<Map<String,Object>> listCredentialSourceSystems(@Param("tenantId") String tenantId);
    List<Map<String,Object>> listHumanReadableAudit(@Param("tenantId") String tenantId,@Param("category") String category,@Param("outcome") String outcome,@Param("afterEventId") String afterEventId,@Param("limit") int limit);
    List<Map<String,Object>> listSecurityPolicyRevisions(@Param("tenantId") String tenantId,@Param("policyKind") String policyKind,@Param("afterRevisionId") String afterRevisionId,@Param("limit") int limit);
    Map<String,Object> findSecurityPolicyRevision(@Param("tenantId") String tenantId,@Param("revisionId") String revisionId);
    int insertSecurityPolicyRevision(@Param("row") Map<String,Object> row);
    List<Map<String,Object>> listPlatformRoles(@Param("text") String text,@Param("type") String type,@Param("status") String status,@Param("offset") int offset,@Param("limit") int limit);
    List<Map<String,Object>> listPermissions(@Param("text") String text,@Param("scopeType") String scopeType,@Param("offset") int offset,@Param("limit") int limit);
    List<Map<String,Object>> listRolePermissions(@Param("tenantId") String tenantId,@Param("roleId") String roleId);
    List<Map<String,Object>> listRoleBindings(@Param("tenantId") String tenantId,@Param("roleId") String roleId,@Param("principalId") String principalId,@Param("afterBindingId") String afterBindingId,@Param("limit") int limit);
    List<Map<String,Object>> listSessions(@Param("tenantId") String tenantId,@Param("subjectId") String subjectId,@Param("afterSessionId") String afterSessionId,@Param("limit") int limit);
    List<Map<String,Object>> listServiceAccounts(@Param("tenantId") String tenantId,@Param("status") String status,@Param("afterId") String afterId,@Param("limit") int limit);
    List<Map<String,Object>> listServiceAccountCredentials(@Param("tenantId") String tenantId,@Param("serviceAccountId") String serviceAccountId,@Param("status") String status,@Param("afterId") String afterId,@Param("limit") int limit);
    List<Map<String,Object>> listTokens(@Param("tenantId") String tenantId,@Param("principalId") String principalId,@Param("status") String status,@Param("afterId") String afterId,@Param("limit") int limit);
    List<Map<String,Object>> listIdentityAudit(@Param("tenantId") String tenantId,@Param("eventType") String eventType,@Param("actorId") String actorId,@Param("targetId") String targetId,@Param("afterEventId") String afterEventId,@Param("limit") int limit);

    Map<String,Object> resolveOneTimeTokenTenant(@Param("tokenPrefix") String tokenPrefix,@Param("tokenType") String tokenType);
    Map<String,Object> findUserByNormalizedUsername(@Param("normalizedUsername") String normalizedUsername);
    Map<String,Object> findCredentialLink(@Param("providerType") String providerType,@Param("normalizedProviderSubject") String normalizedProviderSubject);
    Map<String,Object> findCredentialLinkBySubject(@Param("providerType") String providerType,@Param("subjectId") String subjectId);
    int touchCredentialLink(@Param("credentialLinkId") String credentialLinkId,@Param("authenticatedAt") Instant authenticatedAt,@Param("updatedBy") String updatedBy,@Param("expectedVersion") long expectedVersion);
    int replaceCredentialLink(@Param("credentialLinkId") String credentialLinkId,@Param("replacedAt") Instant replacedAt,@Param("updatedBy") String updatedBy,@Param("expectedVersion") long expectedVersion);
    Map<String,Object> findPasswordPolicy(@Param("tenantId") String tenantId);
    int updatePasswordPolicy(@Param("row") Map<String,Object> row,@Param("expectedVersion") long expectedVersion);
    Map<String,Object> findMfaPolicy(@Param("tenantId") String tenantId);
    int updateMfaPolicy(@Param("row") Map<String,Object> row,@Param("expectedVersion") long expectedVersion);
    Map<String,Object> findSessionPolicy(@Param("tenantId") String tenantId);
    int updateSessionPolicy(@Param("row") Map<String,Object> row,@Param("expectedVersion") long expectedVersion);
    Map<String,Object> findTokenPolicy(@Param("tenantId") String tenantId);
    int updateTokenPolicy(@Param("row") Map<String,Object> row,@Param("expectedVersion") long expectedVersion);
    List<Map<String,Object>> activeTenantChoices(@Param("userId") String userId);
    List<String> activeTenantAuthorityRoleCodes(@Param("tenantId") String tenantId,@Param("userId") String userId,@Param("at") Instant at);
    Map<String,Object> findUiIdentity(@Param("userId") String userId);

    Integer acquireRootInstallationLock();
    List<String> activeInstancePermissionCodes();
    int updateRootInstallationIdentity(@Param("row") Map<String,Object> row);
    int updateRootInstallationState(@Param("row") Map<String,Object> row);
    int insertRootInstallationEvent(@Param("row") Map<String,Object> row);
    int insertRootInstallationSecretIgnored(@Param("row") Map<String,Object> row);
    int updateInitialRootPasswordState(@Param("row") Map<String,Object> row);
    int updateRootLastLogin(@Param("row") Map<String,Object> row);

    // P2.4 Enterprise Authentication Federation
    List<Map<String,Object>> listAuthenticationProviders(@Param("tenantId") String tenantId);
    Map<String,Object> findAuthenticationProvider(@Param("tenantId") String tenantId,@Param("providerId") String providerId);
    int insertAuthenticationProvider(@Param("row") Map<String,Object> row);
    int updateAuthenticationProvider(@Param("row") Map<String,Object> row,@Param("expectedVersion") long expectedVersion);
    int changeAuthenticationProviderStatus(@Param("tenantId") String tenantId,@Param("providerId") String providerId,@Param("status") String status,@Param("updatedAt") Instant updatedAt,@Param("updatedBy") String updatedBy,@Param("expectedVersion") long expectedVersion);
    Map<String,Object> findFederationPolicy(@Param("tenantId") String tenantId);
    int updateFederationPolicy(@Param("row") Map<String,Object> row,@Param("expectedVersion") long expectedVersion);
    List<Map<String,Object>> listUserExternalIdentityLinks(@Param("tenantId") String tenantId,@Param("userId") String userId);
    Map<String,Object> findExternalCredentialLink(@Param("tenantId") String tenantId,@Param("providerId") String providerId,@Param("providerSubject") String providerSubject);
    Map<String,Object> findExternalCredentialLinkById(@Param("tenantId") String tenantId,@Param("credentialLinkId") String credentialLinkId);
    Map<String,Object> findExternalCredentialLinkByUserProvider(@Param("tenantId") String tenantId,@Param("providerId") String providerId,@Param("userId") String userId);
    Map<String,Object> resolveFederationTenant(@Param("tenant") String tenant);
    Map<String,Object> findTenantUserByNormalizedEmail(@Param("tenantId") String tenantId,@Param("normalizedEmail") String normalizedEmail);
    int countActiveFederatedTenantMembership(@Param("tenantId") String tenantId,@Param("userId") String userId,@Param("at") Instant at);
    int insertExternalCredentialLink(@Param("row") Map<String,Object> row);
    int disableExternalCredentialLink(@Param("tenantId") String tenantId,@Param("credentialLinkId") String credentialLinkId,@Param("disabledAt") Instant disabledAt,@Param("updatedBy") String updatedBy,@Param("expectedVersion") long expectedVersion);
    int updateExternalCredentialLinkAuthentication(@Param("tenantId") String tenantId,@Param("credentialLinkId") String credentialLinkId,@Param("authenticatedAt") Instant authenticatedAt,@Param("upstreamUsername") String upstreamUsername,@Param("upstreamEmail") String upstreamEmail,@Param("upstreamEmailVerified") Boolean upstreamEmailVerified,@Param("updatedBy") String updatedBy,@Param("expectedVersion") long expectedVersion);
    int insertOidcLoginAttempt(@Param("row") Map<String,Object> row);
    Map<String,Object> findOidcLoginAttemptByStateHash(@Param("stateHash") String stateHash);
    int consumeOidcLoginAttempt(@Param("tenantId") String tenantId,@Param("attemptId") String attemptId,@Param("consumedAt") Instant consumedAt,@Param("expectedVersion") long expectedVersion);
    int failOidcLoginAttempt(@Param("tenantId") String tenantId,@Param("attemptId") String attemptId,@Param("failedAt") Instant failedAt,@Param("failureCode") String failureCode,@Param("expectedVersion") long expectedVersion);
}

