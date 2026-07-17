package com.opensocket.aievent.core.model;

/**
 * Marker for the canonical Core data-model module.
 *
 * <p>This module owns stable data shapes only: API DTOs, domain records/enums,
 * repository ports, MyBatis DAO interfaces, and persistence PO classes. It must not
 * contain Spring services, controllers, schedulers, transaction orchestration, or
 * runtime side effects.</p>
 */
public final class CoreDataModelModule {
    private CoreDataModelModule() {
    }
}
