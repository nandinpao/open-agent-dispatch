package com.opensocket.aievent.core.configuration.runtime;

import java.util.Set;

/**
 * Domain-owned projection of effective values for migration-authorized Runtime Configuration keys.
 *
 * <p>The control plane uses this SPI for local-process operator/admin projection and migration
 * seeding. Each locally hosted domain owns the mapping from configuration key to its typed runtime
 * view, and a locally resolved key must be owned by exactly one resolver. Settings consumed by a
 * separately deployed Gateway/Worker process are projected from the authenticated distributed
 * Runtime Configuration snapshot instead of introducing cross-process startup-property
 * dependencies into the control plane.</p>
 */
public interface RuntimeConfigurationEffectiveValueResolver {
    /** Stable domain/resolver identifier used in diagnostics and duplicate-ownership failures. */
    String owner();

    /** Configuration keys exclusively owned by this resolver. */
    Set<String> supportedKeys();

    /** Resolve the current effective value through the domain's typed runtime view. */
    Object resolve(String key);
}
