package com.opensocket.aievent.core.kernel.configuration.revision;

import java.util.List;
import java.util.Optional;
import com.opensocket.aievent.core.kernel.configuration.ConfigurationScope;
import com.opensocket.aievent.core.kernel.configuration.OpenDispatchEnvironment;

/**
 * V40-3 persistence port. No distribution/cache semantics belong here.
 * All write methods are expected to execute inside one application transaction.
 */
public interface RuntimeConfigurationRevisionStore {
    RuntimeConfigurationConfigSet createConfigSet(String configSetId, String setKey,
            OpenDispatchEnvironment environment, ConfigurationScope scope, String scopeRef,
            String ownerComponent, String actor);
    Optional<RuntimeConfigurationConfigSet> findConfigSet(String configSetId);
    Optional<RuntimeConfigurationConfigSet> findConfigSetBySetKey(OpenDispatchEnvironment environment, String setKey);
    Optional<RuntimeConfigurationRevision> findRevision(String revisionId);
    List<RuntimeConfigurationRevision> listRevisions(String configSetId, int limit);
    Optional<String> findActiveRevisionId(String configSetId);
    List<RuntimeConfigurationRevisionItem> listItems(String revisionId);
    List<RuntimeConfigurationAuditEntry> history(String configSetId, int limit);

    RuntimeConfigurationRevision createDraft(String revisionId, String configSetId,
            String actor, String reason, String rollbackOfRevisionId, String restoreSourceRevisionId,
            String correlationId);
    RuntimeConfigurationRevisionItem putDraftValue(String revisionId, String definitionKey,
            String canonicalJson, String valueFingerprint, String actor, String reason, String correlationId);
    RuntimeConfigurationRevision transition(String revisionId, RuntimeConfigurationRevisionState expected,
            RuntimeConfigurationRevisionState target, String actor, String reason, String correlationId);
    RuntimeConfigurationRevision publish(String revisionId, String expectedBaseRevisionId,
            String actor, String reason, String correlationId);
    RuntimeConfigurationRevision createRollbackDraft(String newRevisionId, String configSetId,
            String restoreSourceRevisionId, String actor, String reason, String correlationId);
}
