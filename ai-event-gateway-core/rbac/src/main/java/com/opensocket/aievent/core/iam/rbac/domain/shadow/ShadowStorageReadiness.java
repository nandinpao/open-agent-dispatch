package com.opensocket.aievent.core.iam.rbac.domain.shadow;
import java.time.Instant;import java.util.Optional;
public record ShadowStorageReadiness(long durableMismatchRows,long sampledMatchRows,long metricRows,long receiptRows,long archivedPartitions,long overdueArchivePurges,long durablePartitions,long sampledPartitions,long deadLetterPartitions,Optional<Instant> lastRetentionCompletedAt){}
