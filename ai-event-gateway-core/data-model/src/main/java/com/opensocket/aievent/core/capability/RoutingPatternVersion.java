package com.opensocket.aievent.core.capability;
import java.time.OffsetDateTime;import java.util.Map;
/** Immutable routing-pattern lifecycle snapshot. */
public record RoutingPatternVersion(String tenantId,String patternId,int version,Map<String,Object> snapshot,String changeReason,String actorRef,OffsetDateTime createdAt){}
