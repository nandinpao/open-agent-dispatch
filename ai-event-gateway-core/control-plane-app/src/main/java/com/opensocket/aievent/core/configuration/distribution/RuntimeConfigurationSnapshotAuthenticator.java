package com.opensocket.aievent.core.configuration.distribution;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationSnapshotEnvelope;

/** HMAC-SHA256 authenticated snapshot signer/verifier. The key is bootstrap secret material, never snapshot payload. */
@Component
@ConditionalOnProperty(prefix="opendispatch.runtime-configuration.distribution",name="enabled",havingValue="true")
public class RuntimeConfigurationSnapshotAuthenticator {
    private final byte[] key;

    public RuntimeConfigurationSnapshotAuthenticator(RuntimeConfigurationDistributionProperties properties) {
        properties.requireSecureKey();
        this.key=properties.hmacKey().getBytes(StandardCharsets.UTF_8);
    }

    public String sha256(String value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }

    public String sign(String signingInput) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key,"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        } catch(Exception e) { throw new IllegalStateException("Unable to sign runtime configuration snapshot",e); }
    }

    public boolean verify(RuntimeConfigurationSnapshotEnvelope envelope) {
        return verifyFor(envelope,null,Set.of());
    }

    /** Complete envelope validation before any local atomic swap. */
    public boolean verifyFor(RuntimeConfigurationSnapshotEnvelope envelope,String expectedEnvironment,Set<String> allowedAudiences) {
        if(envelope==null||envelope.signature()==null) return false;
        if(expectedEnvironment!=null&&!expectedEnvironment.equalsIgnoreCase(envelope.environment())) return false;
        if(envelope.expiresAt()==null||!envelope.expiresAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC))) return false;
        if(allowedAudiences!=null&&!allowedAudiences.isEmpty()&&allowedAudiences.stream().noneMatch(a->a.equalsIgnoreCase(envelope.audience()))) return false;
        if(!sha256(envelope.payloadJson()).equalsIgnoreCase(envelope.payloadHash())) return false;
        byte[] expected=HexFormat.of().parseHex(sign(envelope.signingInput()));
        byte[] actual;
        try{actual=HexFormat.of().parseHex(envelope.signature());}catch(IllegalArgumentException ex){return false;}
        return MessageDigest.isEqual(expected,actual);
    }
}
