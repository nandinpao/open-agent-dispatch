package com.opensocket.aievent.core.uicapability.core;

import static org.junit.jupiter.api.Assertions.*;
import com.opensocket.aievent.core.iam.security.contract.AuthenticationAssurance;
import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.core.iam.security.contract.SubjectRef;
import com.opensocket.aievent.core.iam.security.contract.TenantRef;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.uicapability.contract.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DefaultUiCapabilityProjectionServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");
    private final PrincipalRef principal = new PrincipalRef(PrincipalRef.PrincipalType.USER, "user-1");
    private final TenantRef tenant = TenantRef.tenant("tenant-a");

    @Test void allowProjectsStepUpWithoutReturningPermissionEvidence() {
        Fixture fixture = new Fixture(DecisionEffect.ALLOW, Set.of(ResourceDecisionReasonCodes.AUTHORIZATION_ALLOWED), 7, 11);
        UiCapabilityEnvelope result = fixture.service().project(command("1.0", List.of("a2a.request.cancel"), 7L, 11L));
        assertEquals(UiDisplayMode.STEP_UP_REQUIRED, result.capabilities().getFirst().displayMode());
        assertEquals(7, result.principalEpoch());
        assertFalse(result.toString().contains("matchedRole"));
        assertFalse(result.toString().contains("a2a.request.cancel" + "="));
    }

    @Test void permissionDenyProjectsRequestAccess() {
        Fixture fixture = new Fixture(DecisionEffect.DENY, Set.of(ResourceDecisionReasonCodes.PERMISSION_NOT_GRANTED), 7, 11);
        UiCapabilityEnvelope result = fixture.service().project(command("1.0", List.of("a2a.request.read"), 7L, 11L));
        assertEquals(UiDisplayMode.REQUEST_ACCESS, result.capabilities().getFirst().displayMode());
        assertEquals(UiReasonCategory.NOT_ALLOWED, result.capabilities().getFirst().reasonCategory());
    }

    @Test void unknownActionFailsClosedWithoutCallingAuthorization() {
        Fixture fixture = new Fixture(DecisionEffect.ALLOW, Set.of(ResourceDecisionReasonCodes.AUTHORIZATION_ALLOWED), 7, 11);
        UiCapabilityEnvelope result = fixture.service().project(command("1.0", List.of("unknown.action.read"), 7L, 11L));
        assertEquals(UiDisplayMode.HIDE, result.capabilities().getFirst().displayMode());
        assertEquals(0, fixture.authorizationCalls);
    }

    @Test void missingOrCrossTenantResourceIsAntiEnumerationHidden() {
        Fixture fixture = new Fixture(DecisionEffect.ALLOW, Set.of(ResourceDecisionReasonCodes.AUTHORIZATION_ALLOWED), 7, 11);
        fixture.resourcePresent = false;
        UiCapabilityEnvelope result = fixture.service().project(command("1.0", List.of("a2a.request.read"), 7L, 11L));
        assertEquals(UiDisplayMode.HIDE, result.capabilities().getFirst().displayMode());
        assertEquals(7, result.principalEpoch());
        assertEquals(0, fixture.authorizationCalls);
    }

    @Test void descriptorAuthorityFailureDisablesWithoutDowngradingSessionEpoch() {
        Fixture fixture = new Fixture(DecisionEffect.ALLOW, Set.of(ResourceDecisionReasonCodes.AUTHORIZATION_ALLOWED), 7, 11);
        fixture.descriptorUnavailable = true;
        UiCapabilityEnvelope result = fixture.service().project(command("1.0", List.of("a2a.request.read"), 7L, 11L));
        assertEquals(UiDisplayMode.DISABLE_WITH_REASON, result.capabilities().getFirst().displayMode());
        assertEquals(UiReasonCategory.TEMPORARILY_UNAVAILABLE, result.capabilities().getFirst().reasonCategory());
        assertEquals(7, result.principalEpoch());
        assertEquals(0, fixture.authorizationCalls);
    }

    @Test void staleResourceReturnsReloadWithoutCallingAuthorization() {
        Fixture fixture = new Fixture(DecisionEffect.ALLOW, Set.of(ResourceDecisionReasonCodes.AUTHORIZATION_ALLOWED), 7, 12);
        UiCapabilityEnvelope result = fixture.service().project(command("1.0", List.of("a2a.request.read"), 7L, 11L));
        assertEquals(UiDisplayMode.STALE_RELOAD, result.capabilities().getFirst().displayMode());
        assertEquals(0, fixture.authorizationCalls);
    }

    @Test void staleAuthenticatedEpochReturnsReloadWithoutCallingAuthorization() {
        Fixture fixture = new Fixture(DecisionEffect.ALLOW, Set.of(ResourceDecisionReasonCodes.AUTHORIZATION_ALLOWED), 8, 11);
        UiCapabilityEnvelope result = fixture.service().project(command("1.0", List.of("a2a.request.read"), 7L, 11L));
        assertEquals(UiDisplayMode.STALE_RELOAD, result.capabilities().getFirst().displayMode());
        assertEquals(8, result.principalEpoch());
        assertEquals(0, fixture.authorizationCalls);
    }

    @Test void stalePresentedPrincipalEpochFailsClosed() {
        Fixture fixture = new Fixture(DecisionEffect.ALLOW, Set.of(ResourceDecisionReasonCodes.AUTHORIZATION_ALLOWED), 7, 11);
        UiCapabilityProjectionException error = assertThrows(UiCapabilityProjectionException.class,
                () -> fixture.service().project(command("1.0", List.of("a2a.request.read"), 6L, 11L)));
        assertEquals(UiCapabilityProjectionException.Code.UI_CAPABILITY_PRINCIPAL_EPOCH_STALE, error.code());
    }

    @Test void unknownContractMajorFailsClosed() {
        Fixture fixture = new Fixture(DecisionEffect.ALLOW, Set.of(ResourceDecisionReasonCodes.AUTHORIZATION_ALLOWED), 7, 11);
        UiCapabilityProjectionException error = assertThrows(UiCapabilityProjectionException.class,
                () -> fixture.service().project(command("2.0", List.of("a2a.request.read"), 7L, 11L)));
        assertEquals(UiCapabilityProjectionException.Code.UI_CAPABILITY_CONTRACT_UNSUPPORTED, error.code());
    }

    @Test void mixedResourceTypesFailBeforeDescriptorOrAuthorization() {
        Fixture fixture = new Fixture(DecisionEffect.ALLOW, Set.of(ResourceDecisionReasonCodes.AUTHORIZATION_ALLOWED), 7, 11);
        UiCapabilityProjectionException error = assertThrows(UiCapabilityProjectionException.class,
                () -> fixture.service().project(command("1.0",
                        List.of("a2a.request.read", "task.attachment.read"), 7L, 11L)));
        assertEquals(UiCapabilityProjectionException.Code.UI_CAPABILITY_RESOURCE_TYPE_MISMATCH, error.code());
        assertEquals(0, fixture.authorizationCalls);
    }

    @Test void shadowAllowNeverProjectsEnabled() {
        Fixture fixture = new Fixture(DecisionEffect.ALLOW, Set.of(ResourceDecisionReasonCodes.AUTHORIZATION_ALLOWED), 7, 11);
        fixture.mode = AuthorizationDecisionMode.SHADOW;
        fixture.shadowOnly = true;
        UiCapabilityEnvelope result = fixture.service().project(command("1.0", List.of("a2a.request.read"), 7L, 11L));
        assertEquals(UiDisplayMode.DISABLE_WITH_REASON, result.capabilities().getFirst().displayMode());
        assertEquals("SHADOW", result.enforcementMode());
    }

    @Test void exactNamespaceCacheAvoidsDuplicateAuthorization() {
        Fixture fixture = new Fixture(DecisionEffect.ALLOW, Set.of(ResourceDecisionReasonCodes.AUTHORIZATION_ALLOWED), 7, 11);
        UiCapabilityProjectionService service = fixture.service();
        service.project(command("1.0", List.of("a2a.request.read"), 7L, 11L));
        service.project(command("1.0", List.of("a2a.request.read"), 7L, 11L));
        assertEquals(1, fixture.authorizationCalls);
    }

    private UiCapabilityProjectionCommand command(String version, List<String> actions, Long epoch, Long resourceVersion) {
        AuthenticationContext auth = new AuthenticationContext(new SubjectRef(SubjectRef.IdentityType.HUMAN_USER, "subject-1"),
                principal, tenant, Optional.empty(), AuthenticationAssurance.passwordOnly(NOW),
                new com.opensocket.aievent.core.iam.security.contract.SecurityEpoch(9, 5, 7), Optional.empty(),
                NOW.minusSeconds(30), NOW.plusSeconds(3600));
        return new UiCapabilityProjectionCommand(version, auth,
                new UiCapabilityContextRequest("ctx-1", "req-1", resourceVersion, epoch, actions),
                "corr-1", NOW);
    }

    private final class Fixture {
        private final DecisionEffect effect;
        private final Set<String> reasonCodes;
        private final long principalEpoch;
        private final long resourceVersion;
        int authorizationCalls;
        boolean resourcePresent = true;
        boolean descriptorUnavailable;
        AuthorizationDecisionMode mode = AuthorizationDecisionMode.FORMAL;
        boolean shadowOnly;

        Fixture(DecisionEffect effect, Set<String> reasonCodes, long principalEpoch, long resourceVersion) {
            this.effect = effect;
            this.reasonCodes = reasonCodes;
            this.principalEpoch = principalEpoch;
            this.resourceVersion = resourceVersion;
        }

        UiCapabilityProjectionService service() {
            ResourceDescriptor descriptor = new ResourceDescriptor(new ResourceRef("tenant-a", ResourceType.A2A_REQUEST, "req-1"),
                    "REQ-1", OwnershipDescriptor.unowned(1), null, null,
                    new VisibilityDescriptor(SensitivityLevel.INTERNAL, VisibilityLevel.FULL, "visibility-policy", new PolicyVersion(3, 4, "p")),
                    ResourceSecurityState.NORMAL, 1, resourceVersion, DescriptorAuthority.A2A_DOMAIN,
                    "descriptor-hash", NOW);
            UiCapabilityResourceDescriptorPort descriptors = (ref, correlationId, requestedAt) -> {
                if (descriptorUnavailable) throw new IllegalStateException("descriptor authority unavailable");
                return resourcePresent && ref.equals(descriptor.resourceRef()) ? Optional.of(descriptor) : Optional.empty();
            };
            ResourceAuthorizationPort authorization = new ResourceAuthorizationPort() {
                public AuthorizationDecision evaluate(AuthorizationRequest request) { authorizationCalls++; return decision(request); }
                public AuthorizationDecision explain(AuthorizationRequest request) { return decision(request); }
                public AuthorizationDecision simulate(AuthorizationSimulationRequest request) { throw new UnsupportedOperationException(); }
            };
            UiCapabilityAuthorityNamespacePort namespaces = (tenantId, actor, ref) ->
                    new UiCapabilityAuthorityNamespace(new PolicyVersion(3, 4, "p"),
                            new SecurityEpoch(10, 6, principalEpoch, 8, 3, 2));
            return new DefaultUiCapabilityProjectionService(authorization, descriptors, namespaces,
                    new CanonicalUiActionCatalogResolver(), new CatalogUiOperationPrerequisiteResolver(),
                    new InMemoryUiCapabilityProjectionCache(100), Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(60));
        }

        private AuthorizationDecision decision(AuthorizationRequest request) {
            List<DecisionReason> reasons = reasonCodes.stream().map(code -> new DecisionReason(code,
                    DecisionReason.Category.PERMISSION, "safe", Set.of())).toList();
            VisibilityLevel visibility = effect == DecisionEffect.ALLOW ? VisibilityLevel.FULL : VisibilityLevel.NONE;
            return new AuthorizationDecision("decision-1", effect, mode,
                    request.action().permissionCode(), request.resourceRef(), visibility, Set.of("binding"), Set.of(),
                    Set.of(), Set.of(), Set.of(), Set.of(), new PolicyVersion(3, 4, "p"),
                    new SecurityEpoch(10, 6, principalEpoch, 8, 3, 2), "descriptor-hash", "", reasons,
                    NOW, shadowOnly, effect == DecisionEffect.ALLOW, effect == DecisionEffect.ALLOW ? Duration.ofSeconds(30) : Duration.ZERO);
        }
    }
}
