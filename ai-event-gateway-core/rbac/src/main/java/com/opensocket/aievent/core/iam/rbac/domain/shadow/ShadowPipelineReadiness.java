package com.opensocket.aievent.core.iam.rbac.domain.shadow;
public record ShadowPipelineReadiness(long pendingCount,long processingCount,long readyCount,long oldestPendingSeconds,long deadLetters24h,long dropped24h,long retries24h,long processed24h,long queueCapacity,boolean enabled){}
