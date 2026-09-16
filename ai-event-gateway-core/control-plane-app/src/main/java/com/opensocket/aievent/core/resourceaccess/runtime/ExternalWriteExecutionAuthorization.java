package com.opensocket.aievent.core.resourceaccess.runtime;
import com.opensocket.aievent.core.resourceaccess.contract.ExternalWriteAuthorizationContext;
import com.opensocket.aievent.core.issuetracking.identity.ProviderWriteIdentityResolution;
/** Human authorization plus secret-free Provider identity resolution. Secret material remains behind Secret Bridge. */
public record ExternalWriteExecutionAuthorization(ExternalWriteAuthorizationContext humanAuthorization,ProviderWriteIdentityResolution providerIdentity,String attributionId) {
 public boolean executable(){return humanAuthorization.executable();}
}
