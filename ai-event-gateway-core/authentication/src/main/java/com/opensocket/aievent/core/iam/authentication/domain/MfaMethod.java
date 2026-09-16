package com.opensocket.aievent.core.iam.authentication.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class MfaMethod {
    private final String methodId;
    private final CredentialSubjectType subjectType;
    private final String subjectId;
    private final Type type;
    private final Status status;
    private final String protectedSecret;
    private final String keyId;
    private final int digits;
    private final int periodSeconds;
    private final String algorithm;
    private final Instant enrolledAt;
    private final Optional<Instant> verifiedAt;
    private final long version;
    private MfaMethod(String methodId,CredentialSubjectType subjectType,String subjectId,Type type,Status status,String protectedSecret,String keyId,int digits,int periodSeconds,String algorithm,Instant enrolledAt,Optional<Instant> verifiedAt,long version){
        this.methodId=Text.required(methodId,"methodId",128);this.subjectType=Objects.requireNonNull(subjectType,"subjectType");this.subjectId=Text.required(subjectId,"subjectId",128);this.type=Objects.requireNonNull(type,"type");this.status=Objects.requireNonNull(status,"status");this.protectedSecret=Text.required(protectedSecret,"protectedSecret",4096);this.keyId=Text.required(keyId,"keyId",128);if(digits<6||digits>8)throw new IllegalArgumentException("digits must be 6..8");this.digits=digits;if(periodSeconds<15||periodSeconds>120)throw new IllegalArgumentException("periodSeconds must be 15..120");this.periodSeconds=periodSeconds;this.algorithm=Text.required(algorithm,"algorithm",32);this.enrolledAt=Objects.requireNonNull(enrolledAt,"enrolledAt");this.verifiedAt=verifiedAt==null?Optional.empty():verifiedAt;if(version<1)throw new IllegalArgumentException("version must be positive");this.version=version;}
    public static MfaMethod pendingTotp(String methodId,CredentialSubjectType type,String subjectId,String protectedSecret,String keyId,Instant at){return new MfaMethod(methodId,type,subjectId,Type.TOTP,Status.PENDING,protectedSecret,keyId,6,30,"HmacSHA1",at,Optional.empty(),1);}
    public static MfaMethod reconstitute(String methodId,CredentialSubjectType type,String subjectId,Type methodType,Status status,String protectedSecret,String keyId,int digits,int periodSeconds,String algorithm,Instant enrolledAt,Optional<Instant> verifiedAt,long version){return new MfaMethod(methodId,type,subjectId,methodType,status,protectedSecret,keyId,digits,periodSeconds,algorithm,enrolledAt,verifiedAt,version);}
    public MfaMethod activate(Instant at){if(status!=Status.PENDING)throw new AuthenticationDomainException(AuthenticationReasonCode.AUTH_MFA_INVALID,"Only pending MFA may be activated");return new MfaMethod(methodId,subjectType,subjectId,type,Status.ACTIVE,protectedSecret,keyId,digits,periodSeconds,algorithm,enrolledAt,Optional.of(at),version+1);}
    public MfaMethod disable(){return new MfaMethod(methodId,subjectType,subjectId,type,Status.DISABLED,protectedSecret,keyId,digits,periodSeconds,algorithm,enrolledAt,verifiedAt,version+1);}
    public boolean active(){return status==Status.ACTIVE;}
    public String methodId(){return methodId;} public CredentialSubjectType subjectType(){return subjectType;} public String subjectId(){return subjectId;} public Type type(){return type;} public Status status(){return status;} public String protectedSecret(){return protectedSecret;} public String keyId(){return keyId;} public int digits(){return digits;} public int periodSeconds(){return periodSeconds;} public String algorithm(){return algorithm;} public Instant enrolledAt(){return enrolledAt;} public Optional<Instant> verifiedAt(){return verifiedAt;} public long version(){return version;}
    public enum Type{TOTP} public enum Status{PENDING,ACTIVE,DISABLED}
}
