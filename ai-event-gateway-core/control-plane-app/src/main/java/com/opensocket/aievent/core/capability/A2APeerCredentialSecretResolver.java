package com.opensocket.aievent.core.capability;

import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * C0-B8 opaque peer credential resolver.
 *
 * <p>Persistent state stores only {@code secret_ref}; raw credential material is resolved at request time.
 * The C0-B8 runtime implementation deliberately supports {@code env://NAME} only. mTLS client identity,
 * vault/KMS providers and certificate pin material remain later runtime-hardening work.</p>
 */
@Component
public final class A2APeerCredentialSecretResolver {
    public boolean canResolve(String credentialType,String secretRef,String headerName,String authScheme){
        try{resolve(credentialType,secretRef,headerName,authScheme);return true;}catch(RuntimeException ex){return false;}
    }

    public ResolvedCredential resolve(String credentialType,String secretRef,String headerName,String authScheme){
        String type=required(credentialType,"credentialType").toUpperCase(Locale.ROOT);
        if("MTLS".equals(type))throw new IllegalArgumentException("A2A_CREDENTIAL_TYPE_NOT_RUNTIME_SUPPORTED:MTLS");
        if(!"BEARER_TOKEN".equals(type)&&!"API_KEY_HEADER".equals(type))throw new IllegalArgumentException("A2A_CREDENTIAL_TYPE_INVALID");
        String ref=required(secretRef,"secretRef");
        if(!ref.startsWith("env://"))throw new IllegalArgumentException("A2A_CREDENTIAL_SECRET_REF_PROVIDER_NOT_RUNTIME_SUPPORTED");
        String envName=ref.substring("env://".length()).trim();
        if(!envName.matches("[A-Za-z_][A-Za-z0-9_]*"))throw new IllegalArgumentException("A2A_CREDENTIAL_ENV_REF_INVALID");
        String value=System.getenv(envName);
        if(value==null||value.isBlank())throw new IllegalArgumentException("A2A_CREDENTIAL_SECRET_UNAVAILABLE");
        if("BEARER_TOKEN".equals(type)){
            String scheme=authScheme==null||authScheme.isBlank()?"Bearer":authScheme.trim();
            return new ResolvedCredential("Authorization",scheme+" "+value);
        }
        String header=required(headerName,"headerName");
        if(!header.matches("[A-Za-z0-9!#$%&'*+.^_`|~-]+"))throw new IllegalArgumentException("A2A_CREDENTIAL_HEADER_INVALID");
        return new ResolvedCredential(header,value);
    }

    private static String required(String value,String field){if(value==null||value.isBlank())throw new IllegalArgumentException("A2A_CREDENTIAL_"+field.toUpperCase(Locale.ROOT)+"_REQUIRED");return value.trim();}

    /** Header material is intentionally non-record/non-printable to avoid accidental secret logging. */
    public static final class ResolvedCredential {
        private final String headerName; private final String headerValue;
        private ResolvedCredential(String headerName,String headerValue){this.headerName=headerName;this.headerValue=headerValue;}
        public String headerName(){return headerName;} public String headerValue(){return headerValue;}
        @Override public String toString(){return "ResolvedCredential[REDACTED]";}
    }
}
