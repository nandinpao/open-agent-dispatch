package com.opensocket.aievent.core.capability;
import java.time.OffsetDateTime;import java.util.List;
/** Phase 9 semantic aggregation contract. It names WHAT synthesis capability is required, never an Agent/Provider/transport. */
public record AggregationDefinition(String tenantId,String aggregationId,String displayName,String aggregationCapabilityCode,String operation,List<String> requiredInputCapabilities,Double minInputConfidence,boolean allowPartialEvidence,boolean requireHumanReviewOnPartial,String status,int version,OffsetDateTime createdAt,OffsetDateTime updatedAt){public AggregationDefinition{requiredInputCapabilities=requiredInputCapabilities==null?List.of():List.copyOf(requiredInputCapabilities);}}
