package com.opensocket.aievent.core.uicapability.core;

import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.uicapability.contract.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Projects canonical Resource Access decisions. It never grants authority and never returns raw permission,
 * role, scope, deny or policy-expression evidence to the browser.
 */
public final class DefaultUiCapabilityProjectionService implements UiCapabilityProjectionService {
    private static final Duration NON_CACHEABLE_TTL = Duration.ofSeconds(5);
    private final ResourceAuthorizationPort authorization;
    private final UiCapabilityResourceDescriptorPort descriptors;
    private final UiCapabilityAuthorityNamespacePort namespaces;
    private final UiActionCatalogResolver actionCatalog;
    private final UiOperationPrerequisiteResolver prerequisites;
    private final UiCapabilityProjectionCachePort cache;
    private final Clock clock;
    private final Duration maximumTtl;

    public DefaultUiCapabilityProjectionService(
            ResourceAuthorizationPort authorization,
            UiCapabilityResourceDescriptorPort descriptors,
            UiCapabilityAuthorityNamespacePort namespaces,
            UiActionCatalogResolver actionCatalog,
            UiOperationPrerequisiteResolver prerequisites,
            UiCapabilityProjectionCachePort cache,
            Clock clock,
            Duration maximumTtl) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.descriptors = Objects.requireNonNull(descriptors, "descriptors");
        this.namespaces = Objects.requireNonNull(namespaces, "namespaces");
        this.actionCatalog = Objects.requireNonNull(actionCatalog, "actionCatalog");
        this.prerequisites = Objects.requireNonNull(prerequisites, "prerequisites");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.maximumTtl = Objects.requireNonNull(maximumTtl, "maximumTtl");
        if (maximumTtl.isZero() || maximumTtl.isNegative()) {
            throw new IllegalArgumentException("maximumTtl must be positive");
        }
    }

    @Override public UiCapabilityEnvelope project(UiCapabilityProjectionCommand command) {
        Objects.requireNonNull(command, "command");
        requireCompatibleContract(command.contractVersion());
        AuthenticationContext authentication = command.authentication();
        verifyPresentedPrincipalEpoch(authentication, command.context().presentedPrincipalEpoch());

        List<ResolvedRequestAction> requested = resolveActions(command.context().uiActionIds());
        List<ResolvedUiAction> known = requested.stream().map(ResolvedRequestAction::action)
                .filter(Objects::nonNull).toList();
        if (known.isEmpty()) return metadataOnlyEnvelope(command, requested, "FORMAL");

        ResourceType type = requireSingleResourceType(known);
        ResourceRef resourceRef = new ResourceRef(authentication.activeTenant().tenantId(), type,
                command.context().resourceId());
        ResourceDescriptor descriptor;
        try {
            Optional<ResourceDescriptor> resolved = descriptors.resolve(
                    resourceRef, command.correlationId(), command.requestedAt());
            if (resolved.isEmpty()) {
                return hiddenEnvelope(command, requested, resourceRefHash(resourceRef), null, "FORMAL");
            }
            descriptor = resolved.get();
        } catch (RuntimeException unavailable) {
            return unavailableEnvelope(command, requested, resourceRefHash(resourceRef), null, "FORMAL");
        }

        UiCapabilityAuthorityNamespace namespace;
        try {
            namespace = namespaces.current(resourceRef.tenantId(), authentication.principal(), resourceRef);
        } catch (RuntimeException unavailable) {
            return unavailableEnvelope(command, requested, descriptor.descriptorHash(),
                    descriptor.resourceVersion(), "FORMAL");
        }
        PolicyVersion policy = namespace.policyVersion();
        SecurityEpoch epoch = namespace.securityEpoch();
        if (authentication.securityEpoch().principalEpoch() < epoch.principalEpoch()) {
            return staleEnvelope(command, requested, descriptor, policy, epoch);
        }
        if (command.context().resourceVersion() != null
                && command.context().resourceVersion().longValue() != descriptor.resourceVersion()) {
            return staleEnvelope(command, requested, descriptor, policy, epoch);
        }

        UiCapabilityProjectionCacheKey cacheKey = new UiCapabilityProjectionCacheKey(
                resourceRef.tenantId(), authentication.principal().principalId(), command.context().contextId(),
                descriptor.descriptorHash(), descriptor.resourceVersion(), policy.catalogVersion(),
                policy.policyRevision(), epoch.principalEpoch(), epoch.resourceEpoch(), actionDigest(requested));
        Optional<UiCapabilityEnvelope> cached = cache.get(cacheKey, clock.instant());
        if (cached.isPresent()) return cached.get();

        List<UiCapability> capabilities = new ArrayList<>();
        List<AuthorizationDecision> decisions = new ArrayList<>();
        for (ResolvedRequestAction item : requested) {
            if (item.action() == null) {
                capabilities.add(hidden(item.uiActionId()));
                continue;
            }
            ResolvedUiAction action = item.action();
            AuthorizationDecision decision = authorization.evaluate(new AuthorizationRequest(
                    authentication.principal(), authentication, authentication.activeTenant(),
                    action.resourceAction(), resourceRef, action.definition().requestedVisibility(),
                    RequestChannel.UI, "ui-capability:" + action.definition().uiActionId(), OperationPhase.START,
                    "", command.correlationId(), epoch, Map.of("uiActionId", action.definition().uiActionId())));
            decisions.add(decision);
            capabilities.add(project(action, authentication, decision));
        }

        Duration ttl = ttl(decisions);
        Instant now = clock.instant();
        UiCapabilityEnvelope envelope = new UiCapabilityEnvelope(
                UiCapabilityContract.VERSION, command.context().contextId(), resourceRef.tenantId(),
                epoch.principalEpoch(), policy.catalogVersion(), policy.policyRevision(), descriptor.descriptorHash(),
                descriptor.resourceVersion(), enforcementMode(decisions), capabilities, now.plus(ttl),
                now.plus(refreshDelay(ttl)), "");
        cache.put(cacheKey, envelope);
        return envelope;
    }

    private UiCapability project(ResolvedUiAction action, AuthenticationContext authentication,
                                 AuthorizationDecision decision) {
        if (decision.effect() == DecisionEffect.ALLOW
                && decision.mode() == AuthorizationDecisionMode.FORMAL && !decision.shadowOnly()) {
            UiOperationPrerequisites required = prerequisites.resolve(action, authentication);
            if (required.stepUpRequired()) return new UiCapability(action.definition().uiActionId(),
                    UiDisplayMode.STEP_UP_REQUIRED, UiReasonCategory.STEP_UP_REQUIRED, true, false,
                    decision.grantedVisibility(), false, help(action));
            if (required.approvalRequired()) return new UiCapability(action.definition().uiActionId(),
                    UiDisplayMode.APPROVAL_REQUIRED, UiReasonCategory.APPROVAL_REQUIRED, false, true,
                    decision.grantedVisibility(), false, help(action));
            return new UiCapability(action.definition().uiActionId(), UiDisplayMode.ENABLED, null, false, false,
                    decision.grantedVisibility(), false, help(action));
        }
        Set<String> reasons = new LinkedHashSet<>();
        for (DecisionReason reason : decision.reasons()) reasons.add(reason.code());
        if (reasons.contains(ResourceDecisionReasonCodes.RESOURCE_DESCRIPTOR_NOT_FOUND)
                || reasons.contains(ResourceDecisionReasonCodes.RESOURCE_DELETED)) return hidden(action.definition().uiActionId());
        if (reasons.contains(ResourceDecisionReasonCodes.RESOURCE_SECURITY_EPOCH_STALE)
                || reasons.contains(ResourceDecisionReasonCodes.RESOURCE_DESCRIPTOR_STALE)
                || reasons.contains(ResourceDecisionReasonCodes.STALE_EPOCH_ALLOW_REJECTED)) {
            return state(action, UiDisplayMode.STALE_RELOAD, UiReasonCategory.RESOURCE_CHANGED);
        }
        if (reasons.contains(ResourceDecisionReasonCodes.RESOURCE_QUARANTINED)) {
            return state(action, UiDisplayMode.LOCKED_SECURITY, UiReasonCategory.RESOURCE_QUARANTINED);
        }
        if (reasons.contains(ResourceDecisionReasonCodes.EXPLICIT_DENY_MATCHED)
                || reasons.contains(ResourceDecisionReasonCodes.RESOURCE_LEGAL_HOLD_RESTRICTED)
                || reasons.contains(ResourceDecisionReasonCodes.RESOURCE_INVESTIGATION_RESTRICTED)) {
            return state(action, UiDisplayMode.LOCKED_SECURITY, UiReasonCategory.RESOURCE_LOCKED);
        }
        if (reasons.contains(ResourceDecisionReasonCodes.RESOURCE_ARCHIVED_READ_ONLY)) {
            return state(action, UiDisplayMode.READ_ONLY, UiReasonCategory.READ_ONLY_ACCESS);
        }
        if (reasons.contains(ResourceDecisionReasonCodes.RESOURCE_SCOPE_NOT_MATCHED)
                || reasons.contains(ResourceDecisionReasonCodes.VISIBILITY_LEVEL_INSUFFICIENT)
                || reasons.contains(ResourceDecisionReasonCodes.SENSITIVITY_CLEARANCE_INSUFFICIENT)) {
            return requestAccessOrDisabled(action, UiReasonCategory.OUTSIDE_SCOPE);
        }
        if (reasons.contains(ResourceDecisionReasonCodes.PERMISSION_NOT_GRANTED)) {
            return requestAccessOrDisabled(action, UiReasonCategory.NOT_ALLOWED);
        }
        if (decision.effect() == DecisionEffect.ERROR) {
            return state(action, UiDisplayMode.DISABLE_WITH_REASON, UiReasonCategory.TEMPORARILY_UNAVAILABLE);
        }
        return state(action, UiDisplayMode.DISABLE_WITH_REASON, UiReasonCategory.NOT_ALLOWED);
    }

    private UiCapability requestAccessOrDisabled(ResolvedUiAction action, UiReasonCategory reason) {
        if (!action.definition().requestAccessProfile().isBlank()
                && !"NONE".equals(action.definition().requestAccessProfile())) {
            return new UiCapability(action.definition().uiActionId(), UiDisplayMode.REQUEST_ACCESS, reason,
                    false, false, VisibilityLevel.NONE, true, help(action));
        }
        return state(action, UiDisplayMode.DISABLE_WITH_REASON, reason);
    }

    private UiCapability state(ResolvedUiAction action, UiDisplayMode mode, UiReasonCategory reason) {
        return new UiCapability(action.definition().uiActionId(), mode, reason, false, false,
                VisibilityLevel.NONE, false, help(action));
    }
    private static UiCapability hidden(String id) {
        return new UiCapability(id, UiDisplayMode.HIDE, null, false, false, VisibilityLevel.NONE, false, "");
    }
    private static String help(ResolvedUiAction action) { return "ui.help." + action.definition().uiActionId(); }

    private UiCapabilityEnvelope staleEnvelope(UiCapabilityProjectionCommand command,
            List<ResolvedRequestAction> actions, ResourceDescriptor descriptor, PolicyVersion policy, SecurityEpoch epoch) {
        List<UiCapability> caps = actions.stream().map(item -> item.action() == null ? hidden(item.uiActionId())
                : state(item.action(), UiDisplayMode.STALE_RELOAD, UiReasonCategory.RESOURCE_CHANGED)).toList();
        return shortEnvelope(command, caps, descriptor.descriptorHash(), descriptor.resourceVersion(), policy, epoch, "FORMAL");
    }
    private UiCapabilityEnvelope hiddenEnvelope(UiCapabilityProjectionCommand command,
            List<ResolvedRequestAction> actions, String hash, Long version, String mode) {
        return shortEnvelope(command, actions.stream().map(a -> hidden(a.uiActionId())).toList(), hash, version,
                PolicyVersion.ZERO, sessionSecurityEpoch(command), mode);
    }
    private UiCapabilityEnvelope unavailableEnvelope(UiCapabilityProjectionCommand command,
            List<ResolvedRequestAction> actions, String hash, Long version, String mode) {
        List<UiCapability> caps = actions.stream().map(item -> item.action() == null ? hidden(item.uiActionId())
                : state(item.action(), UiDisplayMode.DISABLE_WITH_REASON, UiReasonCategory.TEMPORARILY_UNAVAILABLE)).toList();
        return shortEnvelope(command, caps, hash, version, PolicyVersion.ZERO, sessionSecurityEpoch(command), mode);
    }
    private static SecurityEpoch sessionSecurityEpoch(UiCapabilityProjectionCommand command) {
        com.opensocket.aievent.core.iam.security.contract.SecurityEpoch sessionEpoch =
                command.authentication().securityEpoch();
        return new SecurityEpoch(sessionEpoch.globalEpoch(), sessionEpoch.tenantEpoch(),
                sessionEpoch.principalEpoch(), 0, 0, 0);
    }
    private UiCapabilityEnvelope metadataOnlyEnvelope(UiCapabilityProjectionCommand command,
            List<ResolvedRequestAction> actions, String mode) {
        return hiddenEnvelope(command, actions, hash(command.authentication().activeTenant().tenantId()
                + ":unknown:" + command.context().resourceId()), command.context().resourceVersion(), mode);
    }
    private UiCapabilityEnvelope shortEnvelope(UiCapabilityProjectionCommand command, List<UiCapability> caps,
            String hash, Long version, PolicyVersion policy, SecurityEpoch epoch, String mode) {
        Instant now = clock.instant();
        return new UiCapabilityEnvelope(UiCapabilityContract.VERSION, command.context().contextId(),
                command.authentication().activeTenant().tenantId(), epoch.principalEpoch(), policy.catalogVersion(),
                policy.policyRevision(), hash, version, mode, caps, now.plus(NON_CACHEABLE_TTL),
                now.plusSeconds(2), "");
    }

    private List<ResolvedRequestAction> resolveActions(List<String> ids) {
        List<ResolvedRequestAction> result = new ArrayList<>();
        for (String id : ids) result.add(new ResolvedRequestAction(id, actionCatalog.resolve(id).orElse(null)));
        return List.copyOf(result);
    }
    private static ResourceType requireSingleResourceType(List<ResolvedUiAction> actions) {
        ResourceType type = actions.getFirst().resourceType();
        if (actions.stream().anyMatch(action -> action.resourceType() != type)) {
            throw new UiCapabilityProjectionException(
                    UiCapabilityProjectionException.Code.UI_CAPABILITY_RESOURCE_TYPE_MISMATCH,
                    "A capability context cannot mix resource types");
        }
        return type;
    }
    private static void verifyPresentedPrincipalEpoch(AuthenticationContext authentication, Long presented) {
        if (presented != null && presented.longValue() != authentication.securityEpoch().principalEpoch()) {
            throw new UiCapabilityProjectionException(
                    UiCapabilityProjectionException.Code.UI_CAPABILITY_PRINCIPAL_EPOCH_STALE,
                    "The presented principal epoch does not match the authenticated session");
        }
    }
    private static void requireCompatibleContract(String version) {
        String major = version == null ? "" : version.trim().split("\\.", 2)[0];
        if (!"1".equals(major)) throw new UiCapabilityProjectionException(
                UiCapabilityProjectionException.Code.UI_CAPABILITY_CONTRACT_UNSUPPORTED,
                "Unsupported UI capability contract major version");
    }
    private Duration ttl(List<AuthorizationDecision> decisions) {
        Duration ttl = maximumTtl;
        for (AuthorizationDecision decision : decisions) {
            Duration candidate = decision.cacheable() && !decision.cacheTtl().isZero()
                    ? decision.cacheTtl() : NON_CACHEABLE_TTL;
            if (candidate.compareTo(ttl) < 0) ttl = candidate;
        }
        return ttl.isZero() || ttl.isNegative() ? NON_CACHEABLE_TTL : ttl;
    }
    private static Duration refreshDelay(Duration ttl) {
        long millis = Math.max(1000L, ttl.toMillis() / 2L);
        return Duration.ofMillis(Math.min(millis, Math.max(1000L, ttl.toMillis())));
    }
    private static String enforcementMode(List<AuthorizationDecision> decisions) {
        return decisions.stream().allMatch(d -> d.mode() == AuthorizationDecisionMode.FORMAL && !d.shadowOnly())
                ? "FORMAL" : "SHADOW";
    }
    private static String actionDigest(List<ResolvedRequestAction> actions) {
        return hash(String.join("\n", actions.stream().map(ResolvedRequestAction::uiActionId).toList()));
    }
    private static String resourceRefHash(ResourceRef ref) {
        return hash(ref.tenantId() + ":" + ref.resourceType().name() + ":" + ref.resourceId());
    }
    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }
    private record ResolvedRequestAction(String uiActionId, ResolvedUiAction action) { }
}
