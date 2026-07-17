package com.opensocket.aievent.database.persistence.eventprocessing.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.opensocket.aievent.database.persistence.eventprocessing.po.DedupStateSnapshotPo;

@Mapper
public interface DedupStateSnapshotDao {
    int upsert(@Param("snapshot") DedupStateSnapshotPo snapshot);
    int insertIfAbsent(@Param("snapshot") DedupStateSnapshotPo snapshot);
    DedupStateSnapshotPo findForUpdate(@Param("fingerprint") String fingerprint);
    DedupStateSnapshotPo findActiveByFingerprint(@Param("fingerprint") String fingerprint);
    int updateState(@Param("snapshot") DedupStateSnapshotPo snapshot);
    int attachIncident(@Param("fingerprint") String fingerprint,
                       @Param("activeIncidentId") String activeIncidentId,
                       @Param("expiresAt") java.time.OffsetDateTime expiresAt);
}
