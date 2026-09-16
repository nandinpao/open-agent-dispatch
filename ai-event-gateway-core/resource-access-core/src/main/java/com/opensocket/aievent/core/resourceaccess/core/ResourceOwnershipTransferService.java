package com.opensocket.aievent.core.resourceaccess.core;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Routes ownership mutations to one canonical Domain authority, then refreshes the Resource Access projection. */
public final class ResourceOwnershipTransferService {
    private final Map<ResourceType, ResourceOwnershipAuthorityPort> authorities;
    private final ResourceProjectionService projection;
    private final ResourceProjectionRepository repository;
    public ResourceOwnershipTransferService(List<ResourceOwnershipAuthorityPort> ports, ResourceProjectionService projection, ResourceProjectionRepository repository) {
        EnumMap<ResourceType,ResourceOwnershipAuthorityPort> map=new EnumMap<>(ResourceType.class);
        for(ResourceOwnershipAuthorityPort port:ports==null?List.<ResourceOwnershipAuthorityPort>of():ports){
            for(ResourceType type:ResourceType.values()) if(port.supportsOwnershipTransfer(type)&&map.put(type,port)!=null)
                throw new IllegalArgumentException("DUPLICATE_RESOURCE_OWNERSHIP_AUTHORITY: "+type);
        }
        authorities=Map.copyOf(map);this.projection=Objects.requireNonNull(projection);this.repository=Objects.requireNonNull(repository);
    }
    public OwnershipTransferImpact preview(OwnershipTransferCommand command){return authority(command.resourceRef().resourceType()).preview(command);}
    public OwnershipTransferResult transfer(OwnershipTransferCommand command){
        DescriptorResolutionContext context=new DescriptorResolutionContext(command.correlationId(),"resource-access-ownership-transfer",command.requestedAt());
        var existing=repository.findOwnershipTransfer(command.resourceRef(),command.idempotencyKey());
        if(existing.isPresent()){
            projection.project(command.resourceRef(),context,existing.get().sourceEventId());
            return existing.get();
        }
        OwnershipTransferResult result=authority(command.resourceRef().resourceType()).transfer(command);
        repository.recordOwnershipTransfer(command,result);
        projection.project(command.resourceRef(),context,result.sourceEventId());return result;
    }
    private ResourceOwnershipAuthorityPort authority(ResourceType type){
        ResourceOwnershipAuthorityPort authority=authorities.get(type);
        if(authority==null)throw new UnsupportedOperationException("RESOURCE_OWNERSHIP_TRANSFER_NOT_SUPPORTED: "+type);
        return authority;
    }
}
