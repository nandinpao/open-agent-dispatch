package com.opensocket.aievent.core.runtime;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;

/** V41-C3R2M typed runtime view for Core-to-Gateway disconnect enforcement routing/tuning. */
@Component
public final class RuntimeDisconnectRuntimeConfigurationView {
    public static final String SET_KEY=RuntimeConfigurationSetKeys.CORE_SYSTEM;
    public static final String ENABLED="core.runtime-enforcement.disconnect.enabled";
    public static final String DEFAULT_GATEWAY_BASE_URL="core.runtime-enforcement.disconnect.default-gateway-base-url";
    public static final String GATEWAY_NODE_001="core.runtime-enforcement.disconnect.gateway-base-urls.gateway-node-001";
    public static final String GATEWAY_NODE_002="core.runtime-enforcement.disconnect.gateway-base-urls.gateway-node-002";
    public static final String GATEWAY_NODE_003="core.runtime-enforcement.disconnect.gateway-base-urls.gateway-node-003";
    public static final String GATEWAY_REGISTRY_ENABLED="core.runtime-enforcement.disconnect.gateway-registry-enabled";
    public static final String GATEWAY_REGISTRY_URL="core.runtime-enforcement.disconnect.gateway-registry-url";
    public static final String AUTHORIZATION_SCHEME="core.runtime-enforcement.disconnect.authorization-scheme";
    public static final String REQUEST_TIMEOUT="core.runtime-enforcement.disconnect.request-timeout";
    public static final Set<String> ALL=Set.of(ENABLED,DEFAULT_GATEWAY_BASE_URL,GATEWAY_NODE_001,GATEWAY_NODE_002,GATEWAY_NODE_003,GATEWAY_REGISTRY_ENABLED,GATEWAY_REGISTRY_URL,AUTHORIZATION_SCHEME,REQUEST_TIMEOUT);

    private final RuntimeConfigurationSnapshotValues values; private final RuntimeConfigurationAuthorityRegistry authority; private final RuntimeDisconnectProperties startup;
    @Autowired public RuntimeDisconnectRuntimeConfigurationView(RuntimeConfigurationSnapshotValues values,RuntimeConfigurationAuthorityRegistry authority,RuntimeDisconnectProperties startup){this.values=values;this.authority=authority;this.startup=startup;}
    public RuntimeDisconnectRuntimeConfigurationView(RuntimeDisconnectProperties startup){this(null,null,startup==null?new RuntimeDisconnectProperties():startup);}

    public boolean enabled(){return booleanValue(ENABLED,startup.isEnabled());}
    public Duration requestTimeout(){Duration v=durationValue(REQUEST_TIMEOUT,startup.getRequestTimeout());if(v==null||v.compareTo(Duration.ofMillis(100))<0||v.compareTo(Duration.ofSeconds(30))>0)throw invalid(REQUEST_TIMEOUT);return v;}
    public boolean gatewayRegistryEnabled(){return booleanValue(GATEWAY_REGISTRY_ENABLED,startup.isGatewayRegistryEnabled());}
    public String gatewayRegistryUrl(){String v=textValue(GATEWAY_REGISTRY_URL,startup.getGatewayRegistryUrl());v=v==null?"":trimSlash(v);if(v.isBlank()){if(gatewayRegistryEnabled())throw invalid(GATEWAY_REGISTRY_URL);return "";}validateHttpUri(GATEWAY_REGISTRY_URL,v);return v;}
    public String authorizationScheme(){String v=textValue(AUTHORIZATION_SCHEME,startup.getAuthorizationScheme());v=v==null?"":v.trim();if(!v.matches("[A-Za-z][A-Za-z0-9._-]{0,31}"))throw invalid(AUTHORIZATION_SCHEME);return v;}
    public String defaultGatewayBaseUrl(){String v=textValue(DEFAULT_GATEWAY_BASE_URL,startup.getDefaultGatewayBaseUrl());v=trimSlash(v);validateHttpUri(DEFAULT_GATEWAY_BASE_URL,v);return v;}
    public Map<String,String> gatewayBaseUrls(){
        LinkedHashMap<String,String> out=new LinkedHashMap<>(startup.getGatewayBaseUrls());
        out.put("gateway-node-001",gatewayBaseUrl(GATEWAY_NODE_001,"gateway-node-001"));
        out.put("gateway-node-002",gatewayBaseUrl(GATEWAY_NODE_002,"gateway-node-002"));
        out.put("gateway-node-003",gatewayBaseUrl(GATEWAY_NODE_003,"gateway-node-003"));
        out.entrySet().removeIf(e->e.getValue()==null||e.getValue().isBlank()); return Map.copyOf(out);
    }
    public List<String> candidateBaseUrlsFor(String gatewayNodeId){List<String> out=new ArrayList<>();Map<String,String> map=gatewayBaseUrls();if(gatewayNodeId!=null&&!gatewayNodeId.isBlank())add(out,map.get(gatewayNodeId));add(out,defaultGatewayBaseUrl());for(String v:map.values())add(out,v);return List.copyOf(out);}
    /** Secret/header authority intentionally remains startup-bound. */
    public String adminToken(){return startup.getAdminToken();} public String adminTokenHeader(){return startup.getAdminTokenHeader();}

    private String gatewayBaseUrl(String key,String node){String fallback=startup.getGatewayBaseUrls().getOrDefault(node,"");String v=textValue(key,fallback);v=trimSlash(v);if(!v.isBlank())validateHttpUri(key,v);return v;}
    private void validateHttpUri(String key,String value){try{URI u=URI.create(value);String s=u.getScheme();if(s==null||(!s.equalsIgnoreCase("http")&&!s.equalsIgnoreCase("https"))||u.getHost()==null)throw invalid(key);}catch(RuntimeException ex){if(ex instanceof IllegalStateException i)throw i;throw invalid(key);}}
    private String trimSlash(String v){if(v==null)return "";String r=v.trim();while(r.endsWith("/"))r=r.substring(0,r.length()-1);return r;}
    private void add(List<String> out,String v){String n=trimSlash(v);if(!n.isBlank()&&!out.contains(n))out.add(n);}
    private boolean required(String key){return authority!=null&&authority.isRuntimeAuthoritative(key);}private void require(String key){if(values==null||!values.hasSnapshot(SET_KEY))throw incomplete(key);values.requireKeys(SET_KEY,ALL);}private boolean booleanValue(String key,boolean fb){if(values==null)return fb;if(required(key)){require(key);return values.booleanValue(SET_KEY,key).orElseThrow(()->incomplete(key));}return values.booleanValue(SET_KEY,key).orElse(fb);}private String textValue(String key,String fb){if(values==null)return fb;if(required(key)){require(key);return values.textValue(SET_KEY,key).orElseThrow(()->incomplete(key));}return values.textValue(SET_KEY,key).orElse(fb);}private Duration durationValue(String key,Duration fb){if(values==null)return fb;if(required(key)){require(key);return values.durationValue(SET_KEY,key).orElseThrow(()->incomplete(key));}return values.durationValue(SET_KEY,key).orElse(fb);}private static IllegalStateException invalid(String k){return new IllegalStateException("RUNTIME_CONFIG_VALIDATION_FAILED key="+k);}private static IllegalStateException incomplete(String k){return new IllegalStateException("CONFIGURATION_INCOMPLETE setKey="+SET_KEY+" key="+k);}
}
