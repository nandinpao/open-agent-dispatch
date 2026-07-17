package com.opensocket.aievent.core.integration;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="core.integration-events")
public class IntegrationEventProperties {
    private boolean projectionEnabled=false;
    private boolean deliveryEnabled=false;
    private String store="MEMORY";
    private String source="ai-event-gateway-core";
    private String workerId="core-integration-events";
    private Duration claimLease=Duration.ofSeconds(30);
    private long scanIntervalMs=1000;
    private int batchSize=100;
    private int maxAttempts=8;
    private Duration initialBackoff=Duration.ofSeconds(2);
    private Duration maxBackoff=Duration.ofMinutes(5);
    private String sink="NONE";
    private String endpointUrl="";
    private String tokenHeader="X-Internal-Token";
    private String token="";
    private Duration requestTimeout=Duration.ofSeconds(10);
    private Set<String> exportedEventTypes=new LinkedHashSet<>(Set.of("incident.escalated.v1","task.terminal.v1","adapter-action.requested.v1","dispatch.dead-lettered.v1"));
    public boolean isProjectionEnabled(){return projectionEnabled;}
    public void setProjectionEnabled(boolean v){projectionEnabled=v;}
    public boolean isDeliveryEnabled(){return deliveryEnabled;}
    public void setDeliveryEnabled(boolean v){deliveryEnabled=v;}
    public String getStore(){return store;}
    public void setStore(String v){store=v==null?"MEMORY":v;}
    public String getSource(){return source;}
    public void setSource(String v){source=v==null||v.isBlank()?"ai-event-gateway-core":v;}
    public String getWorkerId(){return workerId;}
    public void setWorkerId(String v){workerId=v==null||v.isBlank()?"core-integration-events":v;}
    public Duration getClaimLease(){return claimLease;}
    public void setClaimLease(Duration v){claimLease=v==null?Duration.ofSeconds(30):v;}
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
    public String getSink(){return sink;}
    public void setSink(String v){sink=v==null?"NONE":v;}
    public String getEndpointUrl(){return endpointUrl;}
    public void setEndpointUrl(String v){endpointUrl=v==null?"":v;}
    public String getTokenHeader(){return tokenHeader;}
    public void setTokenHeader(String v){tokenHeader=v==null||v.isBlank()?"X-Internal-Token":v;}
    public String getToken(){return token;}
    public void setToken(String v){token=v==null?"":v;}
    public Duration getRequestTimeout(){return requestTimeout;}
    public void setRequestTimeout(Duration v){requestTimeout=v==null?Duration.ofSeconds(10):v;}
    public Set<String> getExportedEventTypes(){return exportedEventTypes;}
    public void setExportedEventTypes(Set<String> v){exportedEventTypes=v==null?new LinkedHashSet<>():new LinkedHashSet<>(v);}
}
