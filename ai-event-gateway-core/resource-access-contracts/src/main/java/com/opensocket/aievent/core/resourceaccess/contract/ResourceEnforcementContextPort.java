package com.opensocket.aievent.core.resourceaccess.contract;

/** Supplies verified server-side identity. Request bodies and query parameters never implement this port. */
public interface ResourceEnforcementContextPort {
    ResourceEnforcementContext current();
}
