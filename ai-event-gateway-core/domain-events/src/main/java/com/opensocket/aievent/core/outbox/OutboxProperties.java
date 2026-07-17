package com.opensocket.aievent.core.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="core.outbox")
public class OutboxProperties {
    private String store="MEMORY"; private boolean dispatcherEnabled=true; private String workerId="core-outbox"; private Duration claimLease=Duration.ofSeconds(30); private long scanIntervalMs=1000; private int batchSize=100; private int maxAttempts=8; private Duration initialBackoff=Duration.ofSeconds(2); private Duration maxBackoff=Duration.ofMinutes(5);
    public String getStore(){return store;}
    public void setStore(String v){store=v==null?"MEMORY":v;}
    public String getWorkerId(){return workerId;}
    public void setWorkerId(String v){workerId=v==null||v.isBlank()?"core-outbox":v;}
    public Duration getClaimLease(){return claimLease;}
    public void setClaimLease(Duration v){claimLease=v==null?Duration.ofSeconds(30):v;}
    public boolean isDispatcherEnabled(){return dispatcherEnabled;}
    public void setDispatcherEnabled(boolean v){dispatcherEnabled=v;}
    public long getScanIntervalMs(){return scanIntervalMs;}
    public void setScanIntervalMs(long v){scanIntervalMs=Math.max(250,v);}
    public int getBatchSize(){return batchSize;}
    public void setBatchSize(int v){batchSize=Math.max(1,Math.min(v,1000));}
    public int getMaxAttempts(){return maxAttempts;}
    public void setMaxAttempts(int v){maxAttempts=Math.max(1,v);}
    public Duration getInitialBackoff(){return initialBackoff;}
    public void setInitialBackoff(Duration v){initialBackoff=v==null?Duration.ofSeconds(2):v;}
    public Duration getMaxBackoff(){return maxBackoff;}
    public void setMaxBackoff(Duration v){maxBackoff=v==null?Duration.ofMinutes(5):v;}
}
