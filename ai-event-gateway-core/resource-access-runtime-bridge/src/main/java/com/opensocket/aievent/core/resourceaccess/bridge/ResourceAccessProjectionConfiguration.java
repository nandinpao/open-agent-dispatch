package com.opensocket.aievent.core.resourceaccess.bridge;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.resourceaccess.core.*;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** P4RA-B composition. Disabled by default; no enforcement behavior is activated. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="resource-access",name="enabled",havingValue="true")
public class ResourceAccessProjectionConfiguration {
    @Bean DefaultResourceCatalog resourceCatalog(){return new DefaultResourceCatalog();}
    @Bean OwnershipRequirementPolicy ownershipRequirementPolicy(){return new OwnershipRequirementPolicy();}
    @Bean AuthoritativeResourceDescriptorService authoritativeResourceDescriptorService(DefaultResourceCatalog catalog,List<ResourceDescriptorResolverPort> resolvers){return new AuthoritativeResourceDescriptorService(catalog,resolvers);}
    @Bean AuthoritativeResourceParticipantService authoritativeResourceParticipantService(List<ResourceParticipantResolverPort> resolvers){return new AuthoritativeResourceParticipantService(resolvers);}
    @Bean ResourceProjectionService resourceProjectionService(AuthoritativeResourceDescriptorService descriptors,AuthoritativeResourceParticipantService participants,ResourceProjectionRepository repository,OwnershipRequirementPolicy ownership){return new ResourceProjectionService(descriptors,participants,repository,ownership);}
    @Bean ResourceDescriptorReconciliationService resourceDescriptorReconciliationService(AuthoritativeResourceDescriptorService descriptors,ResourceProjectionRepository repository,ResourceProjectionService projection,OwnershipRequirementPolicy ownership){return new ResourceDescriptorReconciliationService(descriptors,repository,projection,ownership);}
    @Bean ResourceOwnershipTransferService resourceOwnershipTransferService(List<ResourceOwnershipAuthorityPort> authorities,ResourceProjectionService projection,ResourceProjectionRepository repository){return new ResourceOwnershipTransferService(authorities,projection,repository);}
    @Bean ResourceOrphanRepairService resourceOrphanRepairService(ResourceProjectionRepository repository,ResourceOwnershipTransferService transfers){return new ResourceOrphanRepairService(repository,transfers);}
    @Bean ExplicitDenyPrecedencePolicy explicitDenyPrecedencePolicy(){return new ExplicitDenyPrecedencePolicy();}
    @Bean ResourceScopeGrantService resourceScopeGrantService(ResourcePolicyRepository repository){return new ResourceScopeGrantService(repository);}
    @Bean ResourceExplicitDenyService resourceExplicitDenyService(ResourcePolicyRepository repository){return new ResourceExplicitDenyService(repository);}
    @Bean ResourceVisibilityPolicyService resourceVisibilityPolicyService(ResourcePolicyRepository repository){return new ResourceVisibilityPolicyService(repository);}
    @Bean PrincipalClearanceService principalClearanceService(ResourcePolicyRepository repository){return new PrincipalClearanceService(repository);}
    @Bean ResourceSecurityStateService resourceSecurityStateService(ResourcePolicyRepository repository){return new ResourceSecurityStateService(repository);}
    @Bean ResourceGovernanceService resourceGovernanceService(ResourceGovernanceRepository repository){return new ResourceGovernanceService(repository);}
}
