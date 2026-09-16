package com.opensocket.aievent.core.iam.authentication.application.result;
import com.opensocket.aievent.core.iam.authentication.domain.CredentialSubjectType;import java.time.Instant;
public record PasswordAuthenticationResult(CredentialSubjectType subjectType,String subjectId,String username,String tenantId,boolean mfaRequired,boolean passwordChangeRequired,long credentialVersion,Instant authenticatedAt){}
