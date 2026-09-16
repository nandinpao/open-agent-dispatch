package com.opensocket.aievent.core.resourceaccess.core;

import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.core.resourceaccess.contract.AuthorizationDecision;
import com.opensocket.aievent.core.resourceaccess.contract.AuthorizationDecisionAuditRecord;
import com.opensocket.aievent.core.resourceaccess.contract.AuthorizationDecisionMode;
import com.opensocket.aievent.core.resourceaccess.contract.AuthorizationRequest;
import com.opensocket.aievent.core.resourceaccess.contract.AuthorizationSimulationRequest;
import com.opensocket.aievent.core.resourceaccess.contract.DecisionEffect;
import com.opensocket.aievent.core.resourceaccess.contract.DecisionReason;
import com.opensocket.aievent.core.resourceaccess.contract.DescriptorResolutionContext;
import com.opensocket.aievent.core.resourceaccess.contract.ExplicitDenyRecord;
import com.opensocket.aievent.core.resourceaccess.contract.LegacyAuthorizationDecision;
import com.opensocket.aievent.core.resourceaccess.contract.LegacyAuthorizationPort;
import com.opensocket.aievent.core.resourceaccess.contract.OperationPhase;
import com.opensocket.aievent.core.resourceaccess.contract.OwnershipDescriptor;
import com.opensocket.aievent.core.resourceaccess.contract.PolicyPrincipalRef;
import com.opensocket.aievent.core.resourceaccess.contract.PolicyVersion;
import com.opensocket.aievent.core.resourceaccess.contract.PrincipalClearanceRecord;
import com.opensocket.aievent.core.resourceaccess.contract.PrincipalScopeSnapshot;
import com.opensocket.aievent.core.resourceaccess.contract.RequestChannel;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceAccessEnforcementMode;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceAction;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceAuthorizationCacheKey;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceAuthorizationCachePort;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceAuthorizationPort;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceDecisionReasonCodes;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceDescriptor;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceParticipantProjection;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceParticipantType;
import com.opensocket.aievent.core.resourceaccess.contract.ResourcePermissionAuthorityPort;
import com.opensocket.aievent.core.resourceaccess.contract.ResourcePermissionDecision;
import com.opensocket.aievent.core.resourceaccess.contract.ResourcePermissionScopeDecision;
import com.opensocket.aievent.core.resourceaccess.contract.PersistedResourceScopeShare;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceScopeShareRepositoryPort;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceScopeShareTarget;
import com.opensocket.aievent.core.resourceaccess.contract.ScopeGrantRecord;
import com.opensocket.aievent.core.resourceaccess.contract.ScopePrincipalType;
import com.opensocket.aievent.core.resourceaccess.contract.ScopeType;
import com.opensocket.aievent.core.resourceaccess.contract.SecurityEpoch;
import com.opensocket.aievent.core.resourceaccess.contract.SensitivityLevel;
import com.opensocket.aievent.core.resourceaccess.contract.ShadowDecisionComparison;
import com.opensocket.aievent.core.resourceaccess.contract.ShadowMismatchCategory;
import com.opensocket.aievent.core.resourceaccess.contract.ShadowDecisionComparisonV2;
import com.opensocket.aievent.core.resourceaccess.contract.ShadowDecisionEvidenceV2;
import com.opensocket.aievent.core.resourceaccess.contract.ShadowMismatchCategoryV2;
import com.opensocket.aievent.core.resourceaccess.contract.LegacyAuthorizationPortV2;
import com.opensocket.aievent.core.resourceaccess.contract.VisibilityLevel;
import com.opensocket.aievent.core.resourceaccess.contract.VisibilityPolicyRecord;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** P4RA-D explainable, server-resolved and fail-closed Resource Authorization engine. */
public final class DefaultResourceAuthorizationService implements ResourceAuthorizationPort {
    private final AuthoritativeResourceDescriptorService descriptors;
    private final ResourceDecisionEvidenceRepository evidence;
    private final ResourcePermissionAuthorityPort permissions;
    private final ResourceAuthorizationCachePort cache;
    private final ResourceScopeMatcher scopes;
    private final ResourceSecurityStatePolicy security;
    private final Optional<ResourceScopeShareRepositoryPort> scopeShares;
    private final Optional<LegacyAuthorizationPort> legacy;
    private final ResourceAccessEnforcementMode enforcementMode;
    private final Duration allowTtl;
    private final Duration denyTtl;
    private final Clock clock;

    public DefaultResourceAuthorizationService(
            AuthoritativeResourceDescriptorService descriptors,
            ResourceDecisionEvidenceRepository evidence,
            ResourcePermissionAuthorityPort permissions,
            ResourceAuthorizationCachePort cache,
            Optional<LegacyAuthorizationPort> legacy,
            ResourceAccessEnforcementMode enforcementMode,
            Duration allowTtl,
            Duration denyTtl,
            Clock clock) {
        this(descriptors, evidence, permissions, cache, legacy, Optional.empty(), enforcementMode, allowTtl, denyTtl, clock);
    }

    /** RS1 constructor including permission-neutral Resource Scope Share evidence. */
    public DefaultResourceAuthorizationService(
            AuthoritativeResourceDescriptorService descriptors,
            ResourceDecisionEvidenceRepository evidence,
            ResourcePermissionAuthorityPort permissions,
            ResourceAuthorizationCachePort cache,
            Optional<LegacyAuthorizationPort> legacy,
            Optional<ResourceScopeShareRepositoryPort> scopeShares,
            ResourceAccessEnforcementMode enforcementMode,
            Duration allowTtl,
            Duration denyTtl,
            Clock clock) {
        this.descriptors = Objects.requireNonNull(descriptors, "descriptors");
        this.evidence = Objects.requireNonNull(evidence, "evidence");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.scopes = new ResourceScopeMatcher(evidence);
        this.security = new ResourceSecurityStatePolicy();
        this.legacy = legacy == null ? Optional.empty() : legacy;
        this.scopeShares = scopeShares == null ? Optional.empty() : scopeShares;
        this.enforcementMode = enforcementMode == null ? ResourceAccessEnforcementMode.OFF : enforcementMode;
        this.allowTtl = positive(allowTtl, Duration.ofSeconds(30));
        this.denyTtl = positive(denyTtl, Duration.ofSeconds(5));
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public AuthorizationDecision evaluate(AuthorizationRequest request) {
        AuthorizationDecisionMode mode = switch (enforcementMode) {
            case READ_ENFORCE, WRITE_ENFORCE, FULL_ENFORCE -> AuthorizationDecisionMode.FORMAL;
            case OFF, SHADOW -> AuthorizationDecisionMode.SHADOW;
        };
        return evaluateInternal(request, mode);
    }

    @Override
    public AuthorizationDecision explain(AuthorizationRequest request) {
        return evaluateInternal(request, AuthorizationDecisionMode.EXPLAIN);
    }

    @Override
    public AuthorizationDecision simulate(AuthorizationSimulationRequest simulation) {
        Objects.requireNonNull(simulation, "simulation");
        AuthenticationContext actor = simulation.actorAuthentication();
        // This synthetic context is evaluation evidence only. SIMULATION decisions are always non-executable.
        AuthenticationContext targetContext = new AuthenticationContext(
                actor.subject(),
                simulation.targetPrincipal(),
                simulation.activeTenant(),
                actor.session(),
                actor.assurance(),
                actor.securityEpoch(),
                actor.externalIssuer(),
                actor.issuedAt(),
                actor.expiresAt());
        Map<String, String> trustedFlow = new java.util.LinkedHashMap<>(simulation.trustedFlowContext());
        trustedFlow.put("simulationActorPrincipalId", actor.principal().principalId());
        AuthorizationRequest request = new AuthorizationRequest(
                simulation.targetPrincipal(),
                targetContext,
                simulation.activeTenant(),
                simulation.action(),
                simulation.resourceRef(),
                simulation.requestedVisibility(),
                RequestChannel.INTERNAL_PORT,
                simulation.purpose(),
                OperationPhase.START,
                "",
                simulation.correlationId(),
                simulation.presentedEpoch(),
                Map.copyOf(trustedFlow));
        return evaluateInternal(request, AuthorizationDecisionMode.SIMULATION);
    }

    private AuthorizationDecision evaluateInternal(AuthorizationRequest request, AuthorizationDecisionMode mode) {
        Objects.requireNonNull(request, "request");
        Instant evaluatedAt = clock.instant();
        ResourceDescriptor descriptor;
        try {
            descriptor = descriptors.resolve(
                    request.resourceRef(),
                    new DescriptorResolutionContext(request.correlationId(), "resource-access-core", evaluatedAt));
        } catch (RuntimeException exception) {
            AuthorizationDecision unavailable = decision(
                    request,
                    mode,
                    DecisionEffect.DENY,
                    VisibilityLevel.NONE,
                    Set.of(),
                    Set.of(),
                    Set.of(),
                    Set.of(),
                    Set.of(),
                    Set.of(),
                    PolicyVersion.ZERO,
                    SecurityEpoch.ZERO,
                    "",
                    List.of(reason(
                            ResourceDecisionReasonCodes.RESOURCE_DESCRIPTOR_NOT_FOUND,
                            DecisionReason.Category.SYSTEM,
                            "Resource descriptor is unavailable",
                            Set.of())),
                    evaluatedAt,
                    nonExecutable(mode),
                    false,
                    Duration.ZERO);
            return finish(request, unavailable, "", evaluatedAt);
        }

        PrincipalScopeSnapshot principalScope = evidence.resolvePrincipalScope(
                request.resourceRef().tenantId(), request.principal(), evaluatedAt);
        PolicyVersion policyVersion = evidence.currentPolicyVersion(request.resourceRef().tenantId());
        SecurityEpoch currentEpoch = evidence.currentSecurityEpoch(
                request.resourceRef().tenantId(), request.principal(), request.resourceRef());

        boolean runtimeCheckpoint = request.operationPhase() != OperationPhase.START
                && request.operationPhase() != OperationPhase.COMPLETE;
        if (runtimeCheckpoint && !request.presentedEpoch().isAtLeast(currentEpoch)) {
            return finish(
                    request,
                    deny(
                            request,
                            mode,
                            descriptor,
                            policyVersion,
                            currentEpoch,
                            ResourceDecisionReasonCodes.RESOURCE_SECURITY_EPOCH_STALE,
                            DecisionReason.Category.RUNTIME,
                            "Runtime authorization epoch is stale",
                            evaluatedAt),
                    descriptor.descriptorHash(),
                    evaluatedAt);
        }

        ResourceAuthorizationCacheKey cacheKey = cacheKey(request, policyVersion, currentEpoch);
        if (mode == AuthorizationDecisionMode.FORMAL && request.operationPhase() == OperationPhase.START) {
            Optional<AuthorizationDecision> cached = cache.get(cacheKey, evaluatedAt);
            if (cached.isPresent()) {
                AuthorizationDecision cacheHit = copyCacheHit(cached.get(), evaluatedAt);
                return finish(request, cacheHit, descriptor.descriptorHash(), evaluatedAt);
            }
            return cache.coordinate(cacheKey, () -> {
                Optional<AuthorizationDecision> followerCacheHit = cache.get(cacheKey, clock.instant());
                if (followerCacheHit.isPresent()) {
                    AuthorizationDecision cacheHit = copyCacheHit(followerCacheHit.get(), clock.instant());
                    return finish(request, cacheHit, descriptor.descriptorHash(), clock.instant());
                }
                return evaluateResolved(request, mode, descriptor, principalScope, policyVersion, currentEpoch, cacheKey, evaluatedAt);
            });
        }
        return evaluateResolved(request, mode, descriptor, principalScope, policyVersion, currentEpoch, cacheKey, evaluatedAt);
    }

    private AuthorizationDecision evaluateResolved(
            AuthorizationRequest request,
            AuthorizationDecisionMode mode,
            ResourceDescriptor descriptor,
            PrincipalScopeSnapshot principalScope,
            PolicyVersion policyVersion,
            SecurityEpoch currentEpoch,
            ResourceAuthorizationCacheKey cacheKey,
            Instant evaluatedAt) {
        Optional<String> stateBlock = security.blockingReason(
                descriptor.securityState(), request.action(), request.trustedFlowContext());
        if (stateBlock.isPresent()) {
            return finish(
                    request,
                    deny(
                            request,
                            mode,
                            descriptor,
                            policyVersion,
                            currentEpoch,
                            stateBlock.get(),
                            DecisionReason.Category.SECURITY_STATE,
                            "Resource security state blocks the operation",
                            evaluatedAt),
                    descriptor.descriptorHash(),
                    evaluatedAt);
        }

        Set<PolicyPrincipalRef> policyPrincipals = policyPrincipals(request.principal(), principalScope);
        List<ResourceParticipantProjection> participants = evidence.findEffectiveParticipants(
                descriptor.resourceRef(), evaluatedAt);

        List<ExplicitDenyRecord> denies = evidence.findEffectiveExplicitDenies(
                        descriptor.resourceRef().tenantId(),
                        policyPrincipals,
                        request.action().permissionCode(),
                        descriptor.resourceRef().resourceType(),
                        evaluatedAt)
                .stream()
                .filter(deny -> scopes.matches(
                        deny.scopeType(),
                        deny.scopeRefId(),
                        descriptor,
                        participants,
                        request.principal(),
                        principalScope,
                        evaluatedAt))
                .toList();
        if (!denies.isEmpty()) {
            Set<String> denyIds = ids(denies, ExplicitDenyRecord::denyId);
            AuthorizationDecision denied = decision(
                    request,
                    mode,
                    DecisionEffect.DENY,
                    VisibilityLevel.NONE,
                    Set.of(),
                    Set.of(),
                    Set.of(),
                    Set.of(),
                    denyIds,
                    Set.of("EXPLICIT_DENY"),
                    policyVersion,
                    currentEpoch,
                    descriptor.descriptorHash(),
                    List.of(reason(
                            ResourceDecisionReasonCodes.EXPLICIT_DENY_MATCHED,
                            DecisionReason.Category.DENY,
                            "An explicit deny matched",
                            denyIds)),
                    evaluatedAt,
                    nonExecutable(mode),
                    false,
                    Duration.ZERO);
            return finish(request, denied, descriptor.descriptorHash(), evaluatedAt);
        }

        ResourcePermissionScopeDecision permission = permissions.resolveEffectiveScopes(request, principalScope);
        ResourcePermissionDecision permissionEvidence = pointEvidence(permission);
        if (!permission.granted()) {
            return finish(
                    request,
                    denyWithPermissionEvidence(
                            request,
                            mode,
                            descriptor,
                            policyVersion,
                            currentEpoch,
                            permissionEvidence,
                            ResourceDecisionReasonCodes.PERMISSION_NOT_GRANTED,
                            DecisionReason.Category.PERMISSION,
                            "Required permission is not granted",
                            evaluatedAt),
                    descriptor.descriptorHash(),
                    evaluatedAt);
        }

        List<ScopeGrantRecord> grants = evidence.findEffectiveScopeGrants(
                        descriptor.resourceRef().tenantId(),
                        policyPrincipals,
                        request.action().permissionCode(),
                        descriptor.resourceRef().resourceType(),
                        evaluatedAt)
                .stream()
                .filter(grant -> scopes.matches(
                        grant.scopeType(),
                        grant.scopeRefId(),
                        descriptor,
                        participants,
                        request.principal(),
                        principalScope,
                        evaluatedAt))
                .toList();
        List<ResourceParticipantProjection> matchedParticipants = scopes.matchingParticipants(
                participants,
                request.principal(),
                principalScope,
                request.action().permissionCode(),
                evaluatedAt);
        boolean ownerMatched = scopes.ownerMatch(descriptor.ownership(), request.principal(), principalScope);
        boolean descriptorOrganizationScopeMatched = permissionScopeMatches(permission, descriptor);
        VisibilityLevel participantPermissionVisibility = permissionParticipantScopeVisibility(permission, matchedParticipants, descriptor.resourceRef().tenantId());
        boolean participantOrganizationScopeMatched = participantPermissionVisibility != VisibilityLevel.NONE;
        boolean organizationScopeMatched = descriptorOrganizationScopeMatched || participantOrganizationScopeMatched;
        boolean tenantFallbackMatched = permission.tenantScoped();
        // P2.3B authority ceiling: organization membership, participant projection and ownership are evidence,
        // not permission by themselves. IAM Role Binding scope is the organization authority ceiling; only an
        // explicit Resource Access grant may intentionally create a narrower cross-boundary exception.
        boolean organizationAuthorityMatched = organizationScopeMatched || tenantFallbackMatched;
        boolean explicitResourceAuthorityMatched = grants.stream()
                .anyMatch(grant -> explicitResourceException(grant.scopeType()));
        List<PersistedResourceScopeShare> matchedShares = scopeShares
                .map(repository -> repository.findEffectiveByResource(descriptor.resourceRef(), evaluatedAt).stream()
                        .filter(share -> permissionScopeMatchesShare(permission, share.share().target(), descriptor.resourceRef().tenantId()))
                        .toList())
                .orElse(List.of());
        boolean scopeShareMatched = !matchedShares.isEmpty();
        boolean scopeAllowed = organizationAuthorityMatched || explicitResourceAuthorityMatched || scopeShareMatched;
        if (!scopeAllowed) {
            return finish(
                    request,
                    denyWithPermissionEvidence(
                            request,
                            mode,
                            descriptor,
                            policyVersion,
                            currentEpoch,
                            permissionEvidence,
                            ResourceDecisionReasonCodes.RESOURCE_SCOPE_NOT_MATCHED,
                            DecisionReason.Category.SCOPE,
                            "No IAM organization scope or explicit Resource Access grant matched",
                            evaluatedAt),
                    descriptor.descriptorHash(),
                    evaluatedAt);
        }

        SensitivityLevel clearance = evidence.findEffectiveClearance(
                        descriptor.resourceRef().tenantId(),
                        directPolicyPrincipal(request.principal()),
                        evaluatedAt)
                .map(PrincipalClearanceRecord::clearanceLevel)
                .orElse(SensitivityLevel.INTERNAL);
        if (!ResourcePolicyScale.clearanceCovers(clearance, descriptor.visibility().sensitivityLevel())) {
            return finish(
                    request,
                    denyWithPermissionEvidence(
                            request,
                            mode,
                            descriptor,
                            policyVersion,
                            currentEpoch,
                            permissionEvidence,
                            ResourceDecisionReasonCodes.SENSITIVITY_CLEARANCE_INSUFFICIENT,
                            DecisionReason.Category.VISIBILITY,
                            "Principal sensitivity clearance is insufficient",
                            evaluatedAt),
                    descriptor.descriptorHash(),
                    evaluatedAt);
        }

        VisibilityLevel effectiveCap = descriptor.visibility().maximumVisibility();
        Optional<VisibilityPolicyRecord> visibilityPolicy = evidence.findActiveVisibilityPolicy(
                descriptor.resourceRef().tenantId(), descriptor.resourceRef().resourceType());
        if (visibilityPolicy.isPresent()) {
            effectiveCap = ResourcePolicyScale.lowerVisibility(
                    effectiveCap, visibilityPolicy.get().maximumVisibility());
            if (!ResourcePolicyScale.clearanceCovers(
                    visibilityPolicy.get().maximumSensitivity(),
                    descriptor.visibility().sensitivityLevel())) {
                return finish(
                        request,
                        denyWithPermissionEvidence(
                                request,
                                mode,
                                descriptor,
                                policyVersion,
                                currentEpoch,
                                permissionEvidence,
                                ResourceDecisionReasonCodes.VISIBILITY_LEVEL_INSUFFICIENT,
                                DecisionReason.Category.VISIBILITY,
                                "Visibility policy does not cover the resource sensitivity",
                                evaluatedAt),
                        descriptor.descriptorHash(),
                        evaluatedAt);
            }
        }

        VisibilityLevel sourceCap = VisibilityLevel.NONE;
        for (ScopeGrantRecord grant : grants) {
            sourceCap = ResourcePolicyScale.higherVisibility(sourceCap, grant.visibilityLevel());
        }
        // Participant/owner evidence must not raise visibility above the IAM Role Binding scope.
        // Explicit grants carry their own visibility cap above; tenant/organization bindings use the permission cap.
        if (tenantFallbackMatched) {
            sourceCap = ResourcePolicyScale.higherVisibility(
                    sourceCap, descriptor.visibility().maximumVisibility());
        }
        if (organizationScopeMatched) {
            sourceCap = ResourcePolicyScale.higherVisibility(
                    sourceCap, ResourcePolicyScale.higherVisibility(
                            permissionScopeVisibility(permission, descriptor), participantPermissionVisibility));
        }
        if (scopeShareMatched) {
            sourceCap = ResourcePolicyScale.higherVisibility(sourceCap, descriptor.visibility().maximumVisibility());
        }
        effectiveCap = ResourcePolicyScale.lowerVisibility(effectiveCap, sourceCap);
        if (!ResourcePolicyScale.visibilityCovers(effectiveCap, request.requestedVisibility())) {
            return finish(
                    request,
                    denyWithPermissionEvidence(
                            request,
                            mode,
                            descriptor,
                            policyVersion,
                            currentEpoch,
                            permissionEvidence,
                            ResourceDecisionReasonCodes.VISIBILITY_LEVEL_INSUFFICIENT,
                            DecisionReason.Category.VISIBILITY,
                            "Requested visibility exceeds the effective cap",
                            evaluatedAt),
                    descriptor.descriptorHash(),
                    evaluatedAt);
        }

        Set<String> scopeSources = new LinkedHashSet<>();
        if (!grants.isEmpty()) scopeSources.add("EXPLICIT_GRANT");
        if (!matchedParticipants.isEmpty()) scopeSources.add("PARTICIPANT");
        if (ownerMatched) scopeSources.add("OWNERSHIP");
        if (organizationScopeMatched) scopeSources.add("ORGANIZATION_SCOPE");
        if (tenantFallbackMatched) scopeSources.add("TENANT_ROLE_FALLBACK");
        if (scopeShareMatched) scopeSources.add("RESOURCE_SCOPE_SHARE");
        Set<String> ownershipEvidence = ownerMatched
                ? ownershipEvidence(descriptor, request.principal(), principalScope)
                : Set.of();

        List<DecisionReason> reasons = new ArrayList<>();
        reasons.add(reason(
                ResourceDecisionReasonCodes.AUTHORIZATION_ALLOWED,
                DecisionReason.Category.SYSTEM,
                "Authorization requirements passed",
                scopeSources));
        if (mode == AuthorizationDecisionMode.SIMULATION) {
            reasons.add(reason(
                    ResourceDecisionReasonCodes.SIMULATION_ONLY,
                    DecisionReason.Category.SYSTEM,
                    "Simulation is not an execution token",
                    Set.of()));
        }
        if (mode == AuthorizationDecisionMode.SHADOW) {
            reasons.add(reason(
                    ResourceDecisionReasonCodes.SHADOW_ONLY,
                    DecisionReason.Category.SYSTEM,
                    "Shadow result does not enforce business access",
                    Set.of()));
        }

        Duration cacheTtl = ttl(mode, true);
        if (scopeShareMatched) {
            cacheTtl = capToScopeShareExpiry(cacheTtl, matchedShares, evaluatedAt);
        }
        boolean cacheable = cacheable(mode, true, descriptor, request.action()) && !cacheTtl.isZero();
        AuthorizationDecision result = decision(
                request,
                mode,
                DecisionEffect.ALLOW,
                effectiveCap,
                permission.matchedBindingIds(),
                ids(grants, ScopeGrantRecord::grantId),
                ids(matchedParticipants, ResourceParticipantProjection::participantId),
                ownershipEvidence,
                Set.of(),
                scopeSources,
                policyVersion,
                currentEpoch,
                descriptor.descriptorHash(),
                reasons,
                evaluatedAt,
                nonExecutable(mode),
                cacheable,
                cacheTtl);
        result = finish(request, result, descriptor.descriptorHash(), evaluatedAt);
        if (cacheable) {
            cache.put(cacheKey, result, evaluatedAt.plus(cacheTtl));
        }
        return result;
    }

    private AuthorizationDecision copyCacheHit(AuthorizationDecision cached, Instant evaluatedAt) {
        List<DecisionReason> reasons = new ArrayList<>(cached.reasons());
        reasons.add(reason(
                ResourceDecisionReasonCodes.AUTHORIZATION_CACHE_HIT,
                DecisionReason.Category.SYSTEM,
                "Authorization reused from the current epoch namespace",
                Set.of()));
        return new AuthorizationDecision(
                UUID.randomUUID().toString(),
                cached.effect(),
                AuthorizationDecisionMode.FORMAL,
                cached.permissionCode(),
                cached.resourceRef(),
                cached.grantedVisibility(),
                cached.matchedRoleBindingIds(),
                cached.matchedScopeGrantIds(),
                cached.matchedParticipantIds(),
                cached.matchedOwnershipEvidence(),
                cached.matchedDenyIds(),
                cached.matchedScopeSources(),
                cached.policyVersion(),
                cached.securityEpoch(),
                cached.descriptorHash(),
                cached.runtimeLeaseId(),
                reasons,
                evaluatedAt,
                false,
                cached.cacheable(),
                cached.cacheTtl());
    }

    private void compareShadow(AuthorizationRequest request, AuthorizationDecision result, Instant comparedAt) {
        LegacyAuthorizationDecision legacyDecision = legacy
                .map(port -> {
                    try {
                        return port.evaluate(request);
                    } catch (RuntimeException exception) {
                        return new LegacyAuthorizationDecision(
                                LegacyAuthorizationDecision.Effect.ERROR,
                                "LEGACY_AUTHORIZATION_ERROR",
                                "");
                    }
                })
                .orElse(new LegacyAuthorizationDecision(
                        LegacyAuthorizationDecision.Effect.NOT_AVAILABLE,
                        "LEGACY_AUTHORIZATION_NOT_AVAILABLE",
                        ""));
        ShadowMismatchCategory category = switch (legacyDecision.effect()) {
            case ALLOW -> result.effect() == DecisionEffect.ALLOW
                    ? ShadowMismatchCategory.MATCH_ALLOW
                    : ShadowMismatchCategory.LEGACY_ALLOW_RESOURCE_DENY;
            case DENY -> result.effect() == DecisionEffect.ALLOW
                    ? ShadowMismatchCategory.LEGACY_DENY_RESOURCE_ALLOW
                    : ShadowMismatchCategory.MATCH_DENY;
            case NOT_AVAILABLE -> ShadowMismatchCategory.LEGACY_NOT_AVAILABLE;
            case ERROR -> ShadowMismatchCategory.LEGACY_ERROR;
        };
        evidence.appendShadowComparison(new ShadowDecisionComparison(
                UUID.randomUUID().toString(),
                request.resourceRef().tenantId(),
                request.resourceRef(),
                request.action().permissionCode(),
                legacyDecision,
                result,
                category,
                request.correlationId(),
                comparedAt));
        ShadowDecisionEvidenceV2 legacyEvidence = legacy.filter(LegacyAuthorizationPortV2.class::isInstance)
                .map(LegacyAuthorizationPortV2.class::cast)
                .map(port -> port.evaluateEvidence(request))
                .orElseGet(() -> new ShadowDecisionEvidenceV2(
                        legacyDecision.effect().name(), Set.of(), VisibilityLevel.NONE, legacyDecision.reasonCode(),
                        false, legacyDecision.effect() == LegacyAuthorizationDecision.Effect.ERROR ? legacyDecision.reasonCode() : "",
                        legacyDecision.decisionId()));
        String targetReason = result.reasons().isEmpty() ? "" : result.reasons().getFirst().code();
        ShadowDecisionEvidenceV2 targetEvidence = new ShadowDecisionEvidenceV2(
                result.effect().name(), result.matchedScopeSources(), result.grantedVisibility(), targetReason,
                !result.descriptorHash().isBlank(), result.effect() == DecisionEffect.ERROR ? targetReason : "", result.decisionId());
        ShadowDecisionComparatorV2 comparator = new ShadowDecisionComparatorV2();
        ShadowMismatchCategoryV2 categoryV2 = comparator.compare(legacyEvidence, targetEvidence);
        evidence.appendShadowComparisonV2(new ShadowDecisionComparisonV2(
                UUID.randomUUID().toString(), request.resourceRef().tenantId(), domain(request.resourceRef().resourceType().name()),
                request.trustedFlowContext().getOrDefault("entryPointId", ""), request.resourceRef(), request.action().permissionCode(), legacyEvidence, targetEvidence, categoryV2,
                comparator.severity(categoryV2), request.correlationId(), comparedAt));
    }

    private AuthorizationDecision finish(
            AuthorizationRequest request,
            AuthorizationDecision decision,
            String descriptorHash,
            Instant evaluatedAt) {
        evidence.appendDecision(new AuthorizationDecisionAuditRecord(request, decision, descriptorHash));
        if (decision.mode() == AuthorizationDecisionMode.SHADOW) {
            compareShadow(request, decision, evaluatedAt);
        }
        return decision;
    }

    private AuthorizationDecision deny(
            AuthorizationRequest request,
            AuthorizationDecisionMode mode,
            ResourceDescriptor descriptor,
            PolicyVersion policyVersion,
            SecurityEpoch securityEpoch,
            String code,
            DecisionReason.Category category,
            String safeMessage,
            Instant evaluatedAt) {
        return decision(
                request,
                mode,
                DecisionEffect.DENY,
                VisibilityLevel.NONE,
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                policyVersion,
                securityEpoch,
                descriptor.descriptorHash(),
                List.of(reason(code, category, safeMessage, Set.of())),
                evaluatedAt,
                nonExecutable(mode),
                false,
                Duration.ZERO);
    }

    private AuthorizationDecision denyWithPermissionEvidence(
            AuthorizationRequest request,
            AuthorizationDecisionMode mode,
            ResourceDescriptor descriptor,
            PolicyVersion policyVersion,
            SecurityEpoch securityEpoch,
            ResourcePermissionDecision permission,
            String code,
            DecisionReason.Category category,
            String safeMessage,
            Instant evaluatedAt) {
        return decision(
                request,
                mode,
                DecisionEffect.DENY,
                VisibilityLevel.NONE,
                permission.matchedBindingIds(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                policyVersion,
                securityEpoch,
                descriptor.descriptorHash(),
                List.of(reason(code, category, safeMessage, permission.matchedRoleIds())),
                evaluatedAt,
                nonExecutable(mode),
                false,
                Duration.ZERO);
    }

    private AuthorizationDecision decision(
            AuthorizationRequest request,
            AuthorizationDecisionMode mode,
            DecisionEffect effect,
            VisibilityLevel visibility,
            Set<String> roleBindings,
            Set<String> grants,
            Set<String> participants,
            Set<String> ownership,
            Set<String> denies,
            Set<String> scopeSources,
            PolicyVersion policyVersion,
            SecurityEpoch securityEpoch,
            String descriptorHash,
            List<DecisionReason> reasons,
            Instant evaluatedAt,
            boolean nonExecutable,
            boolean cacheable,
            Duration cacheTtl) {
        return new AuthorizationDecision(
                UUID.randomUUID().toString(),
                effect,
                mode,
                request.action().permissionCode(),
                request.resourceRef(),
                visibility,
                roleBindings,
                grants,
                participants,
                ownership,
                denies,
                scopeSources,
                policyVersion,
                securityEpoch,
                descriptorHash,
                request.authorizationLeaseId(),
                reasons,
                evaluatedAt,
                nonExecutable,
                cacheable,
                cacheable ? cacheTtl : Duration.ZERO);
    }

    private static Duration capToScopeShareExpiry(
            Duration configuredTtl, List<PersistedResourceScopeShare> shares, Instant evaluatedAt) {
        Duration result = configuredTtl;
        for (PersistedResourceScopeShare record : shares) {
            Instant expiresAt = record.share().expiresAt();
            if (expiresAt == null) continue;
            Duration remaining = Duration.between(evaluatedAt, expiresAt);
            if (remaining.isNegative() || remaining.isZero()) return Duration.ZERO;
            if (remaining.compareTo(result) < 0) result = remaining;
        }
        return result;
    }

    private static boolean explicitResourceException(ScopeType type) {
        return type == ScopeType.RESOURCE || type == ScopeType.RESOURCE_TREE || type == ScopeType.TASK_CHAIN;
    }

    private ResourcePermissionDecision pointEvidence(ResourcePermissionScopeDecision permission) {
        String scopeType = permission.tenantScoped() ? "TENANT" : "MULTI";
        return new ResourcePermissionDecision(
                permission.granted(),
                permission.reasonCode(),
                permission.matchedBindingIds(),
                permission.matchedRoleIds(),
                scopeType,
                "");
    }

    private VisibilityLevel permissionParticipantScopeVisibility(
            ResourcePermissionScopeDecision permission, List<ResourceParticipantProjection> participants, String tenantId) {
        if (permission.tenantScoped() || participants == null || participants.isEmpty()) {
            return VisibilityLevel.NONE;
        }
        VisibilityLevel visibility = VisibilityLevel.NONE;
        for (ResourceParticipantProjection participant : participants) {
            boolean matches = false;
            if (participant.participantType() == ResourceParticipantType.AGENT_ASSIGNMENT) {
                // RS4: matchingParticipants has already proven exact AGENT_SERVICE principal identity.
                // The permission authority must separately validate the governed Agent profile/task scope.
                matches = true;
            } else if (participant.participantType() == ResourceParticipantType.GROUP) {
                matches = permission.groupIds().contains(participant.participantRefId());
            } else if (participant.participantType() == ResourceParticipantType.DEPARTMENT) {
                String departmentId = participant.participantRefId();
                matches = permission.exactDepartmentIds().contains(departmentId)
                        || permission.subtreeDepartmentRootIds().stream()
                                .anyMatch(root -> departmentScopeMatches(tenantId, root, departmentId, true));
            }
            if (matches) {
                visibility = ResourcePolicyScale.higherVisibility(visibility, participant.visibilityLevel());
            }
        }
        return visibility;
    }

    private VisibilityLevel permissionScopeVisibility(
            ResourcePermissionScopeDecision permission, ResourceDescriptor descriptor) {
        if (permission.tenantScoped()) {
            return descriptor.visibility().maximumVisibility();
        }
        OwnershipDescriptor ownership = descriptor.ownership();
        if (!ownership.ownerGroupId().isBlank() && permission.groupIds().contains(ownership.ownerGroupId())) {
            return descriptor.visibility().maximumVisibility();
        }

        String tenantId = descriptor.resourceRef().tenantId();
        VisibilityLevel visibility = VisibilityLevel.NONE;
        for (String departmentId : permission.exactDepartmentIds()) {
            visibility = ResourcePolicyScale.higherVisibility(
                    visibility, departmentVisibilityForScope(descriptor, tenantId, departmentId, false));
        }
        for (String rootDepartmentId : permission.subtreeDepartmentRootIds()) {
            visibility = ResourcePolicyScale.higherVisibility(
                    visibility, departmentVisibilityForScope(descriptor, tenantId, rootDepartmentId, true));
        }
        return visibility;
    }

    private VisibilityLevel departmentVisibilityForScope(
            ResourceDescriptor descriptor, String tenantId, String departmentScopeId, boolean subtree) {
        OwnershipDescriptor ownership = descriptor.ownership();
        if (departmentScopeMatches(tenantId, departmentScopeId, ownership.ownerDepartmentId(), subtree)) {
            return descriptor.visibility().maximumVisibility();
        }
        if (departmentScopeMatches(tenantId, departmentScopeId, ownership.executorDepartmentId(), subtree)) {
            return ResourcePolicyScale.lowerVisibility(
                    descriptor.visibility().maximumVisibility(), VisibilityLevel.SENSITIVE);
        }
        if (departmentScopeMatches(tenantId, departmentScopeId, ownership.requesterDepartmentId(), subtree)) {
            return ResourcePolicyScale.lowerVisibility(
                    descriptor.visibility().maximumVisibility(), VisibilityLevel.SUMMARY);
        }
        return VisibilityLevel.NONE;
    }

    private boolean permissionScopeMatches(
            ResourcePermissionScopeDecision permission, ResourceDescriptor descriptor) {
        return permission.tenantScoped() || permissionScopeVisibility(permission, descriptor) != VisibilityLevel.NONE;
    }

    private boolean permissionScopeMatchesShare(
            ResourcePermissionScopeDecision permission, ResourceScopeShareTarget target, String tenantId) {
        if (permission.tenantScoped()) return true;
        if (!tenantId.equals(target.tenantId())) return false;
        return switch (target.scopeType()) {
            case GROUP -> permission.groupIds().contains(target.scopeId());
            case DEPARTMENT -> permission.exactDepartmentIds().contains(target.scopeId())
                    || permission.subtreeDepartmentRootIds().stream()
                            .anyMatch(root -> departmentScopeMatches(tenantId, root, target.scopeId(), true));
            case DEPARTMENT_SUBTREE -> permission.exactDepartmentIds().stream()
                            .anyMatch(department -> departmentScopeMatches(tenantId, target.scopeId(), department, true))
                    || permission.subtreeDepartmentRootIds().stream().anyMatch(root ->
                            departmentScopeMatches(tenantId, root, target.scopeId(), true)
                                    || departmentScopeMatches(tenantId, target.scopeId(), root, true));
            case INSTANCE, TENANT, RESOURCE -> false;
        };
    }

    private boolean departmentScopeMatches(String tenantId, String scopeId, String candidateDepartmentId, boolean subtree) {
        if (candidateDepartmentId == null || candidateDepartmentId.isBlank()) {
            return false;
        }
        return subtree
                ? evidence.departmentContains(tenantId, scopeId, candidateDepartmentId)
                : scopeId.equals(candidateDepartmentId);
    }

    private Set<PolicyPrincipalRef> policyPrincipals(
            PrincipalRef principal, PrincipalScopeSnapshot principalScope) {
        Set<PolicyPrincipalRef> result = new LinkedHashSet<>();
        result.add(directPolicyPrincipal(principal));
        principalScope.departmentIds().forEach(
                departmentId -> result.add(new PolicyPrincipalRef(ScopePrincipalType.DEPARTMENT, departmentId)));
        principalScope.groupIds().forEach(
                groupId -> result.add(new PolicyPrincipalRef(ScopePrincipalType.GROUP, groupId)));
        return result;
    }

    private PolicyPrincipalRef directPolicyPrincipal(PrincipalRef principal) {
        return switch (principal.principalType()) {
            case USER -> new PolicyPrincipalRef(ScopePrincipalType.USER, principal.principalId());
            case SERVICE_ACCOUNT, AGENT, A2A_AGENT, SYSTEM_SERVICE ->
                    new PolicyPrincipalRef(ScopePrincipalType.SERVICE_ACCOUNT, principal.principalId());
            case DEPARTMENT -> new PolicyPrincipalRef(ScopePrincipalType.DEPARTMENT, principal.principalId());
            case GROUP -> new PolicyPrincipalRef(ScopePrincipalType.GROUP, principal.principalId());
            case INSTANCE_ROOT -> new PolicyPrincipalRef(ScopePrincipalType.USER, principal.principalId());
        };
    }

    private Set<String> ownershipEvidence(
            ResourceDescriptor descriptor,
            PrincipalRef principal,
            PrincipalScopeSnapshot principalScope) {
        Set<String> result = new LinkedHashSet<>();
        OwnershipDescriptor ownership = descriptor.ownership();
        if (ownership.stewardUserId().equals(principal.principalId())) {
            result.add("STEWARD:" + ownership.stewardUserId());
        }
        if (ownership.custodianServiceId().equals(principal.principalId())) {
            result.add("CUSTODIAN:" + ownership.custodianServiceId());
        }
        if (principalScope.departmentIds().contains(ownership.ownerDepartmentId())) {
            result.add("OWNER_DEPARTMENT:" + ownership.ownerDepartmentId());
        }
        if (principalScope.groupIds().contains(ownership.ownerGroupId())) {
            result.add("OWNER_GROUP:" + ownership.ownerGroupId());
        }
        return result;
    }

    private ResourceAuthorizationCacheKey cacheKey(
            AuthorizationRequest request, PolicyVersion policyVersion, SecurityEpoch securityEpoch) {
        return new ResourceAuthorizationCacheKey(
                request.resourceRef().tenantId(),
                request.principal().principalType().name(),
                request.principal().principalId(),
                request.action().permissionCode(),
                request.resourceRef().resourceType(),
                request.resourceRef().resourceId(),
                request.requestedVisibility(),
                policyVersion,
                securityEpoch);
    }

    private boolean cacheable(
            AuthorizationDecisionMode mode,
            boolean allow,
            ResourceDescriptor descriptor,
            ResourceAction action) {
        return mode == AuthorizationDecisionMode.FORMAL
                && allow
                && !security.highRisk(descriptor, action);
    }

    private Duration ttl(AuthorizationDecisionMode mode, boolean allow) {
        return mode == AuthorizationDecisionMode.FORMAL
                ? (allow ? allowTtl : denyTtl)
                : Duration.ZERO;
    }

    private static boolean nonExecutable(AuthorizationDecisionMode mode) {
        return mode != AuthorizationDecisionMode.FORMAL;
    }

    private static DecisionReason reason(
            String code, DecisionReason.Category category, String safeMessage, Set<String> evidenceRefs) {
        return new DecisionReason(code, category, safeMessage, evidenceRefs);
    }

    private static <T> Set<String> ids(Collection<T> values, Function<T, String> idExtractor) {
        Set<String> result = new LinkedHashSet<>();
        for (T value : values) result.add(idExtractor.apply(value));
        return result;
    }

    private static Duration positive(Duration duration, Duration fallback) {
        return duration == null || duration.isNegative() || duration.isZero() ? fallback : duration;
    }
    private static String domain(String resourceType) {
        String value = resourceType == null ? "INTEGRATION" : resourceType.toUpperCase(java.util.Locale.ROOT);
        if (value.contains("TASK")) return "TASK";
        if (value.contains("A2A") || value.contains("HANDOFF")) return "A2A";
        if (value.contains("AGENT")) return "AGENT";
        if (value.contains("ISSUE")) return "ISSUE";
        return "INTEGRATION";
    }

}
