package com.opensocket.aievent.core.iam.persistence.dao;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/** Authentication foundation mapper. Tenant methods require Phase 1A-2 TenantContext. */
public interface IamAuthenticationDao {
    Map<String,Object> findInstancePasswordPolicy();
    Map<String,Object> findTenantPasswordPolicy(@Param("tenantId") String tenantId);
    int insertTenantPasswordPolicy(@Param("row") Map<String,Object> row);
    int updateTenantPasswordPolicy(@Param("row") Map<String,Object> row,@Param("expectedVersion") long expectedVersion);

    Map<String,Object> findInstanceSessionPolicy();
    Map<String,Object> findTenantSessionPolicy(@Param("tenantId") String tenantId);
    int insertTenantSessionPolicy(@Param("row") Map<String,Object> row);
    int updateTenantSessionPolicy(@Param("row") Map<String,Object> row,@Param("expectedVersion") long expectedVersion);

    Map<String,Object> findCredential(@Param("subjectType") String subjectType,@Param("subjectId") String subjectId);
    int insertCredential(@Param("row") Map<String,Object> row);
    int updateCredential(@Param("row") Map<String,Object> row,@Param("expectedVersion") long expectedVersion);
    List<Map<String,Object>> findPasswordHistory(@Param("subjectType") String subjectType,@Param("subjectId") String subjectId,@Param("limit") int limit);
    int insertPasswordHistory(@Param("row") Map<String,Object> row);
    int trimPasswordHistory(@Param("subjectType") String subjectType,@Param("subjectId") String subjectId,@Param("keep") int keep);

    Map<String,Object> findActiveMfa(@Param("subjectType") String subjectType,@Param("subjectId") String subjectId);
    Map<String,Object> findMfaById(@Param("methodId") String methodId);
    List<Map<String,Object>> findAllMfa(@Param("subjectType") String subjectType,@Param("subjectId") String subjectId);
    int insertMfa(@Param("row") Map<String,Object> row);
    int updateMfa(@Param("row") Map<String,Object> row,@Param("expectedVersion") long expectedVersion);
    int deleteRecoveryCodes(@Param("subjectType") String subjectType,@Param("subjectId") String subjectId);
    int insertRecoveryCode(@Param("row") Map<String,Object> row);
    List<Map<String,Object>> findUnusedRecoveryCodes(@Param("subjectType") String subjectType,@Param("subjectId") String subjectId);
    boolean markRecoveryCodeUsed(@Param("recoveryCodeId") String recoveryCodeId,@Param("usedAt") Instant usedAt);

    Map<String,Object> findSecurityState(@Param("subjectType") String subjectType,@Param("subjectId") String subjectId);
    int insertSecurityState(@Param("row") Map<String,Object> row);
    int updateSecurityState(@Param("row") Map<String,Object> row,@Param("expectedVersion") long expectedVersion);
    int insertLoginAttempt(@Param("row") Map<String,Object> row);
    long countRecentFailures(@Param("normalizedUsername") String normalizedUsername,@Param("since") Instant since);
    long countRecentFailuresByIp(@Param("ipAddress") String ipAddress,@Param("since") Instant since);

    Map<String,Object> findUserSubject(@Param("normalizedUsername") String normalizedUsername);
    Map<String,Object> findRootSubject();
    int countActiveTenantMembership(@Param("tenantId") String tenantId,@Param("userId") String userId);

    long findGlobalSecurityEpoch();
    long findGlobalPrincipalSecurityEpoch(@Param("principalId") String principalId);
    long findTenantSecurityEpoch(@Param("tenantId") String tenantId);
    long findPrincipalSecurityEpoch(@Param("tenantId") String tenantId,@Param("principalId") String principalId);
    long incrementPrincipalSecurityEpoch(@Param("tenantId") String tenantId,@Param("principalId") String principalId,@Param("actorId") String actorId);
    int incrementTenantSecurityEpoch(@Param("tenantId") String tenantId,@Param("actorId") String actorId);

    Map<String,Object> findTenantSession(@Param("sessionId") String sessionId);
    Map<String,Object> findRootSession(@Param("sessionId") String sessionId);
    List<Map<String,Object>> findActiveTenantSessions(@Param("tenantId") String tenantId,@Param("subjectType") String subjectType,@Param("subjectId") String subjectId);
    List<Map<String,Object>> findActiveRootSessions(@Param("subjectId") String subjectId);
    int insertTenantSession(@Param("row") Map<String,Object> row);
    int updateTenantSession(@Param("row") Map<String,Object> row,@Param("expectedVersion") long expectedVersion);
    int insertRootSession(@Param("row") Map<String,Object> row);
    int updateRootSession(@Param("row") Map<String,Object> row,@Param("expectedVersion") long expectedVersion);
    int revokeAllTenantSessionsAcrossTenants(@Param("subjectType") String subjectType,@Param("subjectId") String subjectId,@Param("actorId") String actorId,@Param("reason") String reason,@Param("at") Instant at);
    int revokeAllTenantSessions(@Param("tenantId") String tenantId,@Param("subjectType") String subjectType,@Param("subjectId") String subjectId,@Param("actorId") String actorId,@Param("reason") String reason,@Param("at") Instant at);
    int revokeAllRootSessions(@Param("subjectId") String subjectId,@Param("actorId") String actorId,@Param("reason") String reason,@Param("at") Instant at);

    Map<String,Object> findRootBootstrapState();
    int updateRootBootstrapState(@Param("row") Map<String,Object> row,@Param("expectedVersion") long expectedVersion);
    Map<String,Object> findRootRecoveryGrant(@Param("grantId") String grantId);
    int insertRootRecoveryGrant(@Param("row") Map<String,Object> row);
    int updateRootRecoveryGrant(@Param("row") Map<String,Object> row,@Param("expectedVersion") long expectedVersion);

    Map<String,Object> findTenantReauthenticationGrant(@Param("grantId") String grantId);
    Map<String,Object> findRootReauthenticationGrant(@Param("grantId") String grantId);
    int insertTenantReauthenticationGrant(@Param("row") Map<String,Object> row);
    int updateTenantReauthenticationGrant(@Param("row") Map<String,Object> row,@Param("expectedVersion") long expectedVersion);
    int insertRootReauthenticationGrant(@Param("row") Map<String,Object> row);
    int updateRootReauthenticationGrant(@Param("row") Map<String,Object> row,@Param("expectedVersion") long expectedVersion);
}
