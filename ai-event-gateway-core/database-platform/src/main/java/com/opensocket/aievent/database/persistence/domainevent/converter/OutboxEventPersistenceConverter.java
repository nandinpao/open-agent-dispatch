package com.opensocket.aievent.database.persistence.domainevent.converter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import com.opensocket.aievent.database.persistence.spi.DatabasePersistenceConverter;
import com.opensocket.aievent.database.persistence.domainevent.po.OutboxEventPo;
import com.opensocket.aievent.core.outbox.OutboxEventRecord;
import com.opensocket.aievent.core.outbox.OutboxEventStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@DatabasePersistenceConverter
@ConditionalOnProperty(prefix="core.outbox", name="store", havingValue="MYBATIS")
public class OutboxEventPersistenceConverter {


    public OutboxEventPo toPo(OutboxEventRecord e){OutboxEventPo r=new OutboxEventPo();r.setOutboxId(e.getOutboxId());r.setEventId(e.getEventId());r.setEventType(e.getEventType());r.setAggregateType(e.getAggregateType());r.setAggregateId(e.getAggregateId());r.setPayloadJson(e.getPayloadJson());r.setStatus(e.getStatus()==null?null:e.getStatus().name());r.setAttemptCount(e.getAttemptCount());r.setNextAttemptAt(e.getNextAttemptAt());r.setLastError(e.getLastError());r.setClaimedBy(e.getClaimedBy());r.setClaimUntil(e.getClaimUntil());r.setCreatedAt(e.getCreatedAt());r.setPublishedAt(e.getPublishedAt());r.setUpdatedAt(e.getUpdatedAt());return r;}

    public OutboxEventRecord toDomain(OutboxEventPo r){OutboxEventRecord e=new OutboxEventRecord();e.setOutboxId(r.getOutboxId());e.setEventId(r.getEventId());e.setEventType(r.getEventType());e.setAggregateType(r.getAggregateType());e.setAggregateId(r.getAggregateId());e.setPayloadJson(r.getPayloadJson());e.setStatus(r.getStatus()==null?null:OutboxEventStatus.valueOf(r.getStatus()));e.setAttemptCount(r.getAttemptCount());e.setNextAttemptAt(r.getNextAttemptAt());e.setLastError(r.getLastError());e.setClaimedBy(r.getClaimedBy());e.setClaimUntil(r.getClaimUntil());e.setCreatedAt(r.getCreatedAt());e.setPublishedAt(r.getPublishedAt());e.setUpdatedAt(r.getUpdatedAt());return e;}
}
