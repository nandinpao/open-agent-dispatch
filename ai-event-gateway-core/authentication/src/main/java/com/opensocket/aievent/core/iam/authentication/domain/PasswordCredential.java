package com.opensocket.aievent.core.iam.authentication.domain;

import java.time.Instant;
import java.util.Objects;

public final class PasswordCredential {
    private final CredentialSubjectType subjectType;
    private final String subjectId;
    private final PasswordHash passwordHash;
    private final Instant changedAt;
    private final Instant expiresAt;
    private final boolean mustChange;
    private final long version;

    private PasswordCredential(CredentialSubjectType subjectType, String subjectId, PasswordHash passwordHash,
                               Instant changedAt, Instant expiresAt, boolean mustChange, long version) {
        this.subjectType = Objects.requireNonNull(subjectType, "subjectType");
        this.subjectId = Text.required(subjectId, "subjectId", 128);
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.changedAt = Objects.requireNonNull(changedAt, "changedAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(changedAt)) throw new IllegalArgumentException("expiresAt must be after changedAt");
        this.mustChange = mustChange;
        if (version < 1) throw new IllegalArgumentException("version must be positive");
        this.version = version;
    }
    public static PasswordCredential create(CredentialSubjectType type,String id,PasswordHash hash,Instant at,PasswordPolicy policy,boolean mustChange){return new PasswordCredential(type,id,hash,at,at.plus(policy.maximumAge()),mustChange,1);}
    public static PasswordCredential reconstitute(CredentialSubjectType type,String id,PasswordHash hash,Instant changedAt,Instant expiresAt,boolean mustChange,long version){return new PasswordCredential(type,id,hash,changedAt,expiresAt,mustChange,version);}
    public PasswordCredential rotate(PasswordHash hash,Instant at,PasswordPolicy policy,boolean requireChange){return new PasswordCredential(subjectType,subjectId,hash,at,at.plus(policy.maximumAge()),requireChange,version+1);}
    public PasswordCredential requireChange(){return mustChange?this:new PasswordCredential(subjectType,subjectId,passwordHash,changedAt,expiresAt,true,version+1);}
    public boolean expiredAt(Instant now){return !now.isBefore(expiresAt);}
    public CredentialSubjectType subjectType(){return subjectType;} public String subjectId(){return subjectId;} public PasswordHash passwordHash(){return passwordHash;} public Instant changedAt(){return changedAt;} public Instant expiresAt(){return expiresAt;} public boolean mustChange(){return mustChange;} public long version(){return version;}
}
