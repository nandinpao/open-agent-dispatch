package com.opensocket.aievent.core.iam.persistence.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("aeg.iam.authentication")
public class IamAuthenticationProperties {
    private boolean enabled;
    private String mfaKeyId = "iam-mfa-v1";
    private String mfaMasterKeyBase64 = "";
    private String secretPepperBase64 = "";
    private int totpWindow = 1;
    public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
    public String getMfaKeyId(){return mfaKeyId;} public void setMfaKeyId(String v){mfaKeyId=v;}
    public String getMfaMasterKeyBase64(){return mfaMasterKeyBase64;} public void setMfaMasterKeyBase64(String v){mfaMasterKeyBase64=v;}
    public String getSecretPepperBase64(){return secretPepperBase64;} public void setSecretPepperBase64(String v){secretPepperBase64=v;}
    public int getTotpWindow(){return totpWindow;} public void setTotpWindow(int v){if(v<0||v>2)throw new IllegalArgumentException("totpWindow must be 0..2");totpWindow=v;}
}
