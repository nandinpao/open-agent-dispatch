package com.opensocket.aievent.core.issuetracking.identity;
import java.time.OffsetDateTime;
/** Verified Human-to-Provider actor binding. It contains identifiers only, never a token or secret. */
public record ExternalActorBindingRef(String tenantId,String bindingId,String humanPrincipalId,String connectionId,String providerActorId,
 String integrationPrincipalId,String credentialId,ExternalActorBindingStatus status,OffsetDateTime verifiedAt,OffsetDateTime expiresAt,long version) {
 public ExternalActorBindingRef {tenantId=req(tenantId,"tenantId");bindingId=req(bindingId,"bindingId");humanPrincipalId=req(humanPrincipalId,"humanPrincipalId");
 connectionId=req(connectionId,"connectionId");providerActorId=req(providerActorId,"providerActorId");integrationPrincipalId=req(integrationPrincipalId,"integrationPrincipalId");
 credentialId=req(credentialId,"credentialId");status=status==null?ExternalActorBindingStatus.PENDING_VERIFICATION:status;if(version<1)throw new IllegalArgumentException("version must be positive");}
 public boolean verifiedAt(OffsetDateTime at){return status==ExternalActorBindingStatus.VERIFIED&&verifiedAt!=null&&(expiresAt==null||expiresAt.isAfter(at));}
 private static String req(String v,String n){if(v==null||v.isBlank())throw new IllegalArgumentException(n+" is required");return v.trim();}
}
