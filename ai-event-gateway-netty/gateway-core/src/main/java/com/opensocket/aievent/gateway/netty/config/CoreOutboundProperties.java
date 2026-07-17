package com.opensocket.aievent.gateway.netty.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bounded outbound worker configuration for HTTP calls from the Netty transport gateway to Core.
 *
 * <p>This prevents TCP / WebSocket EventLoop threads from executing or fan-out scheduling unbounded
 * Core HTTP calls. Callers submit work into a bounded queue; dedicated outbound workers perform the
 * blocking HTTP I/O and report completion asynchronously.</p>
 */
@ConfigurationProperties(prefix = "gateway.core-outbound")
public class CoreOutboundProperties {
    private boolean enabled = true;
    private int workerThreads = 4;
    private int queueCapacity = 10000;
    private long shutdownWaitMs = 5000;
    private int logResponseBodyLimit = 500;
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration requestTimeout = Duration.ofSeconds(3);

    public boolean enabled() { return enabled; }
    public boolean isEnabled() { return enabled(); }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int workerThreads() { return workerThreads <= 0 ? 4 : Math.min(workerThreads, 128); }
    public int getWorkerThreads() { return workerThreads(); }
    public void setWorkerThreads(int workerThreads) { this.workerThreads = workerThreads; }

    public int queueCapacity() { return queueCapacity <= 0 ? 10000 : Math.min(queueCapacity, 1_000_000); }
    public int getQueueCapacity() { return queueCapacity(); }
    public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }

    public long shutdownWaitMs() { return shutdownWaitMs < 0 ? 5000 : shutdownWaitMs; }
    public long getShutdownWaitMs() { return shutdownWaitMs(); }
    public void setShutdownWaitMs(long shutdownWaitMs) { this.shutdownWaitMs = shutdownWaitMs; }

    public int logResponseBodyLimit() { return logResponseBodyLimit < 0 ? 0 : Math.min(logResponseBodyLimit, 5000); }
    public int getLogResponseBodyLimit() { return logResponseBodyLimit(); }
    public void setLogResponseBodyLimit(int logResponseBodyLimit) { this.logResponseBodyLimit = logResponseBodyLimit; }

    public Duration connectTimeout() {
        return positive(connectTimeout, Duration.ofSeconds(3));
    }
    public Duration getConnectTimeout() { return connectTimeout(); }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }

    public Duration requestTimeout() {
        return positive(requestTimeout, Duration.ofSeconds(3));
    }
    public Duration getRequestTimeout() { return requestTimeout(); }
    public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }

    private Duration positive(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}
