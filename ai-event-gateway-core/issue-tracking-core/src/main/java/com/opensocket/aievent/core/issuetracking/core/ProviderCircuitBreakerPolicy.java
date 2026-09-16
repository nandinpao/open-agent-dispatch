package com.opensocket.aievent.core.issuetracking.core;
import com.opensocket.aievent.core.issuetracking.recovery.*;
public final class ProviderCircuitBreakerPolicy {
 public ProviderHealthStatus next(ProviderHealthStatus current,int consecutiveFailures,int queueDepth,int maxQueueDepth,boolean rateLimited,boolean success){if(current==ProviderHealthStatus.DISABLED)return current;if(success)return current==ProviderHealthStatus.OPEN_CIRCUIT||current==ProviderHealthStatus.RECOVERING?ProviderHealthStatus.RECOVERING:ProviderHealthStatus.HEALTHY;if(queueDepth>=maxQueueDepth)return ProviderHealthStatus.SATURATED;if(rateLimited)return ProviderHealthStatus.THROTTLED;if(consecutiveFailures>=5)return ProviderHealthStatus.OPEN_CIRCUIT;return current==ProviderHealthStatus.OPEN_CIRCUIT?ProviderHealthStatus.OPEN_CIRCUIT:ProviderHealthStatus.HEALTHY;}
 public boolean acceptsNewWork(ProviderHealthStatus status,int queueDepth,int maxQueueDepth){return status!=ProviderHealthStatus.DISABLED&&status!=ProviderHealthStatus.OPEN_CIRCUIT&&queueDepth<maxQueueDepth;}
}
