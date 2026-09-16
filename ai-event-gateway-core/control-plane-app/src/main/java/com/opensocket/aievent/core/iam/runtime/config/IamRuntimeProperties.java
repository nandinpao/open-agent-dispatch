package com.opensocket.aievent.core.iam.runtime.config;

import java.time.Duration;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("aeg.iam.runtime")
public class IamRuntimeProperties {
    private boolean enabled;
    private String sessionCookieName = "OPENDISPATCH_SESSION";
    private boolean sessionCookieSecure = true;
    private String sessionCookieSameSite = "Lax";
    private String sessionCookiePath = "/";
    private String sessionCookieSigningSecretBase64 = "";
    private Duration loginChallengeTtl = Duration.ofMinutes(5);
    private String oneTimeSecretDeliveryDirectory = "";
    private String activationBaseUrl = "http://localhost:3000";
    private String activationDeliveryDefaultMethod = "DEVELOPMENT_FILE";
    private boolean activationEmailEnabled;
    private String activationMailFrom = "";

    /** Installer-owned Root provisioning. The password may be supplied inline or through a mounted Secret file. */
    private boolean rootInstallationEnabled = true;
    private boolean rootInstallationFailClosed = true;
    private String rootInitialPassword = "";
    private String rootInitialPasswordFile = "";
    private Duration rootPasswordChangeSessionTtl = Duration.ofMinutes(10);

    public boolean isEnabled(){return enabled;}
    public void setEnabled(boolean value){enabled=value;}
    public String getSessionCookieName(){return sessionCookieName;}
    public void setSessionCookieName(String value){sessionCookieName=value;}
    public boolean isSessionCookieSecure(){return sessionCookieSecure;}
    public void setSessionCookieSecure(boolean value){sessionCookieSecure=value;}
    public String getSessionCookieSameSite(){return sessionCookieSameSite;}
    public void setSessionCookieSameSite(String value){sessionCookieSameSite=value;}
    public String getSessionCookiePath(){return sessionCookiePath;}
    public void setSessionCookiePath(String value){sessionCookiePath=value;}
    public String getSessionCookieSigningSecretBase64(){return sessionCookieSigningSecretBase64;}
    public void setSessionCookieSigningSecretBase64(String value){sessionCookieSigningSecretBase64=value;}
    public Duration getLoginChallengeTtl(){return loginChallengeTtl;}
    public void setLoginChallengeTtl(Duration value){loginChallengeTtl=value;}
    public String getOneTimeSecretDeliveryDirectory(){return oneTimeSecretDeliveryDirectory;}
    public void setOneTimeSecretDeliveryDirectory(String value){oneTimeSecretDeliveryDirectory=value;}
    public String getActivationBaseUrl(){return activationBaseUrl;}
    public void setActivationBaseUrl(String value){activationBaseUrl=value;}
    public String getActivationDeliveryDefaultMethod(){return activationDeliveryDefaultMethod;}
    public void setActivationDeliveryDefaultMethod(String value){activationDeliveryDefaultMethod=value;}
    public boolean isActivationEmailEnabled(){return activationEmailEnabled;}
    public void setActivationEmailEnabled(boolean value){activationEmailEnabled=value;}
    public String getActivationMailFrom(){return activationMailFrom;}
    public void setActivationMailFrom(String value){activationMailFrom=value;}
    public boolean isRootInstallationEnabled(){return rootInstallationEnabled;}
    public void setRootInstallationEnabled(boolean value){rootInstallationEnabled=value;}
    public boolean isRootInstallationFailClosed(){return rootInstallationFailClosed;}
    public void setRootInstallationFailClosed(boolean value){rootInstallationFailClosed=value;}
    public String getRootInitialPassword(){return rootInitialPassword;}
    public void setRootInitialPassword(String value){rootInitialPassword=value;}
    public String getRootInitialPasswordFile(){return rootInitialPasswordFile;}
    public void setRootInitialPasswordFile(String value){rootInitialPasswordFile=value;}
    public Duration getRootPasswordChangeSessionTtl(){return rootPasswordChangeSessionTtl;}
    public void setRootPasswordChangeSessionTtl(Duration value){rootPasswordChangeSessionTtl=value;}
    public boolean hasConfiguredRootInstallationSecret(){
        return (rootInitialPassword != null && !rootInitialPassword.isEmpty())
                || (rootInitialPasswordFile != null && !rootInitialPasswordFile.isBlank());
    }

    public byte[] requireSigningKey(){
        if(sessionCookieSigningSecretBase64==null||sessionCookieSigningSecretBase64.isBlank()) {
            throw new IllegalStateException("AEG_IAM_RUNTIME_SESSION_COOKIE_SIGNING_SECRET_BASE64 is required");
        }
        byte[] decoded=Base64.getDecoder().decode(sessionCookieSigningSecretBase64.trim());
        if(decoded.length<32) throw new IllegalStateException("IAM session cookie signing key must decode to at least 32 bytes");
        return java.util.Arrays.copyOf(decoded,32);
    }

    public void validateEnabled(){
        if(!enabled)return;
        requireSigningKey();
        if(loginChallengeTtl==null||loginChallengeTtl.isZero()||loginChallengeTtl.isNegative()
                ||loginChallengeTtl.compareTo(Duration.ofMinutes(10))>0) {
            throw new IllegalStateException("IAM login challenge TTL must be between 1 second and 10 minutes");
        }
        if(oneTimeSecretDeliveryDirectory==null||oneTimeSecretDeliveryDirectory.isBlank()) {
            throw new IllegalStateException("AEG_IAM_RUNTIME_ONE_TIME_SECRET_DELIVERY_DIRECTORY is required");
        }
        if(sessionCookieName==null||!sessionCookieName.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalStateException("IAM session cookie name is invalid");
        }
        if(sessionCookiePath==null||!sessionCookiePath.startsWith("/")) {
            throw new IllegalStateException("IAM session cookie path is invalid");
        }
        if(sessionCookieSameSite==null||java.util.Set.of("Lax","Strict","None").stream()
                .noneMatch(v->v.equalsIgnoreCase(sessionCookieSameSite))) {
            throw new IllegalStateException("IAM session cookie SameSite is invalid");
        }
        if("None".equalsIgnoreCase(sessionCookieSameSite)&&!sessionCookieSecure) {
            throw new IllegalStateException("SameSite=None requires a Secure IAM session cookie");
        }
        boolean inline = rootInitialPassword != null && !rootInitialPassword.isEmpty();
        boolean file = rootInitialPasswordFile != null && !rootInitialPasswordFile.isBlank();
        if(inline && file) {
            throw new IllegalStateException("Configure only one of AEG_IAM_ROOT_INITIAL_PASSWORD or AEG_IAM_ROOT_INITIAL_PASSWORD_FILE");
        }
        String method=activationDeliveryDefaultMethod==null?"":activationDeliveryDefaultMethod.trim().toUpperCase(java.util.Locale.ROOT);
        if(!java.util.Set.of("EMAIL","MANUAL","DEVELOPMENT_FILE").contains(method)) {
            throw new IllegalStateException("aeg.iam.runtime.activation-delivery-default-method must be EMAIL, MANUAL or DEVELOPMENT_FILE");
        }
        activationDeliveryDefaultMethod=method;
        if("EMAIL".equals(method) && !activationEmailEnabled) {
            throw new IllegalStateException("AEG_IAM_RUNTIME_ACTIVATION_EMAIL_ENABLED must be true when the default activation delivery method is EMAIL");
        }
        if(activationBaseUrl==null||activationBaseUrl.isBlank()) {
            throw new IllegalStateException("aeg.iam.runtime.activation-base-url must be configured");
        }
        activationBaseUrl=activationBaseUrl.replaceAll("/+$","");
        if(rootPasswordChangeSessionTtl==null
                ||rootPasswordChangeSessionTtl.compareTo(Duration.ofMinutes(1))<0
                ||rootPasswordChangeSessionTtl.compareTo(Duration.ofMinutes(30))>0) {
            throw new IllegalStateException("Root password-change session TTL must be between 1 minute and 30 minutes");
        }
    }
}
