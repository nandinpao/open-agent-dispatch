package com.opensocket.aievent.core.iam.persistence.crypto;

import com.opensocket.aievent.core.iam.authentication.application.port.out.TotpVerificationPort;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** RFC 6238 TOTP using Base32 secrets and a configurable +/- one-step window. */
public final class Rfc6238TotpVerificationAdapter implements TotpVerificationPort {
    private static final char[] BASE32="ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private final SecureRandom random=new SecureRandom(); private final int window;
    public Rfc6238TotpVerificationAdapter(int window){if(window<0||window>2)throw new IllegalArgumentException("TOTP window must be 0..2");this.window=window;}
    @Override public String generateSecret(){byte[] b=new byte[20];random.nextBytes(b);return encodeBase32(b);}
    @Override public boolean verify(String secret,String code,Instant at,int digits,int periodSeconds,String algorithm){if(code==null||!code.matches("\\d{"+digits+"}"))return false;long counter=at.getEpochSecond()/periodSeconds;for(int i=-window;i<=window;i++){String expected=generate(secret,counter+i,digits,algorithm);if(MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII),code.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))return true;}return false;}
    private String generate(String secret,long counter,int digits,String algorithm){try{Mac mac=Mac.getInstance(algorithm);mac.init(new SecretKeySpec(decodeBase32(secret),algorithm));byte[] h=mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());int o=h[h.length-1]&15;int binary=((h[o]&127)<<24)|((h[o+1]&255)<<16)|((h[o+2]&255)<<8)|(h[o+3]&255);int mod=(int)Math.pow(10,digits);return String.format(java.util.Locale.ROOT,"%0"+digits+"d",binary%mod);}catch(Exception e){throw new IllegalStateException("Unable to verify TOTP",e);}}
    private String encodeBase32(byte[] data){StringBuilder out=new StringBuilder();int buffer=0,bits=0;for(byte value:data){buffer=(buffer<<8)|(value&255);bits+=8;while(bits>=5){out.append(BASE32[(buffer>>(bits-5))&31]);bits-=5;}}if(bits>0)out.append(BASE32[(buffer<<(5-bits))&31]);return out.toString();}
    private byte[] decodeBase32(String value){String s=value.replace("=","").replace(" ","").toUpperCase(java.util.Locale.ROOT);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();int buffer=0,bits=0;for(char c:s.toCharArray()){int v=index(c);if(v<0)throw new IllegalArgumentException("Invalid Base32 secret");buffer=(buffer<<5)|v;bits+=5;if(bits>=8){out.write((buffer>>(bits-8))&255);bits-=8;}}return out.toByteArray();}
    private int index(char c){if(c>='A'&&c<='Z')return c-'A';if(c>='2'&&c<='7')return 26+c-'2';return -1;}
}
