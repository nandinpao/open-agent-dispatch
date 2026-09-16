package com.opensocket.aievent.core.integration.issue.webhook;
import java.nio.charset.StandardCharsets; import java.security.MessageDigest; import java.time.OffsetDateTime; import java.util.*; import javax.crypto.Mac; import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.ObjectProvider; import org.springframework.stereotype.Component;
import com.opensocket.aievent.core.integration.identity.*;
/** Per-Principal HMAC-SHA256 verifier. It resolves only credential references and never stores or logs secret material. */
@Component public class HmacProviderWebhookSignatureVerifier implements ProviderWebhookSignatureVerifier {
 private final ObjectProvider<IntegrationSecretResolver> resolvers; public HmacProviderWebhookSignatureVerifier(ObjectProvider<IntegrationSecretResolver> resolvers){this.resolvers=resolvers;}
 public ProviderWebhookSignatureResult verify(ProviderWebhookSigningRequest request,String supplied,List<IntegrationCredentialMetadata> candidates){
  if(supplied==null||supplied.isBlank())return ProviderWebhookSignatureResult.rejected("WEBHOOK_SIGNATURE_REQUIRED");
  IntegrationSecretResolver resolver=resolvers.getIfAvailable(); if(resolver==null)return ProviderWebhookSignatureResult.rejected("WEBHOOK_SECRET_RESOLVER_UNAVAILABLE");
  String normalized=normalize(supplied);OffsetDateTime now=OffsetDateTime.now();boolean supported=false;
  for(var credential:candidates==null?List.<IntegrationCredentialMetadata>of():candidates){
   if(!usable(credential,now)||!resolver.supports(credential))continue;supported=true;
   try(var secret=resolver.resolve(credential)){char[] material=secret.value();try{String expected=hmac(material,request.signingInput());if(constant(expected,normalized))return ProviderWebhookSignatureResult.accepted(credential.credentialId(),credential.secretVersion());}finally{Arrays.fill(material,'\0');}}
   catch(RuntimeException ignored){/* fail closed; secret values and resolver details are intentionally not exposed */}
  }
  return ProviderWebhookSignatureResult.rejected(supported?"WEBHOOK_SIGNATURE_INVALID":"WEBHOOK_CREDENTIAL_UNAVAILABLE");
 }
 private boolean usable(IntegrationCredentialMetadata c,OffsetDateTime now){return c!=null&&(c.status()==IntegrationCredentialStatus.ACTIVE||c.status()==IntegrationCredentialStatus.GRACE_PERIOD)&&(c.validFrom()==null||!c.validFrom().isAfter(now))&&(c.expiresAt()==null||c.expiresAt().isAfter(now));}
 private String normalize(String v){String s=v.trim();if(s.regionMatches(true,0,"sha256=",0,7))s=s.substring(7);else if(s.regionMatches(true,0,"v1=",0,3))s=s.substring(3);return s.toLowerCase(Locale.ROOT);}
 private String hmac(char[] secret,String input){byte[] key=null;try{var encoded=StandardCharsets.UTF_8.encode(java.nio.CharBuffer.wrap(secret));key=new byte[encoded.remaining()];encoded.get(key);Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("WEBHOOK_HMAC_UNAVAILABLE",e);}finally{if(key!=null)java.util.Arrays.fill(key,(byte)0);}}
 private boolean constant(String a,String b){return a.length()==b.length()&&MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII),b.getBytes(StandardCharsets.US_ASCII));}
 public String mode(){return "HMAC_SHA256_V1_PER_PRINCIPAL";}
}
