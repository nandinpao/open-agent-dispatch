package com.opensocket.aievent.database.persistence.domainevent.dao;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.opensocket.aievent.database.persistence.domainevent.po.OutboxEventPo;

@Mapper
public interface OutboxEventDao {
    int insertIgnore(@Param("event") OutboxEventPo event);
    OutboxEventPo findById(@Param("outboxId") String outboxId);
    OutboxEventPo findByEventId(@Param("eventId") String eventId);
    List<OutboxEventPo> claimDispatchable(
            @Param("now") OffsetDateTime now,
            @Param("limit") int limit,
            @Param("workerId") String workerId,
            @Param("claimUntil") OffsetDateTime claimUntil);
    int markPublished(
            @Param("outboxId") String outboxId,
            @Param("workerId") String workerId,
            @Param("claimUntil") OffsetDateTime claimUntil,
            @Param("publishedAt") OffsetDateTime publishedAt);
    int markRetry(
            @Param("outboxId") String outboxId,
            @Param("workerId") String workerId,
            @Param("claimUntil") OffsetDateTime claimUntil,
            @Param("attemptCount") int attemptCount,
            @Param("nextAttemptAt") OffsetDateTime nextAttemptAt,
            @Param("error") String error,
            @Param("updatedAt") OffsetDateTime updatedAt);
    int markDeadLetter(
            @Param("outboxId") String outboxId,
            @Param("workerId") String workerId,
            @Param("claimUntil") OffsetDateTime claimUntil,
            @Param("attemptCount") int attemptCount,
            @Param("error") String error,
            @Param("updatedAt") OffsetDateTime updatedAt);
    List<OutboxEventPo> recent(@Param("limit") int limit);
}
