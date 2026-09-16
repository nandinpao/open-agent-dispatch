package com.opensocket.aievent.database.persistence.issuesync.po;
import lombok.Getter; import lombok.Setter; import lombok.NoArgsConstructor; import java.time.OffsetDateTime;
@Getter @Setter @NoArgsConstructor
public class IntegrationCircuitBreakerPo {
 private String tenantId;
 private String connectionId;
 private String projectMappingId;
 private String state;
 private int consecutiveFailures;
 private int failureThreshold;
 private OffsetDateTime openedAt;
 private OffsetDateTime nextProbeAt;
 private OffsetDateTime lastSuccessAt;
 private OffsetDateTime lastFailureAt;
 private String lastErrorCode;
 private OffsetDateTime updatedAt;
}
