package com.opensocket.aievent.core.integration.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;

import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Application authority for versioned Project Mapping and Provider Metadata governance. */
public class ProjectMappingGovernanceService {
    private static final Pattern TEMPLATE_TOKEN = Pattern.compile("\\{\\{\\s*([^}]+?)\\s*}}");
    private static final Set<String> BUILT_IN_FIELDS = Set.of("summary", "description", "subject", "priority_id");

    private final IntegrationIdentityRepository repository;
    private final ProviderMetadataProbeGateway gateway;
    private final ObjectMapper json;

    public ProjectMappingGovernanceService(
            IntegrationIdentityRepository repository,
            ProviderMetadataProbeGateway gateway,
            ObjectMapper json) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.json = Objects.requireNonNull(json, "json");
    }

    public List<ProviderMetadataSnapshot> snapshots(String tenantId, String mappingId, int limit) {
        return repository.listMetadataSnapshots(required(tenantId), mappingId, bounded(limit, 500));
    }

    public ProviderMetadataSnapshot snapshot(String tenantId, String snapshotId) {
        return repository.findMetadataSnapshot(required(tenantId), required(snapshotId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Provider Metadata Snapshot not found in Tenant."));
    }

    public List<IntegrationProjectMappingVersion> versions(
            String tenantId, String mappingId, int limit) {
        return repository.listMappingVersions(
                required(tenantId), required(mappingId), bounded(limit, 200));
    }

    /**
     * Beginner/admin metadata discovery for Issue Tracking setup. This probes the provider
     * with one technical Service Account without requiring a persisted Project Mapping first.
     * Discovery never becomes routing authority and does not persist a synthetic Mapping.
     */
    public ProviderMetadataSnapshot discover(
            String tenantId,
            String connectionId,
            String principalId,
            String correlationId) {
        String tenant = required(tenantId);
        String connection = required(connectionId);
        IntegrationConnection resolvedConnection = repository.findConnection(tenant, connection)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Integration Connection not found in Tenant."));
        OffsetDateTime now = OffsetDateTime.now();
        String selectedPrincipalId = principalId;
        if (blank(selectedPrincipalId)) {
            selectedPrincipalId = repository.listPrincipals(tenant, connection, 200).stream()
                    .filter(this::connectorPrincipalEligible)
                    .map(IntegrationPrincipal::principalId)
                    .filter(id -> repository.listCredentials(tenant, id, 100).stream().anyMatch(value ->
                            (value.status() == IntegrationCredentialStatus.PENDING_VALIDATION
                                    || value.status() == IntegrationCredentialStatus.ACTIVE
                                    || value.status() == IntegrationCredentialStatus.GRACE_PERIOD)
                                    && (value.expiresAt() == null || value.expiresAt().isAfter(now))))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("INTEGRATION_TECHNICAL_SERVICE_ACCOUNT_REQUIRED"));
        }
        IntegrationPrincipal principal = repository.findPrincipal(tenant, selectedPrincipalId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Integration technical Service Account not found in Tenant."));
        if (!Objects.equals(connection, principal.connectionId())) {
            throw new IllegalArgumentException("Integration technical Service Account must use the selected Connection.");
        }
        IntegrationCredentialMetadata credential = repository.listCredentials(tenant, selectedPrincipalId, 100).stream()
                .filter(value -> value.status() == IntegrationCredentialStatus.PENDING_VALIDATION
                        || value.status() == IntegrationCredentialStatus.ACTIVE
                        || value.status() == IntegrationCredentialStatus.GRACE_PERIOD)
                .filter(value -> value.expiresAt() == null || value.expiresAt().isAfter(now))
                .sorted(Comparator
                        .comparingInt((IntegrationCredentialMetadata value) -> switch (value.status()) {
                            case PENDING_VALIDATION -> 0;
                            case ACTIVE -> 1;
                            case GRACE_PERIOD -> 2;
                            default -> 3;
                        })
                        .thenComparing(IntegrationCredentialMetadata::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Comparator.comparingLong(IntegrationCredentialMetadata::version).reversed()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("INTEGRATION_CREDENTIAL_ACTIVE_REQUIRED"));

        String discoveryId = "discovery-" + java.util.UUID.randomUUID();
        IntegrationProjectMapping transientMapping = new IntegrationProjectMapping(
                tenant, discoveryId, connection, null, null, null, null, null,
                "__DISCOVERY__", null, null, null, selectedPrincipalId, null, null, null, null, null,
                null, null, null, ProjectMappingStatus.DRAFT, 1000, false, false, 0L, now, now);
        return gateway.probe(
                resolvedConnection,
                principal,
                credential,
                transientMapping,
                new ProviderMetadataProbeRequest(tenant, discoveryId, selectedPrincipalId, true, correlationId));
    }

    @Transactional
    public ProviderMetadataSnapshot probe(
            String tenantId,
            String mappingId,
            String principalId,
            boolean force,
            String correlationId) {
        IntegrationProjectMapping mapping = mapping(tenantId, mappingId);
        OffsetDateTime now = OffsetDateTime.now();
        if (!force) {
            var cached = repository.latestMetadataSnapshot(tenantId, mappingId);
            if (cached.isPresent()
                    && !cached.get().expired(now)
                    && cached.get().cacheStatus() == ProviderMetadataCacheStatus.FRESH) {
                return cached.get();
            }
        }

        String selectedPrincipalId = first(principalId, mapping.readPrincipalId(), mapping.createPrincipalId());
        if (blank(selectedPrincipalId)) {
            selectedPrincipalId = repository.listPrincipals(tenantId, mapping.connectionId(), 200).stream()
                    .filter(this::connectorPrincipalEligible)
                    .map(IntegrationPrincipal::principalId)
                    .filter(id -> repository.listCredentials(tenantId, id, 100).stream().anyMatch(value ->
                            (value.status() == IntegrationCredentialStatus.ACTIVE
                                    || value.status() == IntegrationCredentialStatus.GRACE_PERIOD)
                                    && (value.expiresAt() == null || value.expiresAt().isAfter(now))))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("INTEGRATION_TECHNICAL_SERVICE_ACCOUNT_REQUIRED"));
        }
        IntegrationPrincipal principal = repository.findPrincipal(tenantId, selectedPrincipalId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Integration technical Service Account not found in Tenant."));
        IntegrationConnection connection = repository.findConnection(tenantId, mapping.connectionId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Integration Connection not found in Tenant."));
        IntegrationCredentialMetadata credential = repository
                .listCredentials(tenantId, selectedPrincipalId, 100).stream()
                .filter(value -> value.status() == IntegrationCredentialStatus.ACTIVE || value.status() == IntegrationCredentialStatus.GRACE_PERIOD)
                .filter(value -> value.validFrom() == null || !value.validFrom().isAfter(now))
                .filter(value -> value.expiresAt() == null || value.expiresAt().isAfter(now))
                // Rotation leaves the previous credential in GRACE_PERIOD. Credential
                // version numbers are per credential row, so max(version) can accidentally
                // choose the old grace credential. Prefer ACTIVE first, then newest creation.
                .sorted(Comparator
                        .comparingInt((IntegrationCredentialMetadata value) -> value.status() == IntegrationCredentialStatus.ACTIVE ? 0 : 1)
                        .thenComparing(IntegrationCredentialMetadata::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "INTEGRATION_CREDENTIAL_ACTIVE_REQUIRED"));

        ProviderMetadataSnapshot raw = gateway.probe(
                connection,
                principal,
                credential,
                mapping,
                new ProviderMetadataProbeRequest(
                        tenantId, mappingId, selectedPrincipalId, force, correlationId));
        var previous = repository.latestMetadataSnapshot(tenantId, mappingId);
        int metadataVersion = previous.map(value -> value.metadataVersion() + 1).orElse(1);
        ProviderMetadataCacheStatus status = previous
                .filter(value -> !Objects.equals(value.schemaHash(), raw.schemaHash()))
                .map(value -> ProviderMetadataCacheStatus.DRIFTED)
                .orElse(raw.cacheStatus());

        ProviderMetadataSnapshot saved = new ProviderMetadataSnapshot(
                tenantId,
                raw.snapshotId(),
                connection.connectionId(),
                mappingId,
                raw.providerProjectId(),
                raw.providerProjectKey(),
                raw.projects(),
                raw.issueTypes(),
                raw.fields(),
                raw.transitions(),
                raw.linkTypes(),
                raw.users(),
                raw.groups(),
                raw.permissions(),
                raw.etag(),
                raw.lastModified(),
                raw.schemaHash(),
                metadataVersion,
                status,
                raw.probedAt(),
                raw.expiresAt(),
                raw.providerSummary(),
                correlationId);
        return repository.saveMetadataSnapshot(saved);
    }

    @Transactional
    public ProjectMappingValidationResult validate(
            String tenantId, String mappingId, String actorId) {
        IntegrationProjectMapping mapping = mapping(tenantId, mappingId);
        if (!mapping.mutable()) {
            throw new IllegalStateException("PUBLISHED_MAPPING_IMMUTABLE_CREATE_NEW_VERSION");
        }

        OffsetDateTime now = OffsetDateTime.now();
        repository.saveMapping(copy(
                mapping,
                ProjectMappingLifecycle.VALIDATING,
                false,
                mapping.metadataSnapshotId(),
                mapping.metadataSchemaHash(),
                mapping.validatedAt(),
                mapping.publishedAt(),
                mapping.version() + 1,
                now));

        ProviderMetadataSnapshot metadata = repository
                .latestMetadataSnapshot(tenantId, mappingId).orElse(null);
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (metadata == null) {
            errors.add("PROVIDER_METADATA_REQUIRED");
        } else {
            validateMetadata(mapping, metadata, now, errors, warnings);
        }

        boolean valid = errors.isEmpty();
        IntegrationProjectMapping saved = copy(
                mapping,
                valid ? ProjectMappingLifecycle.VALID : ProjectMappingLifecycle.DRAFT,
                false,
                metadata == null ? null : metadata.snapshotId(),
                metadata == null ? null : metadata.schemaHash(),
                valid ? now : null,
                mapping.publishedAt(),
                mapping.version() + 2,
                now);
        repository.saveMapping(saved);
        return new ProjectMappingValidationResult(
                mappingId,
                mapping.mappingVersion(),
                valid,
                errors,
                warnings,
                saved.metadataSnapshotId(),
                saved.metadataSchemaHash());
    }

    @Transactional
    public IntegrationProjectMapping publish(
            String tenantId, String mappingId, String actorId) {
        IntegrationProjectMapping mapping = mapping(tenantId, mappingId);
        if (mapping.lifecycleStatus() != ProjectMappingLifecycle.VALID) {
            throw new IllegalStateException("PROJECT_MAPPING_MUST_BE_VALID_BEFORE_PUBLISH");
        }
        if (blank(mapping.metadataSnapshotId()) || blank(mapping.metadataSchemaHash())) {
            throw new IllegalStateException("PROVIDER_METADATA_BINDING_REQUIRED");
        }
        ProviderMetadataSnapshot metadata = snapshot(tenantId, mapping.metadataSnapshotId());
        if (!Objects.equals(mapping.metadataSchemaHash(), metadata.schemaHash())) {
            throw new IllegalStateException("PROVIDER_METADATA_SCHEMA_BINDING_MISMATCH");
        }
        if (metadata.expired(OffsetDateTime.now())) {
            throw new IllegalStateException("PROVIDER_METADATA_EXPIRED");
        }

        OffsetDateTime now = OffsetDateTime.now();
        IntegrationProjectMapping active = copy(
                mapping,
                ProjectMappingLifecycle.ACTIVE,
                true,
                mapping.metadataSnapshotId(),
                mapping.metadataSchemaHash(),
                mapping.validatedAt(),
                now,
                mapping.version() + 1,
                now);
        repository.saveMappingVersion(version(active, actorId, now));
        return repository.saveMapping(active);
    }

    /**
     * Product-level Issue Tracking has one authority per Source System. After the new
     * source-default mapping has been validated, retire every older ACTIVE mapping
     * for the same Source System (including a stale source-default mapping) and publish
     * the new default in one database transaction. This guarantees one runtime authority
     * per Source System and lets Quick Setup repair legacy ACTIVE-but-not-ready mappings.
     */
    @Transactional
    public IntegrationProjectMapping publishSourceDefaultAndRetireOverrides(
            String tenantId, String mappingId, String sourceSystemId, String actorId) {
        IntegrationProjectMapping candidate = mapping(tenantId, mappingId);
        if (!Objects.equals(sourceSystemId, candidate.sourceSystemId())
                || (candidate.taskType() != null && !candidate.taskType().isBlank())) {
            throw new IllegalStateException("SOURCE_DEFAULT_MAPPING_REQUIRED");
        }
        List<IntegrationProjectMapping> activeSourceMappings = repository.listMappings(tenantId, null, 1000).stream()
                .filter(value -> !Objects.equals(value.mappingId(), mappingId))
                .filter(value -> value.enabled() && value.lifecycleStatus() == ProjectMappingLifecycle.ACTIVE)
                .filter(value -> Objects.equals(sourceSystemId, value.sourceSystemId()))
                .toList();
        for (IntegrationProjectMapping existing : activeSourceMappings) {
            deprecate(tenantId, existing.mappingId(), actorId);
        }
        return publish(tenantId, mappingId, actorId);
    }

    @Transactional
    public IntegrationProjectMapping deprecate(
            String tenantId, String mappingId, String actorId) {
        IntegrationProjectMapping mapping = mapping(tenantId, mappingId);
        if (mapping.lifecycleStatus() != ProjectMappingLifecycle.ACTIVE) {
            throw new IllegalStateException("ONLY_ACTIVE_MAPPING_CAN_BE_DEPRECATED");
        }
        OffsetDateTime now = OffsetDateTime.now();
        return repository.saveMapping(copy(
                mapping,
                ProjectMappingLifecycle.DEPRECATED,
                false,
                mapping.metadataSnapshotId(),
                mapping.metadataSchemaHash(),
                mapping.validatedAt(),
                mapping.publishedAt(),
                mapping.version() + 1,
                now));
    }

    @Transactional
    public IntegrationProjectMapping fork(
            String tenantId, String sourceMappingId, String newMappingId, String actorId) {
        IntegrationProjectMapping source = mapping(tenantId, sourceMappingId);
        if (source.lifecycleStatus() != ProjectMappingLifecycle.ACTIVE
                && source.lifecycleStatus() != ProjectMappingLifecycle.DEPRECATED) {
            throw new IllegalStateException("ONLY_PUBLISHED_MAPPING_CAN_CREATE_NEW_VERSION");
        }
        ensureMappingIdAvailable(tenantId, newMappingId);
        OffsetDateTime now = OffsetDateTime.now();
        return repository.saveMapping(newDraft(
                source,
                newMappingId,
                source.mappingVersion() + 1,
                source.mappingVersion(),
                source.externalProjectId(),
                source.externalIssueType(),
                source.summaryTemplate(),
                source.descriptionTemplate(),
                source.requiredFields(),
                source.customFieldMappings(),
                source.transitionMappings(),
                source.commentPolicy(),
                source.linkPolicy(),
                now));
    }

    @Transactional
    public IntegrationProjectMapping rollback(
            String tenantId,
            String sourceMappingId,
            int targetVersion,
            String newMappingId,
            String actorId) {
        IntegrationProjectMapping source = mapping(tenantId, sourceMappingId);
        IntegrationProjectMappingVersion historical = repository
                .findMappingVersion(tenantId, sourceMappingId, targetVersion)
                .orElseThrow(() -> new IllegalArgumentException("Mapping version not found."));
        ensureMappingIdAvailable(tenantId, newMappingId);
        try {
            Map<?, ?> config = json.readValue(historical.configurationJson(), Map.class);
            OffsetDateTime now = OffsetDateTime.now();
            return repository.saveMapping(newDraft(
                    source,
                    newMappingId,
                    source.mappingVersion() + 1,
                    targetVersion,
                    text(config.get("project"), source.externalProjectId()),
                    text(config.get("issueType"), source.externalIssueType()),
                    text(config.get("summaryTemplate"), source.summaryTemplate()),
                    text(config.get("descriptionTemplate"), source.descriptionTemplate()),
                    strings(config.get("requiredFields")),
                    stringMap(config.get("customFields")),
                    stringMap(config.get("transitions")),
                    text(config.get("commentPolicy"), source.commentPolicy()),
                    text(config.get("linkPolicy"), source.linkPolicy()),
                    now));
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "MAPPING_ROLLBACK_CONFIGURATION_INVALID", exception);
        }
    }

    public ProjectMappingPreview preview(
            String tenantId, String mappingId, ProjectMappingPreviewRequest request) {
        IntegrationProjectMapping mapping = mapping(tenantId, mappingId);
        Map<String, Object> context = request == null ? Map.of() : request.context();
        String summary = render(mapping.summaryTemplate(), context);
        String description = render(mapping.descriptionTemplate(), context);
        Map<String, Object> mappedFields = new LinkedHashMap<>();
        mapping.customFieldMappings().forEach(
                (target, source) -> mappedFields.put(target, value(context, source)));
        List<String> missing = mapping.requiredFields().stream()
                .filter(field -> !mappedFields.containsKey(field) && value(context, field) == null)
                .toList();
        return new ProjectMappingPreview(
                mappingId,
                mapping.mappingVersion(),
                first(mapping.externalProjectKey(), mapping.externalProjectId()),
                mapping.externalIssueType(),
                summary,
                description,
                mappedFields,
                missing,
                mapping.metadataSnapshotId(),
                mapping.metadataSchemaHash());
    }

    public ProjectMappingDiff diff(
            String tenantId, String mappingId, int fromVersion, int toVersion) {
        IntegrationProjectMappingVersion from = repository
                .findMappingVersion(tenantId, mappingId, fromVersion)
                .orElseThrow(() -> new IllegalArgumentException("Mapping version not found."));
        IntegrationProjectMappingVersion to = repository
                .findMappingVersion(tenantId, mappingId, toVersion)
                .orElseThrow(() -> new IllegalArgumentException("Mapping version not found."));
        Map<String, String> changes = new LinkedHashMap<>();
        compareConfiguration(from.configurationJson(), to.configurationJson(), changes);
        compare("metadataSchemaHash", from.metadataSchemaHash(), to.metadataSchemaHash(), changes);
        compare("configurationHash", from.configurationHash(), to.configurationHash(), changes);
        return new ProjectMappingDiff(mappingId, fromVersion, toVersion, changes);
    }

    private void validateMetadata(
            IntegrationProjectMapping mapping,
            ProviderMetadataSnapshot metadata,
            OffsetDateTime now,
            List<String> errors,
            List<String> warnings) {
        if (metadata.expired(now)) errors.add("PROVIDER_METADATA_EXPIRED");
        if (metadata.cacheStatus() == ProviderMetadataCacheStatus.FAILED) {
            // A failed provider probe contains no trustworthy project/tracker metadata.
            // Do not cascade misleading NOT_ACCESSIBLE / NOT_FOUND errors from an empty
            // failed snapshot; surface the provider probe failure as the single root cause.
            errors.add("PROVIDER_METADATA_PROBE_FAILED");
            return;
        }
        if (metadata.cacheStatus() == ProviderMetadataCacheStatus.DRIFTED) {
            warnings.add("PROVIDER_SCHEMA_DRIFT_DETECTED");
        }

        boolean projectAccessible = metadata.projects().stream().anyMatch(value ->
                (Objects.equals(value.projectId(), mapping.externalProjectId())
                        || Objects.equals(value.projectKey(), mapping.externalProjectKey()))
                        && value.accessible());
        if (!projectAccessible) errors.add("PROVIDER_PROJECT_NOT_ACCESSIBLE");

        ProviderIssueTypeMetadata issueType = metadata.issueTypes().stream()
                .filter(value -> Objects.equals(value.issueTypeId(), mapping.externalIssueType())
                        || Objects.equals(value.issueTypeKey(), mapping.externalIssueType())
                        || Objects.equals(value.issueTypeId(), mapping.externalTrackerId())
                        || Objects.equals(value.issueTypeKey(), mapping.externalTrackerId()))
                .findFirst().orElse(null);
        if (issueType == null) errors.add("PROVIDER_ISSUE_TYPE_NOT_FOUND");

        Set<String> availableFields = metadata.fields().stream()
                .flatMap(value -> List.of(value.fieldId(), value.fieldKey()).stream())
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (String field : mapping.requiredFields()) {
            if (!availableFields.contains(field) && !BUILT_IN_FIELDS.contains(field)) {
                errors.add("REQUIRED_FIELD_NOT_FOUND:" + field);
            }
        }
        for (String target : mapping.customFieldMappings().keySet()) {
            if (!availableFields.contains(target) && !BUILT_IN_FIELDS.contains(target)) {
                errors.add("CUSTOM_FIELD_NOT_FOUND:" + target);
            }
        }
        if (issueType != null) {
            Set<String> configured = new LinkedHashSet<>(mapping.requiredFields());
            configured.addAll(mapping.customFieldMappings().keySet());
            configured.addAll(BUILT_IN_FIELDS);
            for (String field : issueType.requiredFieldIds()) {
                if (!configured.contains(field)) {
                    errors.add("PROVIDER_REQUIRED_FIELD_UNMAPPED:" + field);
                }
            }
        }

        Set<String> transitionIds = metadata.transitions().stream()
                .flatMap(value -> List.of(value.transitionId(), value.transitionKey()).stream())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (String transition : mapping.transitionMappings().values()) {
            if (!transitionIds.isEmpty() && !transitionIds.contains(transition)) {
                errors.add("PROVIDER_TRANSITION_NOT_FOUND:" + transition);
            }
        }
        if (!"CANONICAL_ONLY".equals(mapping.linkPolicy()) && metadata.linkTypes().isEmpty()) {
            errors.add("PROVIDER_LINK_TYPE_REQUIRED");
        }
        if (metadata.permissions().values().stream().anyMatch(value ->
                value == PermissionProbeResultStatus.DENIED
                        || value == PermissionProbeResultStatus.ERROR)) {
            warnings.add("PROVIDER_METADATA_PERMISSION_ADVISORY");
        }
    }

    private void validatePermissionProbe(
            String tenantId,
            IntegrationProjectMapping mapping,
            List<String> warnings) {
        String principalId = first(mapping.createPrincipalId(), mapping.readPrincipalId());
        if (blank(principalId)) {
            warnings.add("PROJECT_MAPPING_EXECUTION_IDENTITY_NOT_CONFIGURED");
            return;
        }
        PermissionProbeResult probe = repository.latestProbe(tenantId, principalId, mapping.mappingId())
                .orElse(null);
        if (probe == null) {
            warnings.add("PROVIDER_PERMISSION_PROBE_NOT_AVAILABLE");
            return;
        }
        if (probe.overallStatus() != ProjectMappingStatus.VALID) {
            warnings.add("PROVIDER_PERMISSION_PROFILE_ADVISORY");
        }
        requireCapability(probe, IntegrationPermissionCapability.PROJECT_VISIBLE, warnings);
        if (!blank(mapping.createPrincipalId())) {
            requireCapability(probe, IntegrationPermissionCapability.CREATE_ISSUE, warnings);
        }
        if (!blank(mapping.commentPrincipalId())) {
            requireCapability(probe, IntegrationPermissionCapability.ADD_COMMENT, warnings);
        }
        if (!"CANONICAL_ONLY".equals(mapping.linkPolicy())
                && !blank(mapping.relationPrincipalId())) {
            requireCapability(probe, IntegrationPermissionCapability.CREATE_RELATION, warnings);
        }
    }

    private void requireCapability(
            PermissionProbeResult probe,
            IntegrationPermissionCapability capability,
            List<String> warnings) {
        if (probe.capabilityResults().get(capability) != PermissionProbeResultStatus.GRANTED) {
            warnings.add("PROVIDER_PERMISSION_UNVERIFIED_OR_DENIED:" + capability.name());
        }
    }

    private IntegrationProjectMapping mapping(String tenantId, String mappingId) {
        return repository.findMapping(required(tenantId), required(mappingId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Project Mapping not found in Tenant."));
    }

    private IntegrationProjectMappingVersion version(
            IntegrationProjectMapping mapping, String actorId, OffsetDateTime now) {
        String configuration = configuration(mapping);
        return new IntegrationProjectMappingVersion(
                mapping.tenantId(),
                mapping.mappingId(),
                mapping.mappingVersion(),
                mapping.lifecycleStatus(),
                configuration,
                hash(configuration),
                mapping.metadataSnapshotId(),
                mapping.metadataSchemaHash(),
                required(actorId),
                now);
    }

    private String configuration(IntegrationProjectMapping mapping) {
        try {
            return json.writeValueAsString(Map.ofEntries(
                    Map.entry("project", safe(mapping.externalProjectId())),
                    Map.entry("issueType", safe(mapping.externalIssueType())),
                    Map.entry("summaryTemplate", mapping.summaryTemplate()),
                    Map.entry("descriptionTemplate", mapping.descriptionTemplate()),
                    Map.entry("requiredFields", mapping.requiredFields()),
                    Map.entry("customFields", mapping.customFieldMappings()),
                    Map.entry("transitions", mapping.transitionMappings()),
                    Map.entry("commentPolicy", mapping.commentPolicy()),
                    Map.entry("linkPolicy", mapping.linkPolicy())));
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "MAPPING_CONFIGURATION_SERIALIZATION_FAILED", exception);
        }
    }

    public static String schemaHash(
            IntegrationProjectMapping mapping,
            List<ProviderFieldMetadata> fields) {
        return schemaHash(mapping, List.of(), fields, List.of(), List.of());
    }

    public static String schemaHash(
            IntegrationProjectMapping mapping,
            List<ProviderIssueTypeMetadata> issueTypes,
            List<ProviderFieldMetadata> fields,
            List<ProviderTransitionMetadata> transitions,
            List<ProviderLinkTypeMetadata> linkTypes) {
        List<String> schema = new ArrayList<>();
        schema.add("project:" + safe(mapping.externalProjectId()));
        schema.add("issueType:" + safe(mapping.externalIssueType()));
        issueTypes.stream()
                .map(value -> "type:" + safe(value.issueTypeId()) + ":"
                        + new TreeSet<>(value.requiredFieldIds()))
                .sorted().forEach(schema::add);
        fields.stream()
                .map(value -> "field:" + safe(value.fieldId()) + ":" + safe(value.fieldType())
                        + ":" + value.required() + ":" + new TreeSet<>(value.allowedValues()))
                .sorted().forEach(schema::add);
        transitions.stream()
                .map(value -> "transition:" + safe(value.transitionId()) + ":"
                        + safe(value.fromStatus()) + ":" + safe(value.toStatus()))
                .sorted().forEach(schema::add);
        linkTypes.stream()
                .map(value -> "link:" + safe(value.linkTypeId()) + ":"
                        + safe(value.linkTypeKey()))
                .sorted().forEach(schema::add);
        return hash(schema);
    }

    private IntegrationProjectMapping copy(
            IntegrationProjectMapping mapping,
            ProjectMappingLifecycle lifecycle,
            boolean enabled,
            String snapshotId,
            String schemaHash,
            OffsetDateTime validatedAt,
            OffsetDateTime publishedAt,
            long rowVersion,
            OffsetDateTime now) {
        ProjectMappingStatus runtimeStatus = ProjectMappingRuntimeReadiness.statusForLifecycle(
                mapping.mappingStatus(), lifecycle);
        return new IntegrationProjectMapping(
                mapping.tenantId(), mapping.mappingId(), mapping.connectionId(),
                mapping.departmentId(), mapping.groupId(), mapping.serviceDomainId(),
                mapping.sourceSystemId(), mapping.taskType(), mapping.externalProjectId(),
                mapping.externalProjectKey(), mapping.externalIssueType(),
                mapping.externalTrackerId(), mapping.readPrincipalId(),
                mapping.createPrincipalId(), mapping.commentPrincipalId(),
                mapping.updatePrincipalId(), mapping.relationPrincipalId(),
                mapping.webhookPrincipalId(), mapping.permissionProfileId(),
                mapping.contextPolicyId(), mapping.resultSharingPolicyId(), mapping.providerWriteIdentityPolicy(),
                runtimeStatus, mapping.resolutionPriority(), mapping.defaultMapping(),
                enabled, lifecycle, mapping.mappingVersion(), mapping.summaryTemplate(),
                mapping.descriptionTemplate(), mapping.requiredFields(),
                mapping.customFieldMappings(), mapping.transitionMappings(),
                mapping.commentPolicy(), mapping.linkPolicy(), snapshotId, schemaHash,
                validatedAt, publishedAt, mapping.supersedesMappingVersion(), rowVersion,
                mapping.createdAt(), now);
    }

    private IntegrationProjectMapping newDraft(
            IntegrationProjectMapping source,
            String newMappingId,
            int mappingVersion,
            Integer supersedesVersion,
            String projectId,
            String issueType,
            String summaryTemplate,
            String descriptionTemplate,
            List<String> requiredFields,
            Map<String, String> customFields,
            Map<String, String> transitions,
            String commentPolicy,
            String linkPolicy,
            OffsetDateTime now) {
        return new IntegrationProjectMapping(
                source.tenantId(), newMappingId, source.connectionId(), source.departmentId(),
                source.groupId(), source.serviceDomainId(), source.sourceSystemId(),
                source.taskType(), projectId, source.externalProjectKey(), issueType,
                source.externalTrackerId(), source.readPrincipalId(), source.createPrincipalId(),
                source.commentPrincipalId(), source.updatePrincipalId(),
                source.relationPrincipalId(), source.webhookPrincipalId(),
                source.permissionProfileId(), source.contextPolicyId(),
                source.resultSharingPolicyId(), source.providerWriteIdentityPolicy(), ProjectMappingStatus.DRAFT,
                source.resolutionPriority(), false, false, ProjectMappingLifecycle.DRAFT,
                mappingVersion, summaryTemplate, descriptionTemplate, requiredFields,
                customFields, transitions, commentPolicy, linkPolicy, null, null,
                null, null, supersedesVersion, 1, now, now);
    }

    private void ensureMappingIdAvailable(String tenantId, String mappingId) {
        if (repository.findMapping(tenantId, required(mappingId)).isPresent()) {
            throw new IllegalStateException("PROJECT_MAPPING_ID_ALREADY_EXISTS");
        }
    }

    private void compareConfiguration(
            String fromJson, String toJson, Map<String, String> changes) {
        try {
            Map<?, ?> from = json.readValue(fromJson, Map.class);
            Map<?, ?> to = json.readValue(toJson, Map.class);
            Set<String> keys = new TreeSet<>();
            if (from != null) from.keySet().forEach(key -> keys.add(String.valueOf(key)));
            if (to != null) to.keySet().forEach(key -> keys.add(String.valueOf(key)));
            for (String key : keys) {
                compare(key, from == null ? null : from.get(key), to == null ? null : to.get(key), changes);
            }
        } catch (Exception exception) {
            changes.put("configuration", "Unable to parse immutable configuration JSON.");
        }
    }

    private void compare(
            String key, Object from, Object to, Map<String, String> changes) {
        if (!Objects.equals(from, to)) {
            changes.put(key, String.valueOf(from) + " -> " + String.valueOf(to));
        }
    }

    private String render(String template, Map<String, Object> context) {
        Matcher matcher = TEMPLATE_TOKEN.matcher(template);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            Object resolved = value(context, matcher.group(1).trim());
            matcher.appendReplacement(
                    output,
                    Matcher.quoteReplacement(
                            resolved == null ? matcher.group() : String.valueOf(resolved)));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private Object value(Map<String, Object> context, String path) {
        if (context.containsKey(path)) return context.get(path);
        Object current = context;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> values)) return null;
            current = values.get(segment);
        }
        return current;
    }

    private List<String> strings(Object value) {
        if (!(value instanceof List<?> values)) return List.of();
        return values.stream().map(String::valueOf).toList();
    }

    private Map<String, String> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> values)) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        values.forEach((key, item) -> result.put(String.valueOf(key), String.valueOf(item)));
        return result;
    }

    private String text(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static String hash(Object value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private boolean connectorPrincipalEligible(IntegrationPrincipal principal) {
        if (principal == null || principal.principalType() != IntegrationPrincipalType.SERVICE_ACCOUNT) return false;
        return principal.status() != IntegrationPrincipalStatus.REVOKED
                && principal.status() != IntegrationPrincipalStatus.EXPIRED
                && principal.status() != IntegrationPrincipalStatus.DISABLED;
    }

    private static int bounded(int value, int max) {
        return Math.max(1, Math.min(value <= 0 ? 100 : value, max));
    }

    private static String required(String value) {
        if (blank(value)) throw new IllegalArgumentException("Required value is missing.");
        return value.trim();
    }

    private static String first(String... values) {
        for (String value : values) {
            if (!blank(value)) return value;
        }
        return null;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
