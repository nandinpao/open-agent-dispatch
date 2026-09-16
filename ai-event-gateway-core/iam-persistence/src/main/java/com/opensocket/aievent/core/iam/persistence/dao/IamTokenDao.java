package com.opensocket.aievent.core.iam.persistence.dao;
import java.time.Instant;import java.util.*;import org.apache.ibatis.annotations.Param;
public interface IamTokenDao {
 Map<String,Object> findTokenById(@Param("tenantId")String tenantId,@Param("tokenId")String tokenId);
 Map<String,Object> findTokenByPrefix(@Param("tenantId")String tenantId,@Param("prefix")String prefix);
 Map<String,Object> findPersonalAccessTokenDirectory(@Param("prefix")String prefix);
 List<Map<String,Object>> findActiveTokens(@Param("tenantId")String tenantId,@Param("principalType")String principalType,@Param("principalId")String principalId,@Param("tokenType")String tokenType);
 long countActiveTokens(@Param("tenantId")String tenantId,@Param("principalType")String principalType,@Param("principalId")String principalId,@Param("tokenType")String tokenType);
 int insertToken(@Param("row")Map<String,Object> row);int updateToken(@Param("row")Map<String,Object> row,@Param("expectedVersion")long expectedVersion);
 Map<String,Object> findServiceAccount(@Param("tenantId")String tenantId,@Param("serviceAccountId")String serviceAccountId);
 int insertServiceAccount(@Param("row")Map<String,Object> row);int updateServiceAccount(@Param("row")Map<String,Object> row,@Param("expectedVersion")long expectedVersion);
 Map<String,Object> findServiceAccountCredential(@Param("tenantId")String tenantId,@Param("credentialId")String credentialId);
 Map<String,Object> findServiceAccountCredentialByClientId(@Param("tenantId")String tenantId,@Param("clientId")String clientId);
 long countUsableServiceAccountCredentials(@Param("tenantId")String tenantId,@Param("serviceAccountId")String serviceAccountId);
 int insertServiceAccountCredential(@Param("row")Map<String,Object> row);
 int updateServiceAccountCredential(@Param("row")Map<String,Object> row,@Param("expectedVersion")long expectedVersion);
 int recordServiceAccountCredentialUsage(@Param("tenantId")String tenantId,@Param("credentialId")String credentialId,@Param("usedAt")Instant usedAt);
 Map<String,Object> findTokenPolicy(@Param("tenantId")String tenantId);
 List<String> findEffectivePermissions(@Param("tenantId")String tenantId,@Param("principalType")String principalType,@Param("principalId")String principalId,@Param("at")Instant at);
 int countActivePrincipal(@Param("tenantId")String tenantId,@Param("principalType")String principalType,@Param("principalId")String principalId);
 int countEligibleOneTimeSubject(@Param("tenantId")String tenantId,@Param("principalId")String principalId,@Param("tokenType")String tokenType);
 int countActiveOwner(@Param("tenantId")String tenantId,@Param("ownerUserId")String ownerUserId,@Param("ownerDepartmentId")String ownerDepartmentId);
 boolean tryAcquireRateLimit(@Param("tenantId")String tenantId,@Param("rateLimitKey")String rateLimitKey,@Param("windowStart")Instant windowStart,@Param("limit")int limit);
 int insertUsageEvidence(@Param("row")Map<String,Object> row);
 Map<String,Object> findMachineCredentialDirectory(@Param("clientId")String clientId);
 Map<String,Object> findActiveMachineSigningKey();
 List<Map<String,Object>> findPublishableMachineSigningKeys(@Param("at")Instant at);
 Map<String,Object> activateMachineSigningKey(@Param("row")Map<String,Object> row,@Param("at")Instant at,@Param("previousVerifyUntil")Instant previousVerifyUntil);
 boolean tryAcquireMachineOauthRateLimit(@Param("rateKey")String rateKey,@Param("windowStart")Instant windowStart,@Param("cleanupBefore")Instant cleanupBefore,@Param("limit")int limit);
 int insertMachineTokenAudit(@Param("row")Map<String,Object> row);
 int insertMachineResourceAccessAudit(@Param("row")Map<String,Object> row);

}
