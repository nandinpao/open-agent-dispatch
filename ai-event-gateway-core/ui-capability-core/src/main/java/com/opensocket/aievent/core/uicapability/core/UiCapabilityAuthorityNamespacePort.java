package com.opensocket.aievent.core.uicapability.core;

import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceRef;

/** Minimum version namespace required for exact projection cache isolation. */
public interface UiCapabilityAuthorityNamespacePort {
    UiCapabilityAuthorityNamespace current(String tenantId, PrincipalRef principal, ResourceRef resourceRef);
}
