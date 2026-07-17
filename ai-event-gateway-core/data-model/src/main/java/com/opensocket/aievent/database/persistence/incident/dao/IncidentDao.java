package com.opensocket.aievent.database.persistence.incident.dao;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.opensocket.aievent.database.persistence.incident.po.IncidentPo;

@Mapper
public interface IncidentDao {
    int upsert(@Param("incident") IncidentPo incident);
    int insert(@Param("incident") IncidentPo incident);
    IncidentPo findById(@Param("incidentId") String incidentId);
    IncidentPo findActiveByFingerprint(@Param("fingerprint") String fingerprint);
    IncidentPo findLatestByFingerprint(@Param("fingerprint") String fingerprint);
    List<IncidentPo> findActiveLastSeenBefore(@Param("cutoff") OffsetDateTime cutoff, @Param("limit") int limit);
    List<IncidentPo> findAll();
    List<IncidentPo> search(@Param("tenantId") String tenantId,
                             @Param("sourceSystem") String sourceSystem,
                             @Param("siteId") String siteId,
                             @Param("plantId") String plantId,
                             @Param("objectType") String objectType,
                             @Param("objectId") String objectId,
                             @Param("eventType") String eventType,
                             @Param("errorCode") String errorCode,
                             @Param("severity") String severity,
                             @Param("status") String status,
                             @Param("limit") int limit);
}
