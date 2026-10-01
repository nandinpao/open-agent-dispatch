package com.opensocket.aievent.core.configuration.distribution;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bootstrap-only V40-4 distribution settings. These are not runtime-tunable keys. */
@ConfigurationProperties(prefix = "opendispatch.runtime-configuration.distribution")
public class RuntimeConfigurationDistributionProperties {
    private boolean enabled;
    private String hmacKey = "";
    private String redisKeyPrefix = "opendispatch:runtime-config";
    private String redisChannel = "opendispatch:runtime-config:updates";
    private String workerId = "core-config-distributor";
    private String coreNodeId = "core";
    private String coreNodeInstanceId = "core-local";
    private int batchSize = 50;
    private long leaseSeconds = 30;
    private long retrySeconds = 10;
    private long snapshotTtlSeconds = 300;
    private long nodeStaleSeconds = 90;
    private int authorityContractVersion = 2;
    private List<String> requiredTargets = new ArrayList<>();

    public boolean enabled(){return enabled;} public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
    public String hmacKey(){return hmacKey==null?"":hmacKey.trim();} public String getHmacKey(){return hmacKey();} public void setHmacKey(String v){hmacKey=v;}
    public String redisKeyPrefix(){return blank(redisKeyPrefix)?"opendispatch:runtime-config":redisKeyPrefix.trim();} public String getRedisKeyPrefix(){return redisKeyPrefix();} public void setRedisKeyPrefix(String v){redisKeyPrefix=v;}
    public String redisChannel(){return blank(redisChannel)?"opendispatch:runtime-config:updates":redisChannel.trim();} public String getRedisChannel(){return redisChannel();} public void setRedisChannel(String v){redisChannel=v;}
    public String workerId(){return blank(workerId)?"core-config-distributor":workerId.trim();} public String getWorkerId(){return workerId();} public void setWorkerId(String v){workerId=v;}
    public String coreNodeId(){return blank(coreNodeId)?"core":coreNodeId.trim();} public String getCoreNodeId(){return coreNodeId();} public void setCoreNodeId(String v){coreNodeId=v;}
    public String coreNodeInstanceId(){return blank(coreNodeInstanceId)?coreNodeId():coreNodeInstanceId.trim();} public String getCoreNodeInstanceId(){return coreNodeInstanceId();} public void setCoreNodeInstanceId(String v){coreNodeInstanceId=v;}
    public int batchSize(){return Math.max(1,Math.min(batchSize,500));} public int getBatchSize(){return batchSize();} public void setBatchSize(int v){batchSize=v;}
    public Duration lease(){return Duration.ofSeconds(Math.max(5,leaseSeconds));} public long getLeaseSeconds(){return leaseSeconds;} public void setLeaseSeconds(long v){leaseSeconds=v;}
    public Duration retry(){return Duration.ofSeconds(Math.max(1,retrySeconds));} public long getRetrySeconds(){return retrySeconds;} public void setRetrySeconds(long v){retrySeconds=v;}
    public Duration snapshotTtl(){return Duration.ofSeconds(Math.max(30,snapshotTtlSeconds));} public long getSnapshotTtlSeconds(){return snapshotTtlSeconds;} public void setSnapshotTtlSeconds(long v){snapshotTtlSeconds=v;}
    public Duration nodeStaleAfter(){return Duration.ofSeconds(Math.max(10,nodeStaleSeconds));} public long getNodeStaleSeconds(){return nodeStaleSeconds;} public void setNodeStaleSeconds(long v){nodeStaleSeconds=v;}
    public int authorityContractVersion(){if(authorityContractVersion<1||authorityContractVersion>2)throw new IllegalStateException("runtime configuration authority contract version must be 1 or 2");return authorityContractVersion;} public int getAuthorityContractVersion(){return authorityContractVersion();} public void setAuthorityContractVersion(int v){authorityContractVersion=v;}
    public List<String> getRequiredTargets(){return requiredTargets==null?List.of():List.copyOf(requiredTargets);}
    public void setRequiredTargets(List<String> values){requiredTargets=values==null?new ArrayList<>():new ArrayList<>(values);}
    public List<RequiredNodeTarget> requiredTargets(){
        if(requiredTargets==null||requiredTargets.isEmpty()) return List.of();
        java.util.LinkedHashMap<String,RequiredNodeTarget> parsed=new java.util.LinkedHashMap<>();
        for(String raw:requiredTargets){
            if(raw==null||raw.isBlank()) continue;
            String[] parts=raw.trim().split("\\|",-1);
            if(parts.length!=3) throw new IllegalStateException("OPENDISPATCH_RUNTIME_CONFIG_REQUIRED_TARGETS entries must be nodeId|nodeRole|nodeInstanceId: "+raw);
            String nodeId=parts[0].trim(); String role=parts[1].trim().toUpperCase(Locale.ROOT); String instance=parts[2].trim();
            if(nodeId.isBlank()||instance.isBlank()||!(role.equals("CORE")||role.equals("GATEWAY")||role.equals("WORKER")))
                throw new IllegalStateException("Invalid runtime configuration required target: "+raw);
            parsed.put(nodeId,new RequiredNodeTarget(nodeId,role,instance));
        }
        return List.copyOf(parsed.values());
    }
    public void requireSecureKey(){if(hmacKey().length()<32) throw new IllegalStateException("OPENDISPATCH_CONFIG_SNAPSHOT_HMAC_KEY must contain at least 32 characters when runtime configuration distribution is enabled");}
    public record RequiredNodeTarget(String nodeId,String nodeRole,String nodeInstanceId) {}
    private static boolean blank(String v){return v==null||v.isBlank();}
}
