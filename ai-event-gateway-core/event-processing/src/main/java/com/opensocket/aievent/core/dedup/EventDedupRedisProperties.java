package com.opensocket.aievent.core.dedup;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "event.dedup.redis")
public class EventDedupRedisProperties {
    private String keyPrefix = "aeg";
    private long lockWaitSeconds = 5;
    private long lockLeaseSeconds = 30;

    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }

    public long getLockWaitSeconds() { return lockWaitSeconds; }
    public void setLockWaitSeconds(long lockWaitSeconds) { this.lockWaitSeconds = lockWaitSeconds; }

    public long getLockLeaseSeconds() { return lockLeaseSeconds; }
    public void setLockLeaseSeconds(long lockLeaseSeconds) { this.lockLeaseSeconds = lockLeaseSeconds; }
}
