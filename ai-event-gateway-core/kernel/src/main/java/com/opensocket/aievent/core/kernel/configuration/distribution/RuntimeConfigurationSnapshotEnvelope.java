package com.opensocket.aievent.core.kernel.configuration.distribution;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Set;
import java.util.TreeSet;

/** Authenticated runtime configuration snapshot envelope. */
public record RuntimeConfigurationSnapshotEnvelope(
        String environment,String configSetId,String setKey,String revisionId,long sequenceNo,int schemaVersion,
        OffsetDateTime issuedAt,OffsetDateTime expiresAt,String audience,int authorityContractVersion,String authorityMode,Set<String> requiredKeys,
        String payloadJson,String payloadHash,String signature) {
    public RuntimeConfigurationSnapshotEnvelope {
        authorityContractVersion=authorityContractVersion<=0?1:authorityContractVersion;
        authorityMode=normalizeAuthorityMode(authorityMode,authorityContractVersion);
        requiredKeys=immutableKeys(requiredKeys);
        if(authorityContractVersion<=1&&!requiredKeys.isEmpty())throw new IllegalArgumentException("authority contract v1 cannot carry requiredKeys");
    }
    /** Historical V40/V41 constructor: signed contract v1, payload fingerprint only. */
    public RuntimeConfigurationSnapshotEnvelope(String environment,String configSetId,String setKey,String revisionId,long sequenceNo,int schemaVersion,
            OffsetDateTime issuedAt,OffsetDateTime expiresAt,String audience,String payloadJson,String payloadHash,String signature){
        this(environment,configSetId,setKey,revisionId,sequenceNo,schemaVersion,issuedAt,expiresAt,audience,1,"DUAL_READ",Set.of(),payloadJson,payloadHash,signature);
    }
    /** X3 convenience constructor for authority contract v2. */
    public RuntimeConfigurationSnapshotEnvelope(String environment,String configSetId,String setKey,String revisionId,long sequenceNo,int schemaVersion,
            OffsetDateTime issuedAt,OffsetDateTime expiresAt,String audience,String authorityMode,Set<String> requiredKeys,String payloadJson,String payloadHash,String signature){
        this(environment,configSetId,setKey,revisionId,sequenceNo,schemaVersion,issuedAt,expiresAt,audience,2,authorityMode,requiredKeys,payloadJson,payloadHash,signature);
    }
    public boolean runtimeOnly(){return authorityContractVersion>=2&&"RUNTIME_ONLY".equals(authorityMode);}
    public boolean required(String key){return authorityContractVersion>=2&&key!=null&&requiredKeys.contains(key);}
    public String snapshotFingerprint(){return authorityContractVersion<=1?payloadHash:sha256(payloadHash+"\n"+authorityMode+"\n"+String.join(",",requiredKeys));}
    public String signingInput(){
        if(authorityContractVersion<=1)return String.join("\n",req(environment),req(configSetId),req(setKey),req(revisionId),Long.toString(sequenceNo),Integer.toString(schemaVersion),req(issuedAt==null?null:issuedAt.toString()),req(expiresAt==null?null:expiresAt.toString()),req(audience),req(payloadHash));
        return String.join("\n",req(environment),req(configSetId),req(setKey),req(revisionId),Long.toString(sequenceNo),Integer.toString(schemaVersion),req(issuedAt==null?null:issuedAt.toString()),req(expiresAt==null?null:expiresAt.toString()),req(audience),Integer.toString(authorityContractVersion),req(authorityMode),String.join(",",requiredKeys),req(payloadHash));
    }
    private static String normalizeAuthorityMode(String value,int version){if(version<=1)return value==null||value.isBlank()?"DUAL_READ":value.trim().toUpperCase();String mode=req(value).trim().toUpperCase();if(!Set.of("STARTUP_ONLY","DUAL_READ","RUNTIME_ONLY").contains(mode))throw new IllegalArgumentException("Unsupported runtime configuration authorityMode: "+value);return mode;}
    private static Set<String> immutableKeys(Set<String> values){if(values==null||values.isEmpty())return Set.of();TreeSet<String> sorted=new TreeSet<>();for(String value:values)if(value!=null&&!value.isBlank())sorted.add(value.trim());return java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(sorted));}
    private static String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static String req(String value){if(value==null||value.isBlank())throw new IllegalArgumentException("Snapshot envelope field is required");return value;}
}
