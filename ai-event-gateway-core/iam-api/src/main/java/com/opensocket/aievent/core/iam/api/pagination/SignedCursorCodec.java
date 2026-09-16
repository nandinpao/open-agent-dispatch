package com.opensocket.aievent.core.iam.api.pagination;

import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class SignedCursorCodec {
    private final byte[] secret; private final Duration ttl; private final ObjectMapper mapper; private final Clock clock;
    private SignedCursorCodec(byte[] secret,Duration ttl,ObjectMapper mapper,Clock clock){if(secret.length<32)throw new IllegalArgumentException("cursor signing secret must be at least 256 bits");this.secret=secret.clone();this.ttl=ttl;this.mapper=mapper;this.clock=clock;}
    public static SignedCursorCodec fromBase64Secret(String secret,Duration ttl,ObjectMapper mapper,Clock clock){try{return new SignedCursorCodec(Base64.getDecoder().decode(secret),ttl,mapper,clock);}catch(IllegalArgumentException ex){throw new IllegalArgumentException("IAM cursor signing secret must be valid Base64",ex);}}
    public String encode(CursorPayload payload){try{CursorPayload bounded=new CursorPayload(payload.tenantId(),payload.resource(),payload.sortField(),payload.sortDirection(),payload.lastSortValue(),payload.lastUniqueId(),payload.filterHash(),clock.instant().plus(ttl));byte[] json=mapper.writeValueAsBytes(bounded);return b64(json)+"."+b64(sign(json));}catch(Exception ex){throw new IllegalStateException("Unable to encode cursor",ex);}}
    public CursorPayload decode(String token,String expectedTenant,String expectedResource,String expectedFilterHash){try{String[] p=token.split("\\.",2);if(p.length!=2)throw new IllegalArgumentException("invalid cursor");byte[] json=Base64.getUrlDecoder().decode(p[0]);byte[] signature=Base64.getUrlDecoder().decode(p[1]);if(!java.security.MessageDigest.isEqual(signature,sign(json)))throw new IllegalArgumentException("cursor signature is invalid");CursorPayload c=mapper.readValue(json,CursorPayload.class);if(!c.expiresAt().isAfter(clock.instant()))throw new IllegalArgumentException("cursor expired");if(!c.tenantId().equals(expectedTenant)||!c.resource().equals(expectedResource)||!c.filterHash().equals(expectedFilterHash))throw new IllegalArgumentException("cursor context mismatch");return c;}catch(IllegalArgumentException ex){throw ex;}catch(Exception ex){throw new IllegalArgumentException("invalid cursor",ex);}}
    private byte[] sign(byte[] bytes)throws Exception{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));return mac.doFinal(bytes);}
    private String b64(byte[] bytes){return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
}
