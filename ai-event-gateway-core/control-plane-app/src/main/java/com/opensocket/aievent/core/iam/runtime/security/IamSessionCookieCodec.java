package com.opensocket.aievent.core.iam.runtime.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HMAC protected browser-session locator. Tenant is a locator hint, never authority. */
public final class IamSessionCookieCodec {
    private final byte[] key; private final Clock clock;
    public IamSessionCookieCodec(byte[] key,Clock clock){this.key=key.clone();this.clock=clock;}
    public String encode(String tenantId,String sessionId,Instant expiresAt){
        String scope=tenantId==null||tenantId.isBlank()?"INSTANCE":"TENANT";
        String payload=scope+"\u001f"+(tenantId==null?"":tenantId.trim())+"\u001f"+required(sessionId)+"\u001f"+expiresAt.getEpochSecond();
        String encoded=Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(sign(encoded));
    }
    public Locator decode(String value){
        try{
            if(value==null||value.length()>2048)throw new IllegalArgumentException("AUTH_SESSION_COOKIE_INVALID");
            String[] parts=value.split("\\.",-1);if(parts.length!=2)throw new IllegalArgumentException("AUTH_SESSION_COOKIE_INVALID");
            byte[] actual=Base64.getUrlDecoder().decode(parts[1]);if(!java.security.MessageDigest.isEqual(actual,sign(parts[0])))throw new IllegalArgumentException("AUTH_SESSION_COOKIE_INVALID");
            String[] data=new String(Base64.getUrlDecoder().decode(parts[0]),StandardCharsets.UTF_8).split("\u001f",-1);
            if(data.length!=4)throw new IllegalArgumentException("AUTH_SESSION_COOKIE_INVALID");
            Locator locator=new Locator(data[0],data[1],required(data[2]),Instant.ofEpochSecond(Long.parseLong(data[3])));
            if(!clock.instant().isBefore(locator.expiresAt()))throw new IllegalArgumentException("AUTH_SESSION_EXPIRED");
            if("INSTANCE".equals(locator.scope())&&!locator.tenantId().isBlank())throw new IllegalArgumentException("AUTH_SESSION_COOKIE_INVALID");
            if("TENANT".equals(locator.scope())&&locator.tenantId().isBlank())throw new IllegalArgumentException("AUTH_SESSION_COOKIE_INVALID");
            return locator;
        }catch(IllegalArgumentException ex){throw ex;}catch(Exception ex){throw new IllegalArgumentException("AUTH_SESSION_COOKIE_INVALID",ex);}
    }
    private byte[] sign(String encoded){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return mac.doFinal(encoded.getBytes(StandardCharsets.US_ASCII));}catch(Exception ex){throw new IllegalStateException("Unable to sign IAM session cookie",ex);}}
    private static String required(String value){if(value==null||value.isBlank())throw new IllegalArgumentException("AUTH_SESSION_COOKIE_INVALID");return value.trim();}
    public record Locator(String scope,String tenantId,String sessionId,Instant expiresAt){}
}
