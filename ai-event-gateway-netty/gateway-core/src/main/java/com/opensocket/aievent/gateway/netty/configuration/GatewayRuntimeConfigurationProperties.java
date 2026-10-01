package com.opensocket.aievent.gateway.netty.configuration;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bootstrap-only Gateway reconcile settings. Gateway never receives PostgreSQL credentials. */
@ConfigurationProperties(prefix="gateway.runtime-configuration")
public class GatewayRuntimeConfigurationProperties {
    private boolean enabled;
    private String baseUrl="http://localhost:18080";
    private String authToken="";
    private String authHeaderName="X-Cluster-Token";
    private String hmacKey="";
    private String nodeInstanceId="gateway-local";
    private List<String> configSetIds=new ArrayList<>();
    private long reconcileMs=5000;
    private boolean pubsubEnabled=false;
    private String redisChannel="opendispatch:runtime-config:updates";
    private boolean failClosedOnColdStart=true;
    private boolean lkgEnabled=true;
    private String lkgDirectory="./data/runtime-config/gateway";
    private long maxStaleMs=300000;
    private long coldStartWaitMs=60000;
    private long coldStartRetryMs=1000;

    public boolean enabled(){return enabled;} public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
    public String baseUrl(){String v=blank(baseUrl)?"http://localhost:18080":baseUrl.trim();return v.endsWith("/")?v.substring(0,v.length()-1):v;} public String getBaseUrl(){return baseUrl();} public void setBaseUrl(String v){baseUrl=v;}
    public String authToken(){return blank(authToken)?"":authToken.trim();} public String getAuthToken(){return authToken();} public void setAuthToken(String v){authToken=v;}
    public String authHeaderName(){return blank(authHeaderName)?"X-Cluster-Token":authHeaderName.trim();} public String getAuthHeaderName(){return authHeaderName();} public void setAuthHeaderName(String v){authHeaderName=v;}
    public String hmacKey(){return blank(hmacKey)?"":hmacKey.trim();} public String getHmacKey(){return hmacKey();} public void setHmacKey(String v){hmacKey=v;}
    public String nodeInstanceId(){return blank(nodeInstanceId)?"gateway-local":nodeInstanceId.trim();} public String getNodeInstanceId(){return nodeInstanceId();} public void setNodeInstanceId(String v){nodeInstanceId=v;}
    public List<String> configSetIds(){return configSetIds==null?List.of():configSetIds.stream().filter(v->v!=null&&!v.isBlank()).map(String::trim).distinct().toList();} public List<String> getConfigSetIds(){return configSetIds();} public void setConfigSetIds(List<String> v){configSetIds=v==null?new ArrayList<>():new ArrayList<>(v);}
    public long reconcileMs(){return Math.max(1000,reconcileMs);} public long getReconcileMs(){return reconcileMs;} public void setReconcileMs(long v){reconcileMs=v;}
    public boolean pubsubEnabled(){return pubsubEnabled;} public boolean isPubsubEnabled(){return pubsubEnabled;} public void setPubsubEnabled(boolean v){pubsubEnabled=v;}
    public String redisChannel(){return blank(redisChannel)?"opendispatch:runtime-config:updates":redisChannel.trim();} public String getRedisChannel(){return redisChannel();} public void setRedisChannel(String v){redisChannel=v;}
    public boolean failClosedOnColdStart(){return failClosedOnColdStart;} public boolean isFailClosedOnColdStart(){return failClosedOnColdStart;} public void setFailClosedOnColdStart(boolean v){failClosedOnColdStart=v;}
    public boolean lkgEnabled(){return lkgEnabled;} public boolean isLkgEnabled(){return lkgEnabled;} public void setLkgEnabled(boolean v){lkgEnabled=v;}
    public String lkgDirectory(){return blank(lkgDirectory)?"./data/runtime-config/gateway":lkgDirectory.trim();} public String getLkgDirectory(){return lkgDirectory();} public void setLkgDirectory(String v){lkgDirectory=v;}
    public long maxStaleMs(){return Math.max(0,maxStaleMs);} public long getMaxStaleMs(){return maxStaleMs;} public void setMaxStaleMs(long v){maxStaleMs=v;}
    public long coldStartWaitMs(){return Math.max(0,coldStartWaitMs);} public long getColdStartWaitMs(){return coldStartWaitMs;} public void setColdStartWaitMs(long v){coldStartWaitMs=v;}
    public long coldStartRetryMs(){return Math.max(100,coldStartRetryMs);} public long getColdStartRetryMs(){return coldStartRetryMs;} public void setColdStartRetryMs(long v){coldStartRetryMs=v;}
    public void requireSecureKey(){if(hmacKey().length()<32) throw new IllegalStateException("OPENDISPATCH_CONFIG_SNAPSHOT_HMAC_KEY must contain at least 32 characters when Gateway runtime configuration reconcile is enabled");}
    private static boolean blank(String v){return v==null||v.isBlank();}
}
