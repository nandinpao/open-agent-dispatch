package com.opensocket.aievent.core.resourceaccess.contract;

/** Canonical Domain mutation authority. Projection repositories must never implement this port. */
public interface ResourceOwnershipAuthorityPort {
    boolean supportsOwnershipTransfer(ResourceType resourceType);
    OwnershipTransferImpact preview(OwnershipTransferCommand command);
    OwnershipTransferResult transfer(OwnershipTransferCommand command);
}
