package com.opensocket.aievent.core.iam.runtime.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Stateless PKCE/nonce derivation plus one-way state evidence. Raw state/verifier/nonce are never persisted. */
public final class FederationStateCodec {
    private final byte[] key; private final SecureRandom random=new SecureRandom();
    public FederationStateCodec(byte[] key){this.key=key.clone();}
    public String newState(){byte[] b=new byte[32];random.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    public String stateHash(String state){return hex(sha256(required(state)));}
    public String nonce(String state){return b64(hmac("nonce:"+required(state)));}
    public String nonceHash(String state){return hex(sha256(nonce(state)));}
    public String codeVerifier(String state){return b64(hmac("pkce:"+required(state)));}
    public String codeChallenge(String state){return b64(sha256(codeVerifier(state)));}
    private byte[] hmac(String value){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));}catch(Exception ex){throw new IllegalStateException("AUTH_FEDERATION_STATE_CRYPTO_FAILED",ex);}}
    private static byte[] sha256(String value){try{return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));}catch(Exception ex){throw new IllegalStateException(ex);}}
    private static String b64(byte[] bytes){return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    private static String hex(byte[] bytes){return java.util.HexFormat.of().formatHex(bytes);}
    private static String required(String value){if(value==null||value.isBlank()||value.length()>2048)throw new IllegalArgumentException("AUTH_FEDERATION_STATE_INVALID");return value;}
}
