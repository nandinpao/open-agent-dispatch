package com.opensocket.aievent.worker.configuration;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bootstrap-only worker configuration distribution settings. No PostgreSQL credentials are permitted here. */
@ConfigurationProperties(prefix="adapter-worker.runtime-configuration")
public class WorkerRuntimeConfigurationProperties {
    private boolean enabled;
    private String hmacKey="";
    private String nodeInstanceId="worker-local";
    private List<String> configSetIds=new ArrayList<>();
    private long reconcileMs=5000;
    private boolean pubsubEnabled=false;
    private String redisChannel="opendispatch:runtime-config:updates";
    private boolean failClosedOnColdStart=true;
    private boolean lkgEnabled=true;
    private String lkgDirectory="./data/runtime-config/worker";
    private long maxStaleMs=300000;

    public boolean isEnabled(){return enabled;} public boolean enabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
    public String hmacKey(){return blank(hmacKey)?"":hmacKey.trim();} public String getHmacKey(){return hmacKey();} public void setHmacKey(String v){hmacKey=v;}
    public String nodeInstanceId(){return blank(nodeInstanceId)?"worker-local":nodeInstanceId.trim();} public String getNodeInstanceId(){return nodeInstanceId();} public void setNodeInstanceId(String v){nodeInstanceId=v;}
    public List<String> configSetIds(){return configSetIds==null?List.of():configSetIds.stream().filter(v->v!=null&&!v.isBlank()).map(String::trim).distinct().toList();} public List<String> getConfigSetIds(){return configSetIds();} public void setConfigSetIds(List<String> v){configSetIds=v==null?new ArrayList<>():new ArrayList<>(v);}
    public long reconcileMs(){return Math.max(1000,reconcileMs);} public long getReconcileMs(){return reconcileMs;} public void setReconcileMs(long v){reconcileMs=v;}
    public boolean isPubsubEnabled(){return pubsubEnabled;} public boolean pubsubEnabled(){return pubsubEnabled;} public void setPubsubEnabled(boolean v){pubsubEnabled=v;}
    public String redisChannel(){return blank(redisChannel)?"opendispatch:runtime-config:updates":redisChannel.trim();} public String getRedisChannel(){return redisChannel();} public void setRedisChannel(String v){redisChannel=v;}
    public boolean failClosedOnColdStart(){return failClosedOnColdStart;} public boolean isFailClosedOnColdStart(){return failClosedOnColdStart;} public void setFailClosedOnColdStart(boolean v){failClosedOnColdStart=v;}
    public boolean lkgEnabled(){return lkgEnabled;} public boolean isLkgEnabled(){return lkgEnabled;} public void setLkgEnabled(boolean v){lkgEnabled=v;}
    public String lkgDirectory(){return blank(lkgDirectory)?"./data/runtime-config/worker":lkgDirectory.trim();} public String getLkgDirectory(){return lkgDirectory();} public void setLkgDirectory(String v){lkgDirectory=v;}
    public long maxStaleMs(){return Math.max(0,maxStaleMs);} public long getMaxStaleMs(){return maxStaleMs;} public void setMaxStaleMs(long v){maxStaleMs=v;}
    public void requireSecureKey(){if(hmacKey().length()<32)throw new IllegalStateException("OPENDISPATCH_CONFIG_SNAPSHOT_HMAC_KEY must contain at least 32 characters when Worker runtime configuration is enabled");}
    private static boolean blank(String v){return v==null||v.isBlank();}
}
