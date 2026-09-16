package com.opensocket.aievent.core.capability;
import java.time.OffsetDateTime;import java.util.Map;
public record AggregationDefinitionVersion(String tenantId,String aggregationId,int version,Map<String,Object> snapshot,String changeReason,String actorRef,OffsetDateTime createdAt){public AggregationDefinitionVersion{snapshot=snapshot==null?Map.of():Map.copyOf(snapshot);}}
