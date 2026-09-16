package com.opensocket.aievent.core.iam.runtime.security;
import org.springframework.core.env.Environment;
/** Provider rows store references only. P2.4 supports env:NAME and property:fully.qualified.name secret references. */
public final class FederationClientSecretResolver {
    private final Environment environment;
    public FederationClientSecretResolver(Environment environment){this.environment=environment;}
    public String resolve(String reference){
        if(reference==null||reference.isBlank())throw new IllegalStateException("AUTH_FEDERATION_CLIENT_SECRET_REFERENCE_REQUIRED");
        String key;
        if(reference.startsWith("env:")) key=reference.substring(4);
        else if(reference.startsWith("property:")) key=reference.substring(9);
        else throw new IllegalStateException("AUTH_FEDERATION_CLIENT_SECRET_REFERENCE_INVALID");
        if(key.isBlank())throw new IllegalStateException("AUTH_FEDERATION_CLIENT_SECRET_REFERENCE_INVALID");
        String value=environment.getProperty(key);
        if(value==null||value.isBlank())throw new IllegalStateException("AUTH_FEDERATION_CLIENT_SECRET_UNAVAILABLE");
        return value;
    }
}
