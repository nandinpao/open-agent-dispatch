package com.opensocket.aievent.core.iam.runtime.security;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/** Minimal authorization-code + PKCE OIDC client. Authorization claims never become OpenDispatch roles. */
public final class OidcProtocolClient {
    private final RestClient http;
    private final FederationClientSecretResolver secrets;
    private final Map<String,Discovery> discoveryCache=new ConcurrentHashMap<>();
    private final Map<String,JwtDecoder> decoderCache=new ConcurrentHashMap<>();

    public OidcProtocolClient(FederationClientSecretResolver secrets){this(RestClient.create(),secrets);}
    OidcProtocolClient(RestClient http,FederationClientSecretResolver secrets){this.http=http;this.secrets=secrets;}

    public String authorizationUrl(Provider provider,String callback,String state,String nonce,String codeChallenge){
        Discovery d=discover(provider.issuerUri());
        return UriComponentsBuilder.fromUriString(d.authorizationEndpoint())
                .queryParam("response_type","code")
                .queryParam("client_id",provider.clientId())
                .queryParam("redirect_uri",callback)
                .queryParam("scope",String.join(" ",provider.scopes()))
                .queryParam("state",state)
                .queryParam("nonce",nonce)
                .queryParam("code_challenge",codeChallenge)
                .queryParam("code_challenge_method","S256")
                .build().encode().toUriString();
    }

    @SuppressWarnings("unchecked")
    public IdentityEvidence exchange(Provider provider,String callback,String code,String verifier,String expectedNonce){
        if(code==null||code.isBlank())throw new IllegalArgumentException("AUTH_FEDERATION_CODE_MISSING");
        Discovery d=discover(provider.issuerUri());
        LinkedMultiValueMap<String,String> form=new LinkedMultiValueMap<>();
        form.add("grant_type","authorization_code"); form.add("code",code); form.add("redirect_uri",callback);
        form.add("client_id",provider.clientId()); form.add("client_secret",secrets.resolve(provider.clientSecretRef()));
        form.add("code_verifier",verifier);
        Map<String,Object> token=http.post().uri(d.tokenEndpoint()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().body(Map.class);
        String idToken=text(token,"id_token");
        if(idToken.isBlank())throw new IllegalStateException("AUTH_FEDERATION_ID_TOKEN_MISSING");
        Jwt jwt=decoderCache.computeIfAbsent(provider.issuerUri(),JwtDecoders::fromIssuerLocation).decode(idToken);
        if(!issuer(provider.issuerUri()).equals(issuer(jwt.getIssuer()==null?"":jwt.getIssuer().toString())))throw new IllegalStateException("AUTH_FEDERATION_ISSUER_MISMATCH");
        if(jwt.getAudience()==null||!jwt.getAudience().contains(provider.clientId()))throw new IllegalStateException("AUTH_FEDERATION_AUDIENCE_MISMATCH");
        if(!Objects.equals(expectedNonce,jwt.getClaimAsString("nonce")))throw new IllegalStateException("AUTH_FEDERATION_NONCE_MISMATCH");
        String subject=claim(jwt,provider.subjectClaim());
        if(subject.isBlank())throw new IllegalStateException("AUTH_FEDERATION_SUBJECT_MISSING");
        String username=claim(jwt,provider.usernameClaim());
        String email=claim(jwt,provider.emailClaim());
        String displayName=claim(jwt,provider.displayNameClaim());
        Boolean emailVerified=booleanClaim(jwt,"email_verified");
        List<String> amr=stringListClaim(jwt,provider.amrClaim());
        String acr=claim(jwt,provider.acrClaim());
        boolean mfaAsserted=mfaAsserted(provider,amr,acr);
        return new IdentityEvidence(provider.issuerUri(),subject,username,email,displayName,emailVerified,amr,acr,mfaAsserted,jwt.getIssuedAt(),jwt.getExpiresAt());
    }

    @SuppressWarnings("unchecked")
    private Discovery discover(String issuer){
        String normalized=issuer(issuer);
        return discoveryCache.computeIfAbsent(normalized,key->{
            String url=key+"/.well-known/openid-configuration";
            Map<String,Object> doc=http.get().uri(URI.create(url)).retrieve().body(Map.class);
            String actualIssuer=text(doc,"issuer");
            if(!key.equals(issuer(actualIssuer)))throw new IllegalStateException("AUTH_FEDERATION_DISCOVERY_ISSUER_MISMATCH");
            String authorization=text(doc,"authorization_endpoint"),token=text(doc,"token_endpoint"),jwks=text(doc,"jwks_uri");
            if(authorization.isBlank()||token.isBlank()||jwks.isBlank())throw new IllegalStateException("AUTH_FEDERATION_DISCOVERY_INCOMPLETE");
            return new Discovery(actualIssuer,authorization,token,jwks);
        });
    }
    private static boolean mfaAsserted(Provider provider,List<String> amr,String acr){
        boolean amrTrusted=amr.stream().map(v->v.toLowerCase(Locale.ROOT)).anyMatch(v->provider.trustedAmrValues().stream().map(x->x.toLowerCase(Locale.ROOT)).anyMatch(v::equals));
        boolean acrTrusted=!acr.isBlank()&&provider.trustedAcrValues().stream().anyMatch(acr::equals);
        return switch(provider.upstreamMfaMode()){
            case "NONE" -> false;
            case "TRUST_AMR" -> amrTrusted;
            case "TRUST_ACR" -> acrTrusted;
            case "TRUST_AMR_OR_ACR","REQUIRE_ASSERTED" -> amrTrusted||acrTrusted;
            default -> throw new IllegalStateException("AUTH_FEDERATION_MFA_MODE_INVALID");
        };
    }
    private static String issuer(String value){String v=value==null?"":value.trim();while(v.endsWith("/"))v=v.substring(0,v.length()-1);return v;}
    private static String claim(Jwt jwt,String name){if(name==null||name.isBlank())return "";Object value=jwt.getClaims().get(name);return value==null?"":String.valueOf(value).trim();}
    private static Boolean booleanClaim(Jwt jwt,String name){Object value=jwt.getClaims().get(name);if(value instanceof Boolean b)return b;if(value==null)return null;return Boolean.valueOf(String.valueOf(value));}
    private static List<String> stringListClaim(Jwt jwt,String name){Object value=jwt.getClaims().get(name);if(value instanceof Collection<?> c)return c.stream().map(String::valueOf).map(String::trim).filter(v->!v.isBlank()).toList();if(value==null)return List.of();String text=String.valueOf(value).trim();return text.isBlank()?List.of():List.of(text);}
    private static String text(Map<String,Object> map,String key){if(map==null)return "";Object value=map.get(key);return value==null?"":String.valueOf(value).trim();}

    private record Discovery(String issuer,String authorizationEndpoint,String tokenEndpoint,String jwksUri){}
    public record Provider(String tenantId,String providerId,String providerCode,String displayName,String issuerUri,String clientId,String clientSecretRef,List<String> scopes,String subjectClaim,String usernameClaim,String emailClaim,String displayNameClaim,String amrClaim,String acrClaim,String upstreamMfaMode,List<String> trustedAmrValues,List<String> trustedAcrValues,String linkMode,String jitMode){}
    public record IdentityEvidence(String issuer,String subject,String username,String email,String displayName,Boolean emailVerified,List<String> amr,String acr,boolean mfaAsserted,Instant issuedAt,Instant expiresAt){}
}
