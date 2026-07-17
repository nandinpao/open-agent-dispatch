package com.opensocket.aievent.database.persistence.integrationevent.dao;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.opensocket.aievent.database.persistence.integrationevent.po.IntegrationEventPo;
import com.opensocket.aievent.database.persistence.integrationevent.po.StatusCountPo;

@Mapper
public interface IntegrationEventDao {
    int insertIgnore(@Param("event") IntegrationEventPo event);
    IntegrationEventPo findByEventId(@Param("eventId") String eventId);
    List<IntegrationEventPo> claimDispatchable(
            @Param("now") OffsetDateTime now,
            @Param("limit") int limit,
            @Param("workerId") String workerId,
            @Param("claimUntil") OffsetDateTime claimUntil);
    int markDelivered(
            @Param("integrationEventId") String integrationEventId,
            @Param("workerId") String workerId,
            @Param("claimUntil") OffsetDateTime claimUntil,
            @Param("deliveredAt") OffsetDateTime deliveredAt);
    int markRetry(
            @Param("integrationEventId") String integrationEventId,
            @Param("workerId") String workerId,
            @Param("claimUntil") OffsetDateTime claimUntil,
            @Param("attemptCount") int attemptCount,
            @Param("nextAttemptAt") OffsetDateTime nextAttemptAt,
            @Param("error") String error,
            @Param("updatedAt") OffsetDateTime updatedAt);
    int markDeadLetter(
            @Param("integrationEventId") String integrationEventId,
            @Param("workerId") String workerId,
            @Param("claimUntil") OffsetDateTime claimUntil,
            @Param("attemptCount") int attemptCount,
            @Param("error") String error,
            @Param("updatedAt") OffsetDateTime updatedAt);
    List<StatusCountPo> statusCounts();
}
