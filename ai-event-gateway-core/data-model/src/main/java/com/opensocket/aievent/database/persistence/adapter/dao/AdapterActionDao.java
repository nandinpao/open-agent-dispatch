package com.opensocket.aievent.database.persistence.adapter.dao;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.opensocket.aievent.database.persistence.adapter.po.AdapterActionPo;

@Mapper
public interface AdapterActionDao {
    int upsert(@Param("action") AdapterActionPo action);
    int insert(@Param("action") AdapterActionPo action);
    AdapterActionPo findById(@Param("actionId") String actionId);
    AdapterActionPo findByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);
    List<AdapterActionPo> findByIncidentId(@Param("incidentId") String incidentId, @Param("limit") int limit);
    List<AdapterActionPo> findByTaskId(@Param("taskId") String taskId, @Param("limit") int limit);
    List<AdapterActionPo> findByStatus(@Param("status") String status, @Param("limit") int limit);
    List<AdapterActionPo> findExecutablePending(@Param("now") OffsetDateTime now, @Param("limit") int limit);
    AdapterActionPo claimNext(
            @Param("adapterType") String adapterType,
            @Param("workerId") String workerId,
            @Param("now") OffsetDateTime now,
            @Param("leaseExpiresAt") OffsetDateTime leaseExpiresAt);
    AdapterActionPo extendLease(
            @Param("actionId") String actionId,
            @Param("workerId") String workerId,
            @Param("expectedLeaseExpiresAt") OffsetDateTime expectedLeaseExpiresAt,
            @Param("now") OffsetDateTime now,
            @Param("leaseExpiresAt") OffsetDateTime leaseExpiresAt);
    int saveClaimed(
            @Param("action") AdapterActionPo action,
            @Param("workerId") String workerId,
            @Param("expectedLeaseExpiresAt") OffsetDateTime expectedLeaseExpiresAt,
            @Param("now") OffsetDateTime now);
    int recoverExpiredClaim(
            @Param("action") AdapterActionPo action,
            @Param("workerId") String workerId,
            @Param("expectedLeaseExpiresAt") OffsetDateTime expectedLeaseExpiresAt,
            @Param("observedAt") OffsetDateTime observedAt);
    List<AdapterActionPo> recent(@Param("limit") int limit);
}
