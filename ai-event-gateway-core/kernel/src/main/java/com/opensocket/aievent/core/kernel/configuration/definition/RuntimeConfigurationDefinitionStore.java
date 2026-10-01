package com.opensocket.aievent.core.kernel.configuration.definition;

import java.util.List;
import java.util.Optional;

/** Read-only runtime materialization of source-controlled configuration definitions. */
public interface RuntimeConfigurationDefinitionStore {
    /** All source-controlled runtime-target definitions, including PROPOSED entries not yet editable. */
    default List<RuntimeConfigurationDefinition> listAll() { return listMigrationAuthorized(); }

    List<RuntimeConfigurationDefinition> listMigrationAuthorized();
    Optional<RuntimeConfigurationDefinition> findByKey(String key);
}
