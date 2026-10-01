package com.opensocket.aievent.worker.configuration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Complete environment/audience/expiry/hash/HMAC validation before worker local swap. */
public final class WorkerRuntimeConfigurationSnapshotVerifier {
    private final byte[] key;
    public WorkerRuntimeConfigurationSnapshotVerifier(String key){if(key==null||key.trim().length()<32)throw new IllegalArgumentException("runtime configuration HMAC key must contain at least 32 characters");this.key=key.trim().getBytes(StandardCharsets.UTF_8);}
    public void verify(WorkerRuntimeConfigurationSnapshot s,String expectedEnvironment){
        verifyAuthenticity(s,expectedEnvironment);
        if(WorkerRuntimeConfigurationSnapshotFreshness.state(s,0)!=WorkerRuntimeConfigurationSnapshotState.ACTIVE)throw new IllegalArgumentException("SNAPSHOT_EXPIRED");
    }
    public WorkerRuntimeConfigurationSnapshotState verifyForRecovery(WorkerRuntimeConfigurationSnapshot s,String expectedEnvironment,long maxStaleMs){
        verifyAuthenticity(s,expectedEnvironment);
        WorkerRuntimeConfigurationSnapshotState state=WorkerRuntimeConfigurationSnapshotFreshness.state(s,maxStaleMs);
        if(state==WorkerRuntimeConfigurationSnapshotState.EXPIRED)throw new IllegalArgumentException("SNAPSHOT_LKG_MAX_STALE_EXCEEDED");
        return state;
    }
    private void verifyAuthenticity(WorkerRuntimeConfigurationSnapshot s,String expectedEnvironment){
        if(s==null)throw new IllegalArgumentException("snapshot is required");
        if(!expectedEnvironment.equalsIgnoreCase(s.environment()))throw new IllegalArgumentException("SNAPSHOT_ENVIRONMENT_MISMATCH");
        if(s.expiresAt()==null)throw new IllegalArgumentException("SNAPSHOT_EXPIRY_MISSING");
        if(!"WORKER".equalsIgnoreCase(s.audience())&&!"ALL_NODES".equalsIgnoreCase(s.audience()))throw new IllegalArgumentException("SNAPSHOT_AUDIENCE_DENIED");
        String hash=sha256(s.payloadJson());if(!hash.equalsIgnoreCase(s.payloadHash()))throw new IllegalArgumentException("SNAPSHOT_HASH_MISMATCH");
        byte[] expected=HexFormat.of().parseHex(sign(s.signingInput()));byte[] actual;
        try{actual=HexFormat.of().parseHex(s.signature());}catch(Exception ex){throw new IllegalArgumentException("SNAPSHOT_SIGNATURE_INVALID",ex);}
        if(!MessageDigest.isEqual(expected,actual))throw new IllegalArgumentException("SNAPSHOT_SIGNATURE_INVALID");
        validateAuthorityContract(s);
    }
    private void validateAuthorityContract(WorkerRuntimeConfigurationSnapshot s){
        try{JsonNode root=JsonMapper.builder().build().readTree(s.payloadJson());if(root==null||!root.isObject())throw new IllegalArgumentException("SNAPSHOT_PAYLOAD_NOT_OBJECT");java.util.TreeSet<String> actual=new java.util.TreeSet<>();root.properties().forEach(e->actual.add(e.getKey()));if(!actual.containsAll(s.requiredKeys())){java.util.TreeSet<String> missing=new java.util.TreeSet<>(s.requiredKeys());missing.removeAll(actual);throw new IllegalStateException("CONFIGURATION_INCOMPLETE setKey="+s.setKey()+" missing="+missing);}if(s.runtimeOnly()&&!actual.equals(new java.util.TreeSet<>(s.requiredKeys())))throw new IllegalStateException("RUNTIME_CONFIG_RUNTIME_ONLY_MANIFEST_MISMATCH setKey="+s.setKey());if("STARTUP_ONLY".equals(s.authorityMode())&&!s.requiredKeys().isEmpty())throw new IllegalStateException("RUNTIME_CONFIG_STARTUP_ONLY_REQUIRED_KEYS_NOT_EMPTY setKey="+s.setKey());}catch(RuntimeException ex){throw ex;}catch(Exception ex){throw new IllegalStateException("SNAPSHOT_AUTHORITY_CONTRACT_INVALID",ex);}
    }
    private String sign(String v){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private String sha256(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
