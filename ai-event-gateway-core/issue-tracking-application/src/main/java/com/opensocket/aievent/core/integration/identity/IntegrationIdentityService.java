package com.opensocket.aievent.core.integration.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Stream;
import org.springframework.transaction.annotation.Transactional;
import com.opensocket.aievent.core.issue.tracking.identity.IntegrationSecretReferencePolicy;

public class IntegrationIdentityService {
    private static final Set<String> ELEVATED = Set.of(
            "PROVIDER_ADMINISTRATOR", "GLOBAL_PROJECT_BROWSE", "GLOBAL_PROJECT_WRITE",
            "USER_ADMINISTRATION", "PERMISSION_SCHEME_ADMINISTRATION", "SYSTEM_CONFIGURATION_ADMINISTRATION");
    private static final Duration DEFAULT_ROTATION_GRACE = Duration.ofHours(24);

    private final IntegrationIdentityRepository repository;
    private final IntegrationPermissionProbeGateway probeGateway;

    public IntegrationIdentityService(IntegrationIdentityRepository repository,
                                      IntegrationPermissionProbeGateway probeGateway) {
        this.repository = repository;
        this.probeGateway = probeGateway;
    }

    public IntegrationConnection connection(String tenant, String id) {
        return repository.findConnection(required(tenant, "tenantId"), required(id, "connectionId"))
                .orElseThrow(() -> missing("Integration Connection", id));
    }

    public List<IntegrationConnection> connections(String tenant, int limit) {
        return repository.listConnections(required(tenant, "tenantId"), bounded(limit));
    }

    public List<IntegrationConnection> connections(String tenant, IntegrationResourceScope scope, int limit) {
        return repository.listConnectionsAuthorized(required(tenant, "tenantId"), Objects.requireNonNull(scope, "scope"), bounded(limit));
    }

    @Transactional
    public IntegrationConnection saveConnection(String tenant, String id, IntegrationConnection body, Long expected) {
        var now = OffsetDateTime.now();
        var existing = repository.findConnection(tenant, id);
        checkVersion(existing.map(IntegrationConnection::version).orElse(0L), expected);
        var value = new IntegrationConnection(
                tenant, id, Objects.requireNonNull(body.providerType(), "providerType is required"),
                required(body.connectionName(), "connectionName"), body.ownerDepartmentId(), body.ownerGroupId(), required(body.baseUrl(), "baseUrl"),
                blank(body.deploymentType(), "SELF_HOSTED"), body.providerVersion(),
                body.status() == null ? IntegrationConnectionStatus.DRAFT : body.status(),
                body.timeoutMs() <= 0 ? 10000 : body.timeoutMs(), body.retryPolicyId(), body.rateLimitPolicyId(),
                body.tlsPolicyId(), body.enabled(), existing.map(v -> v.version() + 1).orElse(1L),
                existing.map(IntegrationConnection::createdAt).orElse(now), now);
        return repository.saveConnection(value);
    }

    public IntegrationPrincipal principal(String tenant, String id) {
        return repository.findPrincipal(required(tenant, "tenantId"), required(id, "principalId"))
                .orElseThrow(() -> missing("Integration Principal", id));
    }

    public List<IntegrationPrincipal> principals(String tenant, String connection, int limit) {
        return repository.listPrincipals(required(tenant, "tenantId"), connection, bounded(limit));
    }

    public List<IntegrationPrincipal> principals(String tenant, String connection, IntegrationResourceScope scope, int limit) {
        return repository.listPrincipalsAuthorized(required(tenant, "tenantId"), connection, Objects.requireNonNull(scope, "scope"), bounded(limit));
    }

    @Transactional
    public IntegrationPrincipal savePrincipal(String tenant, String connectionId, String id,
                                              IntegrationPrincipal body, Long expected) {
        repository.findConnection(tenant, connectionId)
                .orElseThrow(() -> missing("Integration Connection", connectionId));
        var old = repository.findPrincipal(tenant, id);
        checkVersion(old.map(IntegrationPrincipal::version).orElse(0L), expected);
        var now = OffsetDateTime.now();
        var value = new IntegrationPrincipal(
                tenant, id, connectionId, required(body.principalName(), "principalName"),
                body.principalType() == null ? IntegrationPrincipalType.SERVICE_ACCOUNT : body.principalType(),
                body.ownerDepartmentId(), body.ownerGroupId(), body.trustZoneId(), body.externalPrincipalIdentifier(),
                body.status() == null ? IntegrationPrincipalStatus.DRAFT : body.status(),
                body.riskLevel() == null ? IntegrationRiskLevel.UNKNOWN : body.riskLevel(),
                body.permissionSummary() == null ? Map.of() : body.permissionSummary(),
                body.overPrivilegedReasons() == null ? List.of() : body.overPrivilegedReasons(),
                body.lastPermissionProbeAt(), old.map(v -> v.version() + 1).orElse(1L),
                old.map(IntegrationPrincipal::createdAt).orElse(now), now);
        return repository.savePrincipal(value);
    }

    public IntegrationPrincipalScope principalScope(String tenant, String principalId) {
        return repository.findPrincipalScope(required(tenant, "tenantId"), required(principalId, "principalId"))
                .orElseThrow(() -> missing("Integration Principal Scope", principalId));
    }

    public List<IntegrationPrincipalScope> principalScopes(String tenant, String connectionId, int limit) {
        return repository.listPrincipalScopes(required(tenant, "tenantId"), connectionId, bounded(limit));
    }

    @Transactional
    public IntegrationPrincipalScope savePrincipalScope(String tenant, String principalId,
                                                        IntegrationPrincipalScope body, Long expected) {
        var principal = repository.findPrincipal(tenant, principalId)
                .orElseThrow(() -> missing("Integration Principal", principalId));
        var old = repository.findPrincipalScope(tenant, principalId);
        checkVersion(old.map(IntegrationPrincipalScope::version).orElse(0L), expected);
        var mode = body.isolationMode() == null ? IntegrationIsolationMode.PER_PROJECT : body.isolationMode();
        if (mode == IntegrationIsolationMode.GLOBAL_ADMIN && principal.principalType() != IntegrationPrincipalType.BREAK_GLASS) {
            throw new IllegalStateException(IntegrationIdentityReasonCode.GLOBAL_ADMIN_PRINCIPAL_FORBIDDEN.name());
        }
        if (mode == IntegrationIsolationMode.PER_PROJECT) required(body.scopeReference(), "scopeReference");
        if (mode == IntegrationIsolationMode.PER_TRUST_ZONE
                && !Objects.equals(principal.trustZoneId(), body.scopeReference())) {
            throw new IllegalStateException("TRUST_ZONE_SCOPE_MISMATCH");
        }
        var now = OffsetDateTime.now();
        return repository.savePrincipalScope(new IntegrationPrincipalScope(
                tenant, principalId, mode, n(body.scopeReference()), body.allowedProjectIds(),
                body.allowedOperations(), body.allowedIssueTypes(),
                body.productionAllowed() && mode != IntegrationIsolationMode.GLOBAL_ADMIN,
                old.map(v -> v.version() + 1).orElse(1L), old.map(IntegrationPrincipalScope::createdAt).orElse(now), now));
    }

    public IntegrationCredentialMetadata credential(String tenant, String id) {
        return repository.findCredential(required(tenant, "tenantId"), required(id, "credentialId"))
                .orElseThrow(() -> missing("Integration Credential", id));
    }

    public List<IntegrationCredentialMetadata> credentials(String tenant, String principal, int limit) {
        return repository.listCredentials(required(tenant, "tenantId"), principal, bounded(limit));
    }

    public List<IntegrationCredentialMetadata> credentials(String tenant, String principal, IntegrationResourceScope scope, int limit) {
        return repository.listCredentialsAuthorized(required(tenant, "tenantId"), principal, Objects.requireNonNull(scope, "scope"), bounded(limit));
    }

    @Transactional
    public IntegrationCredentialMetadata addCredential(String tenant, String principalId, String credentialId,
                                                       IntegrationCredentialMetadata body) {
        repository.findPrincipal(tenant, principalId).orElseThrow(() -> missing("Integration Principal", principalId));
        validateSecretReference(body.secretRef());
        var existing = repository.findCredential(tenant, credentialId);
        if (existing.isPresent()) return existing.get();
        var now = OffsetDateTime.now();
        return repository.saveCredential(new IntegrationCredentialMetadata(
                tenant, credentialId, principalId, Objects.requireNonNull(body.authType(), "authType is required"),
                body.secretRef().trim(), required(body.secretVersion(), "secretVersion"), safeLast4(body.secretLast4()),
                body.validFrom() == null ? now : body.validFrom(), body.expiresAt(), body.rotatedAt(), null,
                IntegrationCredentialStatus.PENDING_VALIDATION, 1, now, now));
    }

    @Transactional
    public void revokePendingCredentials(String tenant, String principalId) {
        var now = OffsetDateTime.now();
        for (var credential : repository.listCredentials(required(tenant, "tenantId"), required(principalId, "principalId"), 1000)) {
            if (credential.status() == IntegrationCredentialStatus.PENDING_VALIDATION) {
                repository.saveCredential(copyCredential(credential, IntegrationCredentialStatus.REVOKED, credential.expiresAt(), now));
            }
        }
    }

    @Transactional
    public IntegrationCredentialMetadata recordCredentialUse(String tenant, String credentialId, OffsetDateTime usedAt) {
        var credential = repository.findCredential(required(tenant, "tenantId"), required(credentialId, "credentialId"))
                .orElseThrow(() -> missing("Integration Credential", credentialId));
        var now = usedAt == null ? OffsetDateTime.now() : usedAt;
        var updated = new IntegrationCredentialMetadata(credential.tenantId(), credential.credentialId(), credential.principalId(),
                credential.authType(), credential.secretRef(), credential.secretVersion(), credential.secretLast4(),
                credential.validFrom(), credential.expiresAt(), credential.rotatedAt(), now, credential.status(),
                credential.version() + 1, credential.createdAt(), now);
        return repository.saveCredential(updated);
    }

    @Transactional
    public IntegrationCredentialMetadata rotateCredential(String tenant, String principalId, String oldCredentialId,
                                                           String newCredentialId, IntegrationCredentialMetadata body) {
        var old = repository.findCredential(tenant, oldCredentialId)
                .orElseThrow(() -> missing("Integration Credential", oldCredentialId));
        if (!principalId.equals(old.principalId())) {
            throw new IllegalArgumentException("Credential does not belong to the requested Principal.");
        }
        if (old.status() != IntegrationCredentialStatus.ACTIVE) {
            throw new IllegalStateException("Only the active credential can start a rotation.");
        }
        var created = addCredential(tenant, principalId, newCredentialId, body);
        var now = OffsetDateTime.now();
        repository.saveRotationEvent(new CredentialRotationEvent(
                tenant, "rotation-" + UUID.randomUUID(), principalId, oldCredentialId, newCredentialId,
                "PENDING_PROBE", "The old credential remains active until the new credential passes provider authentication/connectivity validation.",
                UUID.randomUUID().toString(), "SYSTEM", "integration-identity-service", now, null, null, null));
        return created;
    }

    @Transactional
    public CredentialRotationEvent completeRotation(String tenant, String principalId, String rotationEventId) {
        var event = repository.listRotationEvents(tenant, principalId, 1000).stream()
                .filter(v -> rotationEventId.equals(v.rotationEventId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(IntegrationIdentityReasonCode.CREDENTIAL_ROTATION_NOT_FOUND.name()));
        if (!"ACTIVE_GRACE_PERIOD".equals(event.status())) {
            throw new IllegalStateException(IntegrationIdentityReasonCode.CREDENTIAL_ROTATION_PROBE_REQUIRED.name());
        }
        var now = OffsetDateTime.now();
        var old = repository.findCredential(tenant, event.oldCredentialId())
                .orElseThrow(() -> missing("Integration Credential", event.oldCredentialId()));
        repository.saveCredential(copyCredential(old, IntegrationCredentialStatus.REVOKED, old.expiresAt(), now));
        var completed = new CredentialRotationEvent(event.tenantId(), event.rotationEventId(), event.principalId(),
                event.oldCredentialId(), event.newCredentialId(), "COMPLETED", event.reason(), event.correlationId(),
                event.actorType(), event.actorId(), event.createdAt(), event.graceExpiresAt(), event.activatedAt(), now);
        return repository.saveRotationEvent(completed);
    }

    @Transactional
    public IntegrationPrincipal revokePrincipal(String tenant, String principalId, String reason) {
        var p = repository.findPrincipal(tenant, principalId).orElseThrow(() -> missing("Integration Principal", principalId));
        var now = OffsetDateTime.now();
        for (var c : repository.listCredentials(tenant, principalId, 1000)) {
            repository.saveCredential(copyCredential(c, IntegrationCredentialStatus.REVOKED, c.expiresAt(), now));
        }
        return repository.savePrincipal(copyPrincipal(p, IntegrationPrincipalStatus.REVOKED, IntegrationRiskLevel.HIGH,
                p.permissionSummary(), p.overPrivilegedReasons(), p.lastPermissionProbeAt(), now));
    }

    public IntegrationProjectMapping mapping(String tenant, String id) {
        return repository.findMapping(required(tenant, "tenantId"), required(id, "mappingId"))
                .orElseThrow(() -> missing("Project Mapping", id));
    }

    public List<IntegrationProjectMapping> mappings(String tenant, String connection, int limit) {
        return repository.listMappings(required(tenant, "tenantId"), connection, bounded(limit));
    }

    public List<IntegrationProjectMapping> mappings(String tenant, String connection, IntegrationResourceScope scope, int limit) {
        return repository.listMappingsAuthorized(required(tenant, "tenantId"), connection, Objects.requireNonNull(scope, "scope"), bounded(limit));
    }

    @Transactional
    public IntegrationProjectMapping saveMapping(String tenant, String id, IntegrationProjectMapping body, Long expected) {
        var old = repository.findMapping(tenant, id);
        checkVersion(old.map(IntegrationProjectMapping::version).orElse(0L), expected);
        if (old.isPresent() && !old.get().mutable()) {
            throw new IllegalStateException("PUBLISHED_MAPPING_IMMUTABLE_CREATE_NEW_VERSION");
        }
        repository.findConnection(tenant, body.connectionId())
                .orElseThrow(() -> missing("Integration Connection", body.connectionId()));
        // I0-C/D: legacy operation-principal slots remain compatibility metadata only.
        // Mapping persistence validates referential integrity, not provider permissions.
        // Canonical connector runtime resolves one technical SERVICE_ACCOUNT and lets Redmine
        // remain the final Issue permission/workflow authority.
        for (String principalId : mappingPrincipalIds(body)) {
            var principal = repository.findPrincipal(tenant, principalId)
                    .orElseThrow(() -> missing("Integration Principal", principalId));
            if (!body.connectionId().equals(principal.connectionId())) {
                throw new IllegalArgumentException("Project Mapping principals must use the same Connection.");
            }
        }
        var now = OffsetDateTime.now();
        var value = new IntegrationProjectMapping(
                tenant, id, body.connectionId(), body.departmentId(), body.groupId(), body.serviceDomainId(),
                body.sourceSystemId(), body.taskType(), required(body.externalProjectId(), "externalProjectId"),
                body.externalProjectKey(), body.externalIssueType(), body.externalTrackerId(), body.readPrincipalId(),
                body.createPrincipalId(), body.commentPrincipalId(), body.updatePrincipalId(), body.relationPrincipalId(),
                body.webhookPrincipalId(), body.permissionProfileId(), body.contextPolicyId(), body.resultSharingPolicyId(),
                body.providerWriteIdentityPolicy(), body.mappingStatus() == null ? ProjectMappingStatus.DRAFT : body.mappingStatus(),
                body.resolutionPriority() <= 0 ? 1000 : body.resolutionPriority(), body.defaultMapping(), false,
                body.lifecycleStatus() == null ? ProjectMappingLifecycle.DRAFT : body.lifecycleStatus(),
                old.map(IntegrationProjectMapping::mappingVersion).orElse(Math.max(1, body.mappingVersion())),
                body.summaryTemplate(), body.descriptionTemplate(), body.requiredFields(), body.customFieldMappings(),
                body.transitionMappings(), body.commentPolicy(), body.linkPolicy(), body.metadataSnapshotId(),
                body.metadataSchemaHash(), body.validatedAt(), body.publishedAt(), body.supersedesMappingVersion(),
                old.map(v -> v.version() + 1).orElse(1L), old.map(IntegrationProjectMapping::createdAt).orElse(now), now);
        return repository.saveMapping(value);
    }

    public IntegrationProjectMapping resolve(MappingResolutionRequest request) {
        var candidates = repository.resolutionCandidates(request).stream()
                .filter(m -> match(m.departmentId(), request.departmentId()) && match(m.groupId(), request.groupId())
                        && match(m.serviceDomainId(), request.serviceDomainId())
                        && match(m.sourceSystemId(), request.sourceSystemId()) && match(m.taskType(), request.taskType()))
                .sorted(Comparator.comparingInt(this::specificity).reversed()
                        .thenComparingInt(IntegrationProjectMapping::resolutionPriority)).toList();
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException(IntegrationIdentityReasonCode.PROJECT_MAPPING_NOT_FOUND.name()
                    + ": No enabled Project Mapping matched tenantId=" + request.tenantId()
                    + ", connectionId=" + evidence(request.connectionId())
                    + ", departmentId=" + evidence(request.departmentId())
                    + ", groupId=" + evidence(request.groupId())
                    + ", serviceDomainId=" + evidence(request.serviceDomainId())
                    + ", sourceSystemId=" + evidence(request.sourceSystemId())
                    + ", taskType=" + evidence(request.taskType()) + ".");
        }
        if (candidates.size() > 1 && specificity(candidates.get(0)) == specificity(candidates.get(1))
                && candidates.get(0).resolutionPriority() == candidates.get(1).resolutionPriority()) {
            throw new IllegalStateException(IntegrationIdentityReasonCode.PROJECT_MAPPING_AMBIGUOUS.name());
        }
        return candidates.get(0);
    }

    @Transactional
    public PermissionProbeResult probe(String tenant, String principalId, String mappingId, String correlationId) {
        var principal = repository.findPrincipal(tenant, principalId)
                .orElseThrow(() -> missing("Integration Principal", principalId));
        var connection = repository.findConnection(tenant, principal.connectionId())
                .orElseThrow(() -> missing("Integration Connection", principal.connectionId()));
        var credential = selectProbeCredential(tenant, principalId);
        var mapping = mappingId == null ? null : repository.findMapping(tenant, mappingId)
                .orElseThrow(() -> missing("Project Mapping", mappingId));
        if (mapping != null) validatePrincipalScopeForMapping(tenant, principal, mapping);
        var previous = repository.latestProbe(tenant, principalId, mappingId);
        var start = OffsetDateTime.now();
        var observation = probeGateway.probe(connection, principal, credential, mapping);
        var elevated = observation.elevatedPermissions().stream().map(String::toUpperCase)
                .filter(ELEVATED::contains).distinct().toList();
        var status = elevated.isEmpty() ? deriveMappingStatus(observation.capabilities()) : ProjectMappingStatus.OVER_PRIVILEGED;
        var result = new PermissionProbeResult(
                tenant, "probe-" + UUID.randomUUID(), connection.connectionId(), principalId, mappingId, status,
                observation.capabilities(), elevated, observation.summary(), start, OffsetDateTime.now(), correlationId);
        repository.saveProbe(result);
        recordPermissionChange(previous.orElse(null), result);
        var principalStatus = elevated.isEmpty()
                ? (status == ProjectMappingStatus.VALID ? IntegrationPrincipalStatus.ACTIVE : IntegrationPrincipalStatus.DEGRADED)
                : IntegrationPrincipalStatus.OVER_PRIVILEGED;
        repository.savePrincipal(copyPrincipal(principal, principalStatus,
                elevated.isEmpty() ? IntegrationRiskLevel.LOW : IntegrationRiskLevel.CRITICAL,
                toSummary(observation.capabilities()), elevated, OffsetDateTime.now(), OffsetDateTime.now()));
        // I0-C/D: permission observations are diagnostic evidence only. A probe must not
        // disable or change lifecycle state of a routing Mapping. Credential activation is
        // based on successful provider authentication, not speculative write-permission probes.
        if (observation.capabilities().getOrDefault(IntegrationPermissionCapability.AUTHENTICATE, PermissionProbeResultStatus.DENIED)
                == PermissionProbeResultStatus.GRANTED) {
            activatePendingRotation(tenant, principalId, credential.credentialId());
        }
        return result;
    }

    /**
     * Canonical connector readiness probe for the current I0-C/D runtime.
     *
     * Unlike the historical scoped permission probe, this path deliberately does not
     * consult IntegrationPrincipalScope. The canonical Issue runtime uses Project Mapping
     * + one technical Service Account + Credential, while Redmine/Jira remains the final
     * Issue permission authority. Provider permission evidence remains diagnostic only.
     */
    @Transactional
    public PermissionProbeResult probeConnectorExecutionContext(String tenant, String principalId,
                                                                 String mappingId, String correlationId) {
        var principal = repository.findPrincipal(tenant, principalId)
                .orElseThrow(() -> missing("Integration Principal", principalId));
        var connection = repository.findConnection(tenant, principal.connectionId())
                .orElseThrow(() -> missing("Integration Connection", principal.connectionId()));
        var credential = selectProbeCredential(tenant, principalId);
        var mapping = repository.findMapping(tenant, mappingId)
                .orElseThrow(() -> missing("Project Mapping", mappingId));
        var blockers = ProjectMappingRuntimeReadiness.blockers(mapping);
        if (!blockers.isEmpty()) {
            throw new IllegalStateException("ISSUE_CONNECTOR_PROJECT_MAPPING_NOT_RUNTIME_READY: "
                    + String.join(",", blockers));
        }
        if (!Objects.equals(mapping.connectionId(), connection.connectionId())) {
            throw new IllegalStateException("ISSUE_CONNECTOR_IDENTITY_CONNECTION_MISMATCH");
        }
        var canonicalPrincipal = resolveConnectorPrincipal(tenant, mapping, connection);
        if (!Objects.equals(canonicalPrincipal.principalId(), principalId)) {
            throw new IllegalStateException("ISSUE_CONNECTOR_IDENTITY_MISMATCH: expected="
                    + canonicalPrincipal.principalId() + ", actual=" + principalId);
        }
        var previous = repository.latestProbe(tenant, principalId, mappingId);
        var start = OffsetDateTime.now();
        var observation = probeGateway.probe(connection, principal, credential, mapping);
        var elevated = observation.elevatedPermissions().stream().map(String::toUpperCase)
                .filter(ELEVATED::contains).distinct().toList();
        var status = elevated.isEmpty() ? deriveMappingStatus(observation.capabilities()) : ProjectMappingStatus.OVER_PRIVILEGED;
        var result = new PermissionProbeResult(
                tenant, "probe-" + UUID.randomUUID(), connection.connectionId(), principalId, mappingId, status,
                observation.capabilities(), elevated, observation.summary(), start, OffsetDateTime.now(), correlationId);
        repository.saveProbe(result);
        recordPermissionChange(previous.orElse(null), result);
        var principalStatus = elevated.isEmpty()
                ? (status == ProjectMappingStatus.VALID ? IntegrationPrincipalStatus.ACTIVE : IntegrationPrincipalStatus.DEGRADED)
                : IntegrationPrincipalStatus.OVER_PRIVILEGED;
        repository.savePrincipal(copyPrincipal(principal, principalStatus,
                elevated.isEmpty() ? IntegrationRiskLevel.LOW : IntegrationRiskLevel.CRITICAL,
                toSummary(observation.capabilities()), elevated, OffsetDateTime.now(), OffsetDateTime.now()));
        if (observation.capabilities().getOrDefault(IntegrationPermissionCapability.AUTHENTICATE, PermissionProbeResultStatus.DENIED)
                == PermissionProbeResultStatus.GRANTED) {
            activatePendingRotation(tenant, principalId, credential.credentialId());
        }
        return result;
    }

    public ScopedIntegrationExecutionContext resolveExecutionContext(String tenant, String mappingId,
                                                                     IntegrationOperation operation) {
        var mapping = mapping(tenant, mappingId);
        if (!mapping.enabled()) throw new IllegalStateException("Project Mapping is not enabled.");
        String principalId = principalId(mapping, operation);
        if (principalId == null) {
            throw new IllegalStateException(IntegrationIdentityReasonCode.INTEGRATION_PRINCIPAL_NOT_AUTHORIZED.name()
                    + ": No Principal is configured for " + operation + ".");
        }
        var principal = principal(tenant, principalId);
        var scope = principalScope(tenant, principalId);
        validatePrincipalScopeForMapping(tenant, principal, mapping);
        if (principal.blocksProductionMapping() && !activeOverride(tenant, principalId, mappingId)) {
            throw new IllegalStateException(IntegrationIdentityReasonCode.SECURITY_OVERRIDE_REQUIRED.name());
        }
        var probe = validateProbeForOperation(tenant, principalId, mappingId, operation);
        var credential = selectRuntimeCredential(tenant, principalId);
        return new ScopedIntegrationExecutionContext(connection(tenant, principal.connectionId()), principal, scope,
                credential, mapping, operation, probe);
    }

    public ScopedIntegrationExecutionContext resolveExecutionContext(MappingResolutionRequest request,
                                                                     IntegrationOperation operation) {
        return resolveExecutionContext(request.tenantId(), resolve(request).mappingId(), operation);
    }

    /**
     * Resolve the canonical I0 connector execution context without treating
     * Operation Principal scope or Permission Probe evidence as Issue
     * authorization. The selected Principal is only the technical execution
     * identity that owns the provider credential reference.
     */
    public ConnectorIntegrationExecutionContext resolveConnectorExecutionContext(MappingResolutionRequest request) {
        Objects.requireNonNull(request, "request");
        var mapping = resolve(request);
        List<String> mappingBlockers = ProjectMappingRuntimeReadiness.blockers(mapping);
        if (!mappingBlockers.isEmpty()) {
            throw new IllegalStateException("ISSUE_CONNECTOR_PROJECT_MAPPING_NOT_RUNTIME_READY: "
                    + String.join(",", mappingBlockers));
        }
        var connection = connection(request.tenantId(), mapping.connectionId());
        if (!connection.enabled() || connection.status() != IntegrationConnectionStatus.ACTIVE) {
            throw new IllegalStateException("ISSUE_CONNECTOR_CONNECTION_NOT_ACTIVE: " + connection.status());
        }
        if (connection.providerType() != IntegrationProviderType.REDMINE) {
            throw new IllegalStateException("ISSUE_CONNECTOR_PROVIDER_NOT_ENABLED: " + connection.providerType());
        }
        var principal = resolveConnectorPrincipal(request.tenantId(), mapping, connection);
        var credential = selectRuntimeCredential(request.tenantId(), principal.principalId());
        return new ConnectorIntegrationExecutionContext(connection, principal, credential, mapping);
    }

    private IntegrationPrincipal resolveConnectorPrincipal(String tenant, IntegrationProjectMapping mapping,
                                                           IntegrationConnection connection) {
        var explicit = mappingPrincipalIds(mapping);
        if (explicit.size() > 1) {
            throw new IllegalStateException("ISSUE_CONNECTOR_IDENTITY_AMBIGUOUS: Project Mapping still references multiple operation principals.");
        }
        IntegrationPrincipal principal;
        if (explicit.size() == 1) {
            principal = principal(tenant, explicit.get(0));
        } else {
            var candidates = repository.listPrincipals(tenant, connection.connectionId(), 1000).stream()
                    .filter(this::connectorPrincipalEligible)
                    .toList();
            if (candidates.isEmpty()) {
                throw new IllegalStateException("ISSUE_CONNECTOR_IDENTITY_NOT_CONFIGURED");
            }
            if (candidates.size() > 1) {
                throw new IllegalStateException("ISSUE_CONNECTOR_IDENTITY_AMBIGUOUS: Configure one technical Service Account for this Connection.");
            }
            principal = candidates.get(0);
        }
        if (!Objects.equals(connection.connectionId(), principal.connectionId())) {
            throw new IllegalStateException("ISSUE_CONNECTOR_IDENTITY_CONNECTION_MISMATCH");
        }
        if (!connectorPrincipalEligible(principal)) {
            throw new IllegalStateException("ISSUE_CONNECTOR_IDENTITY_NOT_ACTIVE");
        }
        return principal;
    }

    private boolean connectorPrincipalEligible(IntegrationPrincipal principal) {
        if (principal == null || principal.principalType() != IntegrationPrincipalType.SERVICE_ACCOUNT) return false;
        return principal.status() != IntegrationPrincipalStatus.REVOKED
                && principal.status() != IntegrationPrincipalStatus.EXPIRED
                && principal.status() != IntegrationPrincipalStatus.DISABLED;
    }

    private IntegrationCredentialMetadata selectRuntimeCredential(String tenant, String principalId) {
        OffsetDateTime now = OffsetDateTime.now();
        return repository.listCredentials(tenant, principalId, 100).stream()
                .filter(c -> c.status() == IntegrationCredentialStatus.ACTIVE
                        || c.status() == IntegrationCredentialStatus.GRACE_PERIOD)
                .filter(c -> c.validFrom() == null || !c.validFrom().isAfter(now))
                .filter(c -> c.expiresAt() == null || c.expiresAt().isAfter(now))
                // ACTIVE is authoritative. GRACE_PERIOD exists only as rollback safety and
                // must never win merely because its row version is numerically larger.
                .sorted(Comparator
                        .comparingInt((IntegrationCredentialMetadata c) -> c.status() == IntegrationCredentialStatus.ACTIVE ? 0 : 1)
                        .thenComparing(IntegrationCredentialMetadata::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(IntegrationIdentityReasonCode.INTEGRATION_CREDENTIAL_EXPIRED.name()));
    }

    @Transactional
    public IntegrationProviderAuthorizationFailure recordAuthorizationFailure(String tenant, String mappingId,
                                                                               IntegrationOperation operation,
                                                                               int providerStatus,
                                                                               String correlationId) {
        if (providerStatus != 401 && providerStatus != 403) {
            throw new IllegalArgumentException("Only provider 401 or 403 responses create authorization failures.");
        }
        var mapping = mapping(tenant, mappingId);
        var principalId = principalId(mapping, operation);
        var principal = principal(tenant, principalId);
        var credential = repository.listCredentials(tenant, principalId, 100).stream()
                .filter(c -> c.status() == IntegrationCredentialStatus.ACTIVE).findFirst().orElse(null);
        var now = OffsetDateTime.now();
        var failure = new IntegrationProviderAuthorizationFailure(
                tenant, "auth-failure-" + UUID.randomUUID(), mapping.connectionId(), principalId,
                credential == null ? null : credential.credentialId(), mappingId, providerStatus, operation,
                IntegrationIdentityReasonCode.PROVIDER_AUTHORIZATION_FAILED.name(), "PENDING", correlationId, now, null);
        repository.saveAuthorizationFailure(failure);
        repository.saveMapping(copyMapping(mapping, ProjectMappingStatus.DEGRADED, false));
        try {
            probe(tenant, principalId, mappingId, correlationId);
            var reprobed = new IntegrationProviderAuthorizationFailure(
                    failure.tenantId(), failure.failureId(), failure.connectionId(), failure.principalId(),
                    failure.credentialId(), failure.mappingId(), failure.providerStatus(), failure.operation(),
                    failure.reasonCode(), "COMPLETED", failure.correlationId(), failure.occurredAt(), OffsetDateTime.now());
            return repository.saveAuthorizationFailure(reprobed);
        } catch (RuntimeException ignored) {
            return failure;
        }
    }

    @Transactional
    public IntegrationSecurityOverride grantSecurityOverride(String tenant, String overrideId,
                                                             IntegrationSecurityOverride body) {
        var principal = principal(tenant, body.principalId());
        if (body.mappingId() != null) mapping(tenant, body.mappingId());
        var now = OffsetDateTime.now();
        var expires = Objects.requireNonNull(body.expiresAt(), "expiresAt is required");
        if (!expires.isAfter(now)) throw new IllegalArgumentException("expiresAt must be in the future.");
        required(body.reason(), "reason"); required(body.approvedBy(), "approvedBy");
        return repository.saveSecurityOverride(new IntegrationSecurityOverride(
                tenant, overrideId, principal.principalId(), body.mappingId(),
                blank(body.overrideType(), "OVER_PRIVILEGED_MAPPING"), body.reason().trim(), body.approvedBy().trim(),
                now, expires, "APPROVED", null, null, body.correlationId(), 1, now, now));
    }

    @Transactional
    public IntegrationSecurityOverride revokeSecurityOverride(String tenant, String overrideId, String actor) {
        var value = repository.findSecurityOverride(tenant, overrideId)
                .orElseThrow(() -> missing("Integration Security Override", overrideId));
        var now = OffsetDateTime.now();
        return repository.saveSecurityOverride(new IntegrationSecurityOverride(
                value.tenantId(), value.overrideId(), value.principalId(), value.mappingId(), value.overrideType(),
                value.reason(), value.approvedBy(), value.approvedAt(), value.expiresAt(), "REVOKED",
                required(actor, "actor"), now, value.correlationId(), value.version() + 1, value.createdAt(), now));
    }

    public List<IntegrationSecurityOverride> securityOverrides(String tenant, String principalId,
                                                               String mappingId, int limit) {
        return repository.listSecurityOverrides(required(tenant, "tenantId"), principalId, mappingId, bounded(limit));
    }

    public List<IntegrationPermissionChangeEvent> permissionChanges(String tenant, String principalId,
                                                                    String mappingId, int limit) {
        return repository.listPermissionChangeEvents(required(tenant, "tenantId"), principalId, mappingId, bounded(limit));
    }

    public List<IntegrationProviderAuthorizationFailure> authorizationFailures(String tenant, String mappingId,
                                                                                int limit) {
        return repository.listAuthorizationFailures(required(tenant, "tenantId"), mappingId, bounded(limit));
    }

    public CrossProjectRelayReadiness relayReadiness(String tenant, String sourceMappingId, String targetMappingId) {
        var blockers = new ArrayList<String>();
        IntegrationProjectMapping source = null, target = null;
        try { source = mapping(tenant, sourceMappingId); } catch (RuntimeException e) { blockers.add("Source Project Mapping was not found."); }
        try { target = mapping(tenant, targetMappingId); } catch (RuntimeException e) { blockers.add("Target Project Mapping was not found."); }
        if (source != null && target != null) {
            if (Objects.equals(source.mappingId(), target.mappingId())) blockers.add("Source and target mappings must be different.");
            try { resolveExecutionContext(tenant, source.mappingId(), IntegrationOperation.READ); }
            catch (RuntimeException e) { blockers.add("Source read context: " + e.getMessage()); }
            try { resolveExecutionContext(tenant, source.mappingId(), IntegrationOperation.COMMENT); }
            catch (RuntimeException e) { blockers.add("Source backlink context: " + e.getMessage()); }
            try { resolveExecutionContext(tenant, target.mappingId(), IntegrationOperation.CREATE); }
            catch (RuntimeException e) { blockers.add("Target create context: " + e.getMessage()); }
            try { resolveExecutionContext(tenant, target.mappingId(), IntegrationOperation.COMMENT); }
            catch (RuntimeException e) { blockers.add("Target backlink context: " + e.getMessage()); }
        }
        return new CrossProjectRelayReadiness(blockers.isEmpty(), sourceMappingId, targetMappingId, blockers);
    }

    public CredentialBlastRadius blastRadius(String tenant, String credentialId) {
        return repository.blastRadius(required(tenant, "tenantId"), required(credentialId, "credentialId"));
    }

    private void validatePrincipalScopeForMapping(String tenant, IntegrationPrincipal principal,
                                                  IntegrationProjectMapping mapping) {
        var scope = repository.findPrincipalScope(tenant, principal.principalId())
                .orElseThrow(() -> new IllegalStateException(IntegrationIdentityReasonCode.PROJECT_SCOPE_NOT_GRANTED.name()));
        if (scope.isolationMode() == IntegrationIsolationMode.GLOBAL_ADMIN) {
            throw new IllegalStateException(IntegrationIdentityReasonCode.GLOBAL_ADMIN_PRINCIPAL_FORBIDDEN.name());
        }
        if (!scope.productionAllowed() || !scope.allowsProject(mapping.externalProjectId(), principal.trustZoneId())) {
            throw new IllegalStateException(IntegrationIdentityReasonCode.PROJECT_SCOPE_NOT_GRANTED.name()
                    + ": Principal scope does not include Project " + mapping.externalProjectId() + ".");
        }
        IntegrationOperation operation = operationForPrincipal(mapping, principal.principalId());
        if (!scope.allowsOperation(operation)) {
            throw new IllegalStateException("OPERATION_SCOPE_NOT_GRANTED: " + operation);
        }
        String issueType = mapping.externalIssueType() == null || mapping.externalIssueType().isBlank()
                ? mapping.externalTrackerId() : mapping.externalIssueType();
        if (!scope.allowsIssueType(issueType)) {
            throw new IllegalStateException("ISSUE_TYPE_SCOPE_NOT_GRANTED: " + issueType);
        }
    }

    private PermissionProbeResult validateProbeForOperation(String tenant, String principalId, String mappingId,
                                                            IntegrationOperation operation) {
        var probe = repository.latestProbe(tenant, principalId, mappingId)
                .orElseThrow(() -> new IllegalStateException(
                        IntegrationIdentityReasonCode.PROJECT_PERMISSION_PROBE_REQUIRED.name()));
        var capability = capability(operation);
        if (probe.capabilityResults().getOrDefault(capability, PermissionProbeResultStatus.NOT_TESTED)
                != PermissionProbeResultStatus.GRANTED) {
            throw new IllegalStateException(reason(operation).name());
        }
        return probe;
    }

    private IntegrationPermissionCapability capability(IntegrationOperation operation) {
        return switch (operation) {
            case READ -> IntegrationPermissionCapability.READ_ISSUE;
            case CREATE -> IntegrationPermissionCapability.CREATE_ISSUE;
            case COMMENT -> IntegrationPermissionCapability.ADD_COMMENT;
            case UPDATE -> IntegrationPermissionCapability.UPDATE_ISSUE;
            case RELATION -> IntegrationPermissionCapability.CREATE_RELATION;
            case WEBHOOK -> IntegrationPermissionCapability.OBSERVE_STATUS;
        };
    }

    private IntegrationIdentityReasonCode reason(IntegrationOperation operation) {
        return switch (operation) {
            case READ -> IntegrationIdentityReasonCode.PROJECT_READ_NOT_GRANTED;
            case CREATE -> IntegrationIdentityReasonCode.PROJECT_CREATE_NOT_GRANTED;
            case COMMENT -> IntegrationIdentityReasonCode.PROJECT_COMMENT_NOT_GRANTED;
            case UPDATE -> IntegrationIdentityReasonCode.PROJECT_UPDATE_NOT_GRANTED;
            case RELATION -> IntegrationIdentityReasonCode.PROJECT_RELATION_NOT_GRANTED;
            case WEBHOOK -> IntegrationIdentityReasonCode.INTEGRATION_PRINCIPAL_NOT_AUTHORIZED;
        };
    }

    private String principalId(IntegrationProjectMapping mapping, IntegrationOperation operation) {
        return switch (operation) {
            case READ -> n(mapping.readPrincipalId());
            case CREATE -> n(mapping.createPrincipalId());
            case COMMENT -> n(mapping.commentPrincipalId());
            case UPDATE -> n(mapping.updatePrincipalId());
            case RELATION -> n(mapping.relationPrincipalId());
            case WEBHOOK -> n(mapping.webhookPrincipalId());
        };
    }

    private IntegrationOperation operationForPrincipal(IntegrationProjectMapping mapping, String principalId) {
        if (Objects.equals(mapping.createPrincipalId(), principalId)) return IntegrationOperation.CREATE;
        if (Objects.equals(mapping.commentPrincipalId(), principalId)) return IntegrationOperation.COMMENT;
        if (Objects.equals(mapping.updatePrincipalId(), principalId)) return IntegrationOperation.UPDATE;
        if (Objects.equals(mapping.relationPrincipalId(), principalId)) return IntegrationOperation.RELATION;
        if (Objects.equals(mapping.webhookPrincipalId(), principalId)) return IntegrationOperation.WEBHOOK;
        return IntegrationOperation.READ;
    }

    private List<String> mappingPrincipalIds(IntegrationProjectMapping body) {
        return Stream.of(n(body.readPrincipalId()), n(body.createPrincipalId()), n(body.commentPrincipalId()),
                        n(body.updatePrincipalId()), n(body.relationPrincipalId()), n(body.webhookPrincipalId()))
                .filter(Objects::nonNull).distinct().toList();
    }

    private boolean activeOverride(String tenant, String principalId, String mappingId) {
        var now = OffsetDateTime.now();
        return repository.listSecurityOverrides(tenant, principalId, mappingId, 100).stream()
                .anyMatch(v -> v.activeAt(now) && (v.mappingId() == null || Objects.equals(mappingId, v.mappingId())));
    }

    private IntegrationCredentialMetadata selectProbeCredential(String tenant, String principalId) {
        return repository.listCredentials(tenant, principalId, 100).stream()
                .filter(c -> c.status() == IntegrationCredentialStatus.PENDING_VALIDATION
                        || c.status() == IntegrationCredentialStatus.ACTIVE)
                .sorted(Comparator.comparingInt(c -> c.status() == IntegrationCredentialStatus.PENDING_VALIDATION ? 0 : 1))
                .findFirst().orElseThrow(() -> missing("Active Integration Credential", principalId));
    }

    private void activatePendingRotation(String tenant, String principalId, String credentialId) {
        var pending = repository.listRotationEvents(tenant, principalId, 100).stream()
                .filter(v -> ("PENDING_VALIDATION".equals(v.status()) || "PENDING_PROBE".equals(v.status()))
                        && credentialId.equals(v.newCredentialId()))
                .findFirst();
        if (pending.isEmpty()) {
            var credential = repository.findCredential(tenant, credentialId).orElse(null);
            if (credential != null && credential.status() == IntegrationCredentialStatus.PENDING_VALIDATION) {
                repository.saveCredential(copyCredential(credential, IntegrationCredentialStatus.ACTIVE,
                        credential.expiresAt(), OffsetDateTime.now()));
            }
            return;
        }
        var event = pending.get();
        var now = OffsetDateTime.now();
        var graceEnd = now.plus(DEFAULT_ROTATION_GRACE);
        var newCredential = repository.findCredential(tenant, event.newCredentialId()).orElseThrow();
        repository.saveCredential(copyCredential(newCredential, IntegrationCredentialStatus.ACTIVE,
                newCredential.expiresAt(), now));
        var oldCredential = repository.findCredential(tenant, event.oldCredentialId()).orElseThrow();
        repository.saveCredential(copyCredential(oldCredential, IntegrationCredentialStatus.GRACE_PERIOD, graceEnd, now));
        repository.saveRotationEvent(new CredentialRotationEvent(event.tenantId(), event.rotationEventId(),
                event.principalId(), event.oldCredentialId(), event.newCredentialId(), "ACTIVE_GRACE_PERIOD",
                "The new credential passed provider authentication/connectivity validation. The old credential is in a 24-hour grace period.",
                event.correlationId(), event.actorType(), event.actorId(), event.createdAt(), graceEnd, now, null));
    }

    private void recordPermissionChange(PermissionProbeResult previous, PermissionProbeResult current) {
        var currentFingerprint = fingerprint(current);
        var previousFingerprint = previous == null ? null : fingerprint(previous);
        if (previous != null && Objects.equals(previousFingerprint, currentFingerprint)) return;
        repository.savePermissionChangeEvent(new IntegrationPermissionChangeEvent(
                current.tenantId(), "permission-change-" + UUID.randomUUID(), current.principalId(), current.mappingId(),
                previous == null ? null : previous.probeId(), current.probeId(), previousFingerprint, currentFingerprint,
                previous == null ? "Initial permission baseline recorded." : "Provider permission capability set changed.",
                OffsetDateTime.now(), current.correlationId()));
    }

    private String fingerprint(PermissionProbeResult value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var canonical = new TreeMap<String, String>();
            value.capabilityResults().forEach((k, v) -> canonical.put(k.name(), v.name()));
            canonical.put("elevated", String.join(",", new TreeSet<>(value.elevatedPermissions())));
            return HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ProjectMappingStatus deriveMappingStatus(Map<IntegrationPermissionCapability, PermissionProbeResultStatus> values) {
        if (values.values().stream().allMatch(v -> v == PermissionProbeResultStatus.NOT_TESTED)) return ProjectMappingStatus.DEGRADED;
        if (values.getOrDefault(IntegrationPermissionCapability.AUTHENTICATE, PermissionProbeResultStatus.ERROR)
                != PermissionProbeResultStatus.GRANTED) return ProjectMappingStatus.MISCONFIGURED;
        if (values.values().stream().anyMatch(v -> v == PermissionProbeResultStatus.ERROR)) return ProjectMappingStatus.UNREACHABLE;
        if (values.values().stream().anyMatch(v -> v == PermissionProbeResultStatus.DENIED)) return ProjectMappingStatus.DEGRADED;
        return ProjectMappingStatus.VALID;
    }

    private Map<String, String> toSummary(Map<IntegrationPermissionCapability, PermissionProbeResultStatus> source) {
        var out = new LinkedHashMap<String, String>(); source.forEach((k, v) -> out.put(k.name(), v.name())); return out;
    }

    private IntegrationPrincipal copyPrincipal(IntegrationPrincipal p, IntegrationPrincipalStatus status,
                                               IntegrationRiskLevel risk, Map<String, String> summary,
                                               List<String> reasons, OffsetDateTime probeAt, OffsetDateTime now) {
        return new IntegrationPrincipal(p.tenantId(), p.principalId(), p.connectionId(), p.principalName(),
                p.principalType(), p.ownerDepartmentId(), p.ownerGroupId(), p.trustZoneId(),
                p.externalPrincipalIdentifier(), status, risk, summary, reasons, probeAt, p.version() + 1,
                p.createdAt(), now);
    }

    private IntegrationCredentialMetadata copyCredential(IntegrationCredentialMetadata c,
                                                          IntegrationCredentialStatus status,
                                                          OffsetDateTime expiresAt, OffsetDateTime now) {
        return new IntegrationCredentialMetadata(c.tenantId(), c.credentialId(), c.principalId(), c.authType(),
                c.secretRef(), c.secretVersion(), c.secretLast4(), c.validFrom(), expiresAt, c.rotatedAt(),
                c.lastUsedAt(), status, c.version() + 1, c.createdAt(), now);
    }

    private IntegrationProjectMapping copyMapping(IntegrationProjectMapping m, ProjectMappingStatus status,
                                                  boolean enabled) {
        var now = OffsetDateTime.now();
        return new IntegrationProjectMapping(m.tenantId(), m.mappingId(), m.connectionId(), m.departmentId(),
                m.groupId(), m.serviceDomainId(), m.sourceSystemId(), m.taskType(), m.externalProjectId(),
                m.externalProjectKey(), m.externalIssueType(), m.externalTrackerId(), m.readPrincipalId(),
                m.createPrincipalId(), m.commentPrincipalId(), m.updatePrincipalId(), m.relationPrincipalId(),
                m.webhookPrincipalId(), m.permissionProfileId(), m.contextPolicyId(), m.resultSharingPolicyId(),
                m.providerWriteIdentityPolicy(), status, m.resolutionPriority(), m.defaultMapping(), enabled,
                enabled ? ProjectMappingLifecycle.ACTIVE : m.lifecycleStatus(), m.mappingVersion(), m.summaryTemplate(),
                m.descriptionTemplate(), m.requiredFields(), m.customFieldMappings(), m.transitionMappings(),
                m.commentPolicy(), m.linkPolicy(), m.metadataSnapshotId(), m.metadataSchemaHash(), m.validatedAt(),
                m.publishedAt(), m.supersedesMappingVersion(), m.version() + 1, m.createdAt(), now);
    }

    private int specificity(IntegrationProjectMapping m) { int s=0; if(m.groupId()!=null)s+=32; if(m.departmentId()!=null)s+=16; if(m.serviceDomainId()!=null)s+=8; if(m.taskType()!=null)s+=4; if(m.sourceSystemId()!=null)s+=2; if(m.defaultMapping())s+=1; return s; }
    private boolean match(String rule,String actual){return rule==null||rule.isBlank()||Objects.equals(rule,actual);}
    private int bounded(int n){return Math.max(1,Math.min(n,1000));}
    private String evidence(String value){return value==null||value.isBlank()?"-":value.trim();}
    private String required(String v,String n){if(v==null||v.isBlank())throw new IllegalArgumentException(n+" is required.");return v.trim();}
    private String blank(String v,String d){return v==null||v.isBlank()?d:v.trim();}
    private String n(String v){return v==null||v.isBlank()?null:v.trim();}
    private String safeLast4(String v){if(v==null||v.isBlank())return null;return v.length()<=4?v:v.substring(v.length()-4);}
    private void validateSecretReference(String ref){
        try { IntegrationSecretReferencePolicy.validate(ref); }
        catch (IllegalArgumentException ex) {
            if ("SECRET_VALUE_MUST_NOT_BE_PERSISTED".equals(ex.getMessage()))
                throw new IllegalArgumentException(IntegrationIdentityReasonCode.SECRET_VALUE_MUST_NOT_BE_PERSISTED.name());
            throw ex;
        }
    }
    private void checkVersion(long actual,Long expected){if(expected!=null&&expected!=actual)throw new IllegalStateException(IntegrationIdentityReasonCode.RESOURCE_VERSION_CONFLICT.name());}
    private IllegalArgumentException missing(String type,String id){return new IllegalArgumentException(type+" "+id+" was not found in Tenant.");}
}
