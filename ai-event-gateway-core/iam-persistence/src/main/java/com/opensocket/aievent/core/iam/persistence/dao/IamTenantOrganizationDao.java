package com.opensocket.aievent.core.iam.persistence.dao;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/** Tenant-owned mapper. Namespace is classified by the tenant transaction interceptor. */
public interface IamTenantOrganizationDao {
    Map<String,Object> findTenantMembership(@Param("tenantId") String tenantId, @Param("userId") String userId);
    Map<String,Object> findTenantMembershipById(@Param("tenantId") String tenantId, @Param("membershipId") String membershipId);
    int insertTenantMembership(@Param("row") Map<String,Object> row);
    int updateTenantMembership(@Param("row") Map<String,Object> row, @Param("expectedVersion") long expectedVersion);
    int insertTenantMembershipEvidence(@Param("row") Map<String,Object> row);

    Map<String,Object> findDepartment(@Param("tenantId") String tenantId, @Param("departmentId") String departmentId);
    int countDepartmentByCode(@Param("tenantId") String tenantId, @Param("code") String code);
    List<Map<String,Object>> findActiveDepartmentsManagedByUser(@Param("tenantId") String tenantId, @Param("userId") String userId);
    List<Map<String,Object>> findActiveDepartmentsManagedByUserAcrossTenants(@Param("userId") String userId);
    long countActiveDepartmentDescendants(@Param("tenantId") String tenantId, @Param("departmentId") String departmentId);
    int insertDepartment(@Param("row") Map<String,Object> row);
    int updateDepartment(@Param("row") Map<String,Object> row, @Param("expectedVersion") long expectedVersion);
    long lockDepartmentHierarchy(@Param("tenantId") String tenantId);
    int deleteDepartmentClosure(@Param("tenantId") String tenantId);
    int insertDepartmentSelfClosure(@Param("tenantId") String tenantId);
    int insertDepartmentAncestorClosure(@Param("tenantId") String tenantId);
    List<Map<String,Object>> loadDepartmentNodes(@Param("tenantId") String tenantId);
    List<Map<String,Object>> loadDepartmentPath(@Param("tenantId") String tenantId, @Param("departmentId") String departmentId);

    Map<String,Object> findCurrentDepartmentRevision(@Param("tenantId") String tenantId, @Param("departmentId") String departmentId);
    int closeCurrentDepartmentRevision(@Param("tenantId") String tenantId, @Param("departmentId") String departmentId,
                                       @Param("validTo") Object validTo);
    int insertDepartmentRevision(@Param("row") Map<String,Object> row);

    Map<String,Object> findDepartmentMembership(@Param("tenantId") String tenantId,@Param("membershipId") String membershipId);
    List<Map<String,Object>> findDepartmentMemberships(@Param("tenantId") String tenantId, @Param("userId") String userId);
    Map<String,Object> findDepartmentMembershipByUserAndDepartment(@Param("tenantId") String tenantId, @Param("userId") String userId, @Param("departmentId") String departmentId);
    Map<String,Object> findStoredPrimaryDepartmentMembership(@Param("tenantId") String tenantId, @Param("userId") String userId);
    long countActiveDepartmentMemberships(@Param("tenantId") String tenantId, @Param("departmentId") String departmentId);
    int insertDepartmentMembership(@Param("row") Map<String,Object> row);
    int updateDepartmentMembership(@Param("row") Map<String,Object> row, @Param("expectedVersion") long expectedVersion);

    Map<String,Object> findGroup(@Param("tenantId") String tenantId, @Param("groupId") String groupId);
    int countGroupByCode(@Param("tenantId") String tenantId, @Param("code") String code);
    long countActiveGroupChildren(@Param("tenantId") String tenantId, @Param("groupId") String groupId);
    long countActiveGroupsByOwnerDepartment(@Param("tenantId") String tenantId, @Param("departmentId") String departmentId);
    int insertGroup(@Param("row") Map<String,Object> row);
    int updateGroup(@Param("row") Map<String,Object> row, @Param("expectedVersion") long expectedVersion);

    Map<String,Object> findGroupMembership(@Param("tenantId") String tenantId,@Param("membershipId") String membershipId);
    List<Map<String,Object>> findGroupMemberships(@Param("tenantId") String tenantId, @Param("userId") String userId);
    long countActiveGroupMemberships(@Param("tenantId") String tenantId, @Param("groupId") String groupId);
    int insertGroupMembership(@Param("row") Map<String,Object> row);
    int updateGroupMembership(@Param("row") Map<String,Object> row, @Param("expectedVersion") long expectedVersion);

    Map<String,Object> findSnapshotByHash(@Param("tenantId") String tenantId, @Param("contentHash") String contentHash);
    int insertSnapshot(@Param("row") Map<String,Object> row);
    long currentSecurityEpoch(@Param("tenantId") String tenantId);
    long incrementSecurityEpoch(@Param("tenantId") String tenantId, @Param("actorId") String actorId);
}
