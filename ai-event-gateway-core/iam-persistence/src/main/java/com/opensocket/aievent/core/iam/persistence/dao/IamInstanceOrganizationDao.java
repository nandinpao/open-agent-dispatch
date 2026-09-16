package com.opensocket.aievent.core.iam.persistence.dao;

import java.util.Map;
import org.apache.ibatis.annotations.Param;

/** Instance-authorized tenant registry mapper; it is not subject to tenant RLS context. */
public interface IamInstanceOrganizationDao {
    String initializeTenantTransactionContext(@Param("tenantId") String tenantId, @Param("actorId") String actorId);
    Map<String,Object> findTenant(@Param("tenantId") String tenantId);
    int countTenantByCode(@Param("code") String code);
    int insertTenant(@Param("row") Map<String,Object> row);
    int updateTenant(@Param("row") Map<String,Object> row, @Param("expectedVersion") long expectedVersion);
}
