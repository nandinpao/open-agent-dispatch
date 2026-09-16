package com.opensocket.aievent.core.iam.runtime.config;

import java.time.Duration;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("aeg.iam.machine-token")
public class IamMachineTokenProperties {
    private boolean enabled;
    private String issuer = "http://localhost:18080";
    private Duration accessTokenTtl = Duration.ofMinutes(10);
    private Duration minimumAccessTokenTtl = Duration.ofMinutes(1);
    private Duration clockSkew = Duration.ofSeconds(30);
    private Duration signingKeyRotation = Duration.ofDays(30);
    private Duration signingKeyVerificationGrace = Duration.ofHours(1);
    private int rsaKeySize = 3072;
    private String signingKeyProtectionKeyId = "iam-machine-jwt-v1";
    private String signingKeyProtectionKeyBase64 = "";
    private int tokenEndpointRateLimitPerMinute = 120;
    private int perClientRateLimitPerMinute = 30;
    private int jwksCacheMaxAgeSeconds = 300;
    private boolean legacyTokenEndpointEnabled = false;
    private boolean allowLegacyAudienceParameter = true;
    private List<String> trustedProxyCidrs = new ArrayList<>();

    public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
    public String getIssuer(){return issuer;} public void setIssuer(String v){issuer=v;}
    public Duration getAccessTokenTtl(){return accessTokenTtl;} public void setAccessTokenTtl(Duration v){accessTokenTtl=v;}
    public Duration getMinimumAccessTokenTtl(){return minimumAccessTokenTtl;} public void setMinimumAccessTokenTtl(Duration v){minimumAccessTokenTtl=v;}
    public Duration getClockSkew(){return clockSkew;} public void setClockSkew(Duration v){clockSkew=v;}
    public Duration getSigningKeyRotation(){return signingKeyRotation;} public void setSigningKeyRotation(Duration v){signingKeyRotation=v;}
    public Duration getSigningKeyVerificationGrace(){return signingKeyVerificationGrace;} public void setSigningKeyVerificationGrace(Duration v){signingKeyVerificationGrace=v;}
    public int getRsaKeySize(){return rsaKeySize;} public void setRsaKeySize(int v){rsaKeySize=v;}
    public String getSigningKeyProtectionKeyId(){return signingKeyProtectionKeyId;} public void setSigningKeyProtectionKeyId(String v){signingKeyProtectionKeyId=v;}
    public String getSigningKeyProtectionKeyBase64(){return signingKeyProtectionKeyBase64;} public void setSigningKeyProtectionKeyBase64(String v){signingKeyProtectionKeyBase64=v;}
    public int getTokenEndpointRateLimitPerMinute(){return tokenEndpointRateLimitPerMinute;} public void setTokenEndpointRateLimitPerMinute(int v){tokenEndpointRateLimitPerMinute=v;}
    public int getPerClientRateLimitPerMinute(){return perClientRateLimitPerMinute;} public void setPerClientRateLimitPerMinute(int v){perClientRateLimitPerMinute=v;}
    public int getJwksCacheMaxAgeSeconds(){return jwksCacheMaxAgeSeconds;} public void setJwksCacheMaxAgeSeconds(int v){jwksCacheMaxAgeSeconds=v;}
    public boolean isLegacyTokenEndpointEnabled(){return legacyTokenEndpointEnabled;} public void setLegacyTokenEndpointEnabled(boolean v){legacyTokenEndpointEnabled=v;}
    public boolean isAllowLegacyAudienceParameter(){return allowLegacyAudienceParameter;} public void setAllowLegacyAudienceParameter(boolean v){allowLegacyAudienceParameter=v;}
    public List<String> getTrustedProxyCidrs(){return List.copyOf(trustedProxyCidrs);} public void setTrustedProxyCidrs(List<String> v){
        trustedProxyCidrs=new ArrayList<>();
        if(v!=null) for(String value:v) if(value!=null&&!value.isBlank()) trustedProxyCidrs.add(value.trim());
    }

    public byte[] requireProtectionKey() {
        if (signingKeyProtectionKeyBase64 == null || signingKeyProtectionKeyBase64.isBlank()) {
            throw new IllegalStateException("AEG_IAM_MACHINE_TOKEN_SIGNING_KEY_PROTECTION_KEY_BASE64 is required when machine tokens are enabled");
        }
        byte[] decoded;
        try { decoded = Base64.getDecoder().decode(signingKeyProtectionKeyBase64.trim()); }
        catch (IllegalArgumentException e) { throw new IllegalStateException("Machine signing-key protection key must be valid Base64", e); }
        if (decoded.length != 32) throw new IllegalStateException("Machine signing-key protection key must decode to exactly 32 bytes");
        return decoded;
    }

    public void validateEnabled() {
        if (!enabled) return;
        requireProtectionKey();
        if (issuer == null || issuer.isBlank()) throw new IllegalStateException("Machine token issuer is required");
        try { java.net.URI parsed = java.net.URI.create(issuer.trim()); if (!parsed.isAbsolute()) throw new IllegalArgumentException(); }
        catch (RuntimeException ex) { throw new IllegalStateException("Machine token issuer must be an absolute URI for OAuth Authorization Server metadata", ex); }
        for (String cidr : trustedProxyCidrs) if (cidr != null && !cidr.isBlank()) com.opensocket.aievent.core.iam.token.domain.CidrBlock.parse(cidr.trim());
        if (accessTokenTtl == null || accessTokenTtl.isNegative() || accessTokenTtl.isZero() || accessTokenTtl.compareTo(Duration.ofHours(1)) > 0) throw new IllegalStateException("Machine access token TTL must be >0 and <=1 hour");
        if (minimumAccessTokenTtl == null || minimumAccessTokenTtl.isNegative() || minimumAccessTokenTtl.isZero() || minimumAccessTokenTtl.compareTo(accessTokenTtl) > 0) throw new IllegalStateException("Machine minimum token TTL is invalid");
        if (clockSkew == null || clockSkew.isNegative() || clockSkew.compareTo(Duration.ofMinutes(5)) > 0) throw new IllegalStateException("Machine JWT clock skew must be between 0 and 5 minutes");
        if (signingKeyRotation == null || signingKeyRotation.compareTo(Duration.ofHours(1)) < 0) throw new IllegalStateException("Machine signing-key rotation must be at least 1 hour");
        if (signingKeyVerificationGrace == null || signingKeyVerificationGrace.compareTo(accessTokenTtl.plus(clockSkew)) < 0) throw new IllegalStateException("Machine signing-key verification grace must cover access token TTL + clock skew");
        if (rsaKeySize < 2048) throw new IllegalStateException("Machine JWT RSA key size must be at least 2048");
        if (tokenEndpointRateLimitPerMinute < 1 || perClientRateLimitPerMinute < 1) throw new IllegalStateException("Machine OAuth rate limits must be positive");
        if (jwksCacheMaxAgeSeconds < 0 || jwksCacheMaxAgeSeconds > 3600) throw new IllegalStateException("JWKS cache max age must be 0..3600 seconds");
    }
}
