package com.opensocket.aievent.core.iam.rbac.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opensocket.aievent.core.iam.rbac.application.command.catalog.CreatePermissionCatalogDraftCommand;
import com.opensocket.aievent.core.iam.rbac.application.command.catalog.PublishPermissionCatalogCommand;
import com.opensocket.aievent.core.iam.rbac.application.command.catalog.UpsertPermissionDefinitionCommand;
import com.opensocket.aievent.core.iam.rbac.application.port.out.PermissionCatalogAdministrationRepository;
import com.opensocket.aievent.core.iam.rbac.domain.PermissionCode;
import com.opensocket.aievent.core.iam.rbac.domain.ScopeType;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionCatalogAlias;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionCatalogPublication;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionCatalogRevision;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionCatalogRevisionStatus;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionDefinition;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionLifecycle;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionRiskLane;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PermissionCatalogAdministrationServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-30T01:30:00Z");
    private static final UUID ACTIVE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DRAFT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void createsRevisionOwnedDraftAndPublishesSha256Projection() {
        InMemoryRepository repository = new InMemoryRepository();
        PermissionCatalogAdministrationService service = new PermissionCatalogAdministrationService(repository);

        PermissionCatalogRevision draft = service.createDraft(new CreatePermissionCatalogDraftCommand(
                DRAFT_ID, "CATALOG-2", "Second catalog", "root-1", "corr-1", NOW));
        assertEquals(PermissionCatalogRevisionStatus.DRAFT, draft.status());
        assertEquals(1, service.definitions(DRAFT_ID).size());

        service.upsertDefinition(new UpsertPermissionDefinitionCommand(
                DRAFT_ID, new PermissionCode("permission.catalog.test"), "rbac", "PERMISSION_CATALOG",
                "TEST", "Test catalog publication", "HIGH", PermissionRiskLane.ADMIN,
                PermissionLifecycle.ACTIVE, Set.of(ScopeType.INSTANCE), true, Optional.empty(),
                Optional.empty(), Optional.empty(), 0, "root-1", "corr-1", NOW));

        PermissionCatalogPublication publication = service.publish(new PublishPermissionCatalogCommand(
                DRAFT_ID, 1, "Publish validated catalog", "root-1", "corr-1", NOW));

        assertTrue(publication.contentHash().matches("sha256:[0-9a-f]{64}"));
        assertEquals(DRAFT_ID, repository.activeRevisionId);
        assertEquals(PermissionCatalogRevisionStatus.SUPERSEDED,
                repository.revisions.get(ACTIVE_ID).status());
        assertEquals(PermissionCatalogRevisionStatus.PUBLISHED,
                repository.revisions.get(DRAFT_ID).status());
        assertTrue(repository.activeProjection.containsKey("permission.catalog.test"));
        assertEquals(1, repository.publications.size());

        IllegalStateException immutable = assertThrows(IllegalStateException.class, () ->
                service.upsertDefinition(new UpsertPermissionDefinitionCommand(
                        DRAFT_ID, new PermissionCode("permission.catalog.test"), "rbac",
                        "PERMISSION_CATALOG", "TEST", "Changed", "HIGH", PermissionRiskLane.ADMIN,
                        PermissionLifecycle.ACTIVE, Set.of(ScopeType.INSTANCE), true, Optional.empty(),
                        Optional.empty(), Optional.empty(), 1, "root-1", "corr-2", NOW)));
        assertEquals("PERMISSION_CATALOG_REVISION_NOT_DRAFT", immutable.getMessage());
    }

    @Test
    void rejectsSkippingActiveToRetiredLifecycle() {
        InMemoryRepository repository = new InMemoryRepository();
        PermissionCatalogAdministrationService service = new PermissionCatalogAdministrationService(repository);
        service.createDraft(new CreatePermissionCatalogDraftCommand(
                DRAFT_ID, "CATALOG-2", "Second catalog", "root-1", "corr-1", NOW));

        IllegalStateException invalid = assertThrows(IllegalStateException.class, () ->
                service.upsertDefinition(new UpsertPermissionDefinitionCommand(
                        DRAFT_ID, new PermissionCode("task.read"), "task", "TASK", "READ",
                        "Read tasks", "LOW", PermissionRiskLane.READ, PermissionLifecycle.RETIRED,
                        Set.of(ScopeType.TENANT), true, Optional.of(new PermissionCode("task.view")),
                        Optional.empty(), Optional.of(NOW), 1, "root-1", "corr-2", NOW)));

        assertEquals("PERMISSION_CATALOG_LIFECYCLE_TRANSITION_INVALID", invalid.getMessage());
    }

    private static final class InMemoryRepository implements PermissionCatalogAdministrationRepository {
        private final Map<UUID, PermissionCatalogRevision> revisions = new LinkedHashMap<>();
        private final Map<UUID, Map<String, PermissionDefinition>> definitions = new LinkedHashMap<>();
        private final Map<UUID, Map<String, PermissionCatalogAlias>> aliases = new LinkedHashMap<>();
        private final Map<String, PermissionDefinition> activeProjection = new LinkedHashMap<>();
        private final List<PermissionCatalogPublication> publications = new ArrayList<>();
        private UUID activeRevisionId = ACTIVE_ID;
        private long activePointerVersion = 1;

        private InMemoryRepository() {
            PermissionCatalogRevision active = new PermissionCatalogRevision(
                    ACTIVE_ID, "CATALOG-1", 1, PermissionCatalogRevisionStatus.PUBLISHED,
                    "sha256:baseline", "Baseline", Optional.empty(), NOW.minusSeconds(60), "migration",
                    Optional.of(NOW.minusSeconds(30)), Optional.of("migration"), 1);
            revisions.put(ACTIVE_ID, active);
            PermissionDefinition taskRead = definition(ACTIVE_ID, "task.read", 1);
            definitions.put(ACTIVE_ID, new LinkedHashMap<>(Map.of(taskRead.code().value(), taskRead)));
            aliases.put(ACTIVE_ID, new LinkedHashMap<>());
            activeProjection.put(taskRead.code().value(), taskRead);
        }

        @Override public Optional<PermissionCatalogRevision> findActiveRevision() {
            return Optional.ofNullable(revisions.get(activeRevisionId));
        }
        @Override public long activeRevisionPointerVersion() { return activePointerVersion; }
        @Override public Optional<PermissionCatalogRevision> findRevision(UUID revisionId) {
            return Optional.ofNullable(revisions.get(revisionId));
        }
        @Override public List<PermissionCatalogRevision> findRevisions() { return List.copyOf(revisions.values()); }
        @Override public boolean draftExists() {
            return revisions.values().stream().anyMatch(v -> v.status() == PermissionCatalogRevisionStatus.DRAFT);
        }
        @Override public long nextRevisionNumber() {
            return revisions.values().stream().mapToLong(PermissionCatalogRevision::revisionNumber).max().orElse(0) + 1;
        }
        @Override public void insertRevision(PermissionCatalogRevision revision) {
            revisions.put(revision.revisionId(), revision);
            definitions.put(revision.revisionId(), new LinkedHashMap<>());
            aliases.put(revision.revisionId(), new LinkedHashMap<>());
        }
        @Override public int cloneEntries(UUID sourceRevisionId, UUID targetRevisionId, String actorId, Instant at) {
            Map<String, PermissionDefinition> target = definitions.get(targetRevisionId);
            definitions.get(sourceRevisionId).values().forEach(value -> target.put(value.code().value(),
                    copy(value, targetRevisionId, actorId, at)));
            return target.size();
        }
        @Override public int cloneAliases(UUID sourceRevisionId, UUID targetRevisionId, String actorId, Instant at) {
            Map<String, PermissionCatalogAlias> target = aliases.get(targetRevisionId);
            aliases.get(sourceRevisionId).values().forEach(value -> target.put(value.aliasCode().value(),
                    new PermissionCatalogAlias(targetRevisionId, value.aliasCode(), value.canonicalPermissionCode(),
                            value.aliasType(), value.validFrom(), value.validUntil(), value.reason(), at, actorId, 1)));
            return target.size();
        }
        @Override public List<PermissionDefinition> findDefinitions(UUID revisionId) {
            return List.copyOf(definitions.getOrDefault(revisionId, Map.of()).values());
        }
        @Override public Optional<PermissionDefinition> findDefinition(UUID revisionId, PermissionCode code) {
            return Optional.ofNullable(definitions.getOrDefault(revisionId, Map.of()).get(code.value()));
        }
        @Override public void insertDefinition(PermissionDefinition definition) {
            definitions.get(definition.catalogRevisionId()).put(definition.code().value(), definition);
        }
        @Override public boolean updateDefinition(PermissionDefinition definition, long expectedVersion) {
            PermissionDefinition current = definitions.get(definition.catalogRevisionId()).get(definition.code().value());
            if (current == null || current.version() != expectedVersion) return false;
            definitions.get(definition.catalogRevisionId()).put(definition.code().value(), definition);
            return true;
        }
        @Override public boolean deleteDefinition(UUID revisionId, PermissionCode code, long expectedVersion) {
            PermissionDefinition current = definitions.get(revisionId).get(code.value());
            if (current == null || current.version() != expectedVersion) return false;
            definitions.get(revisionId).remove(code.value());
            return true;
        }
        @Override public List<PermissionCatalogAlias> findAliases(UUID revisionId) {
            return List.copyOf(aliases.getOrDefault(revisionId, Map.of()).values());
        }
        @Override public Optional<PermissionCatalogAlias> findAlias(UUID revisionId, PermissionCode aliasCode) {
            return Optional.ofNullable(aliases.getOrDefault(revisionId, Map.of()).get(aliasCode.value()));
        }
        @Override public void insertAlias(PermissionCatalogAlias alias) {
            aliases.get(alias.revisionId()).put(alias.aliasCode().value(), alias);
        }
        @Override public boolean updateAlias(PermissionCatalogAlias alias, long expectedVersion) {
            PermissionCatalogAlias current = aliases.get(alias.revisionId()).get(alias.aliasCode().value());
            if (current == null || current.version() != expectedVersion) return false;
            aliases.get(alias.revisionId()).put(alias.aliasCode().value(), alias);
            return true;
        }
        @Override public boolean deleteAlias(UUID revisionId, PermissionCode aliasCode, long expectedVersion) {
            PermissionCatalogAlias current = aliases.get(revisionId).get(aliasCode.value());
            if (current == null || current.version() != expectedVersion) return false;
            aliases.get(revisionId).remove(aliasCode.value());
            return true;
        }
        @Override public Set<String> findRoleReferencedPermissionCodes() { return Set.of("task.read"); }
        @Override public void appendChangeEvent(UUID eventId, UUID revisionId, String permissionCode,
                String eventType, String changeSummary, String beforeHash, String afterHash,
                String actorId, String correlationId, Instant occurredAt) { }
        @Override public boolean publishRevision(UUID revisionId, String contentHash, String actorId,
                Instant at, long expectedVersion) {
            PermissionCatalogRevision current = revisions.get(revisionId);
            if (current == null || current.status() != PermissionCatalogRevisionStatus.DRAFT
                    || current.version() != expectedVersion) return false;
            revisions.put(revisionId, new PermissionCatalogRevision(current.revisionId(), current.revisionCode(),
                    current.revisionNumber(), PermissionCatalogRevisionStatus.PUBLISHED, contentHash,
                    current.description(), current.supersedesRevisionId(), current.createdAt(), current.createdBy(),
                    Optional.of(at), Optional.of(actorId), current.version() + 1));
            return true;
        }
        @Override public boolean supersedeRevision(UUID revisionId, String actorId, Instant at,
                long expectedVersion) {
            PermissionCatalogRevision current = revisions.get(revisionId);
            if (current == null || current.status() != PermissionCatalogRevisionStatus.PUBLISHED
                    || current.version() != expectedVersion) return false;
            revisions.put(revisionId, new PermissionCatalogRevision(current.revisionId(), current.revisionCode(),
                    current.revisionNumber(), PermissionCatalogRevisionStatus.SUPERSEDED, current.contentHash(),
                    current.description(), current.supersedesRevisionId(), current.createdAt(), current.createdBy(),
                    current.publishedAt(), current.publishedBy(), current.version() + 1));
            return true;
        }
        @Override public void beginPublication(UUID revisionId) { }
        @Override public void materializeActiveDefinition(PermissionDefinition definition) {
            activeProjection.put(definition.code().value(), definition);
        }
        @Override public boolean activateRevision(UUID revisionId, String actorId, Instant at,
                long expectedVersion) {
            if (activePointerVersion != expectedVersion) return false;
            activeRevisionId = revisionId;
            activePointerVersion++;
            return true;
        }
        @Override public void appendPublication(PermissionCatalogPublication publication) {
            publications.add(publication);
        }
        @Override public List<PermissionCatalogPublication> findPublications(int limit) {
            return publications.stream().limit(limit).toList();
        }

        private static PermissionDefinition definition(UUID revisionId, String code, long version) {
            return new PermissionDefinition(new PermissionCode(code), "task", "TASK", "READ", "Read tasks",
                    "LOW", PermissionRiskLane.READ, PermissionLifecycle.ACTIVE, Set.of(ScopeType.TENANT),
                    true, revisionId, Optional.empty(), NOW.minusSeconds(300), Optional.empty(),
                    Optional.empty(), NOW, "migration", version);
        }
        private static PermissionDefinition copy(PermissionDefinition value, UUID revisionId,
                String actorId, Instant at) {
            return new PermissionDefinition(value.code(), value.ownerModule(), value.resourceType(),
                    value.actionCode(), value.description(), value.riskLevel(), value.riskLane(),
                    value.lifecycle(), value.allowedScopes(), value.systemManaged(), revisionId,
                    value.replacementPermissionCode(), value.introducedAt(), value.deprecatedAt(),
                    value.retiredAt(), at, actorId, 1);
        }
    }
}
