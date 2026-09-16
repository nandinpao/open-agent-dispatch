package com.opensocket.aievent.core.iam.runtime.config;

import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("aeg.iam.federation")
public class IamFederationProperties {
    private boolean enabled;
    private String oidcCallbackUrl = "";
    private String stateSecretBase64 = "";
    private Duration loginAttemptTtl = Duration.ofMinutes(5);
    public boolean isEnabled(){return enabled;} public void setEnabled(boolean value){enabled=value;}
    public String getOidcCallbackUrl(){return oidcCallbackUrl;} public void setOidcCallbackUrl(String value){oidcCallbackUrl=value==null?"":value.trim();}
    public String getStateSecretBase64(){return stateSecretBase64;} public void setStateSecretBase64(String value){stateSecretBase64=value==null?"":value.trim();}
    public Duration getLoginAttemptTtl(){return loginAttemptTtl;} public void setLoginAttemptTtl(Duration value){loginAttemptTtl=value==null?Duration.ofMinutes(5):value;}
    private void validateCallbackUrl(){
        try{
            URI uri=URI.create(oidcCallbackUrl);
            boolean localhost="localhost".equalsIgnoreCase(uri.getHost())||"127.0.0.1".equals(uri.getHost())||"::1".equals(uri.getHost());
            boolean schemeOk="https".equalsIgnoreCase(uri.getScheme())||(localhost&&"http".equalsIgnoreCase(uri.getScheme()));
            if(!uri.isAbsolute()||uri.getHost()==null||!schemeOk||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null){
                throw new IllegalStateException("aeg.iam.federation.oidc-callback-url must be an absolute HTTPS URL; HTTP is allowed only for localhost development and userinfo/query/fragment are forbidden");
            }
        }catch(IllegalArgumentException ex){
            throw new IllegalStateException("aeg.iam.federation.oidc-callback-url is invalid",ex);
        }
    }
    public byte[] requireStateSecret(){
        if(!enabled) return new byte[0];
        if(oidcCallbackUrl.isBlank()) throw new IllegalStateException("aeg.iam.federation.oidc-callback-url is required when federation is enabled");
        validateCallbackUrl();
        if(stateSecretBase64.isBlank()) throw new IllegalStateException("aeg.iam.federation.state-secret-base64 is required when federation is enabled");
        byte[] decoded=Base64.getDecoder().decode(stateSecretBase64);
        if(decoded.length<32) throw new IllegalStateException("aeg.iam.federation.state-secret-base64 must contain at least 32 decoded bytes");
        if(loginAttemptTtl.isNegative()||loginAttemptTtl.isZero()||loginAttemptTtl.compareTo(Duration.ofMinutes(15))>0) throw new IllegalStateException("aeg.iam.federation.login-attempt-ttl must be between >0 and 15m");
        return decoded;
    }
}
