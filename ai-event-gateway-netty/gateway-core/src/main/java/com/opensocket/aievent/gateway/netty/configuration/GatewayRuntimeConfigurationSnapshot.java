package com.opensocket.aievent.gateway.netty.configuration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Set;
import java.util.TreeSet;

/** JSON-compatible copy of Core snapshot contract; Netty has no Core reactor dependency. */
public record GatewayRuntimeConfigurationSnapshot(
        String environment,String configSetId,String setKey,String revisionId,long sequenceNo,int schemaVersion,
        OffsetDateTime issuedAt,OffsetDateTime expiresAt,String audience,int authorityContractVersion,String authorityMode,Set<String> requiredKeys,
        String payloadJson,String payloadHash,String signature) {
    public GatewayRuntimeConfigurationSnapshot {authorityContractVersion=authorityContractVersion<=0?1:authorityContractVersion;authorityMode=mode(authorityMode,authorityContractVersion);requiredKeys=keys(requiredKeys);if(authorityContractVersion<=1&&!requiredKeys.isEmpty())throw new IllegalArgumentException("authority contract v1 cannot carry requiredKeys");}
    public GatewayRuntimeConfigurationSnapshot(String environment,String configSetId,String setKey,String revisionId,long sequenceNo,int schemaVersion,OffsetDateTime issuedAt,OffsetDateTime expiresAt,String audience,String payloadJson,String payloadHash,String signature){this(environment,configSetId,setKey,revisionId,sequenceNo,schemaVersion,issuedAt,expiresAt,audience,1,"DUAL_READ",Set.of(),payloadJson,payloadHash,signature);}
    public GatewayRuntimeConfigurationSnapshot(String environment,String configSetId,String setKey,String revisionId,long sequenceNo,int schemaVersion,OffsetDateTime issuedAt,OffsetDateTime expiresAt,String audience,String authorityMode,Set<String> requiredKeys,String payloadJson,String payloadHash,String signature){this(environment,configSetId,setKey,revisionId,sequenceNo,schemaVersion,issuedAt,expiresAt,audience,2,authorityMode,requiredKeys,payloadJson,payloadHash,signature);}
    public boolean runtimeOnly(){return authorityContractVersion>=2&&"RUNTIME_ONLY".equals(authorityMode);}
    public boolean required(String key){return authorityContractVersion>=2&&key!=null&&requiredKeys.contains(key);}
    public String snapshotFingerprint(){return authorityContractVersion<=1?payloadHash:sha256(payloadHash+"\n"+authorityMode+"\n"+String.join(",",requiredKeys));}
    public String signingInput(){if(authorityContractVersion<=1)return String.join("\n",req(environment),req(configSetId),req(setKey),req(revisionId),Long.toString(sequenceNo),Integer.toString(schemaVersion),req(issuedAt==null?null:issuedAt.toString()),req(expiresAt==null?null:expiresAt.toString()),req(audience),req(payloadHash));return String.join("\n",req(environment),req(configSetId),req(setKey),req(revisionId),Long.toString(sequenceNo),Integer.toString(schemaVersion),req(issuedAt==null?null:issuedAt.toString()),req(expiresAt==null?null:expiresAt.toString()),req(audience),Integer.toString(authorityContractVersion),req(authorityMode),String.join(",",requiredKeys),req(payloadHash));}
    private static String mode(String v,int version){if(version<=1)return v==null||v.isBlank()?"DUAL_READ":v.trim().toUpperCase();String m=req(v).trim().toUpperCase();if(!Set.of("STARTUP_ONLY","DUAL_READ","RUNTIME_ONLY").contains(m))throw new IllegalArgumentException("unsupported snapshot authorityMode: "+v);return m;}
    private static Set<String> keys(Set<String> v){if(v==null||v.isEmpty())return Set.of();TreeSet<String>s=new TreeSet<>();for(String x:v)if(x!=null&&!x.isBlank())s.add(x.trim());return java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(s));}
    private static String sha256(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static String req(String v){if(v==null||v.isBlank())throw new IllegalArgumentException("snapshot field is required");return v;}
}
