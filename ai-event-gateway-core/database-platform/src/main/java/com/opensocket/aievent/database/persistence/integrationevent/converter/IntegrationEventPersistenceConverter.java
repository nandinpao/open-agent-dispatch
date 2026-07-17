package com.opensocket.aievent.database.persistence.integrationevent.converter;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.opensocket.aievent.database.persistence.spi.DatabasePersistenceConverter;
import com.opensocket.aievent.database.persistence.integrationevent.po.IntegrationEventPo;
import com.opensocket.aievent.core.integration.IntegrationEventRecord;
import com.opensocket.aievent.core.integration.IntegrationEventStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@DatabasePersistenceConverter
@ConditionalOnProperty(prefix="core.integration-events", name="store", havingValue="MYBATIS")
public class IntegrationEventPersistenceConverter {


    public IntegrationEventPo toPo(IntegrationEventRecord e){IntegrationEventPo r=new IntegrationEventPo();r.setIntegrationEventId(e.getIntegrationEventId());r.setEventId(e.getEventId());r.setEventType(e.getEventType());r.setAggregateType(e.getAggregateType());r.setAggregateId(e.getAggregateId());r.setEnvelopeJson(e.getEnvelopeJson());r.setStatus(e.getStatus()==null?null:e.getStatus().name());r.setAttemptCount(e.getAttemptCount());r.setNextAttemptAt(e.getNextAttemptAt());r.setLastError(e.getLastError());r.setClaimedBy(e.getClaimedBy());r.setClaimUntil(e.getClaimUntil());r.setCreatedAt(e.getCreatedAt());r.setDeliveredAt(e.getDeliveredAt());r.setUpdatedAt(e.getUpdatedAt());return r;}

    public IntegrationEventRecord toDomain(IntegrationEventPo r){IntegrationEventRecord e=new IntegrationEventRecord();e.setIntegrationEventId(r.getIntegrationEventId());e.setEventId(r.getEventId());e.setEventType(r.getEventType());e.setAggregateType(r.getAggregateType());e.setAggregateId(r.getAggregateId());e.setEnvelopeJson(r.getEnvelopeJson());e.setStatus(r.getStatus()==null?null:IntegrationEventStatus.valueOf(r.getStatus()));e.setAttemptCount(r.getAttemptCount());e.setNextAttemptAt(r.getNextAttemptAt());e.setLastError(r.getLastError());e.setClaimedBy(r.getClaimedBy());e.setClaimUntil(r.getClaimUntil());e.setCreatedAt(r.getCreatedAt());e.setDeliveredAt(r.getDeliveredAt());e.setUpdatedAt(r.getUpdatedAt());return e;}
}
