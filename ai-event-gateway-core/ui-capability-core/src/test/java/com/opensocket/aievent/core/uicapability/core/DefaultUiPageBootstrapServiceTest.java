package com.opensocket.aievent.core.uicapability.core;

import static org.junit.jupiter.api.Assertions.*;
import com.opensocket.aievent.core.iam.security.contract.*;
import java.util.Optional;
import com.opensocket.aievent.core.resourceaccess.contract.VisibilityLevel;
import com.opensocket.aievent.core.uicapability.contract.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultUiPageBootstrapServiceTest {
    @Test void enabledViewProducesPageWithoutResourceTitle() {
        UiPageBootstrap result = service(UiDisplayMode.ENABLED).bootstrap(command());
        assertEquals(UiPageBootstrapOutcome.PAGE, result.outcome());
        assertEquals("/tasks/task-1", result.canonicalPath());
        assertEquals("opaque-ref", result.resourceSummaryRef());
        assertFalse(result.toString().contains("Sensitive Task Title"));
    }
    @Test void hiddenViewProducesAntiEnumerationShell() {
        UiPageBootstrap result = service(UiDisplayMode.HIDE).bootstrap(command());
        assertEquals(UiPageBootstrapOutcome.ANTI_ENUMERATION_NOT_FOUND_SHELL, result.outcome());
        assertEquals("", result.resourceSummaryRef());
    }
    @Test void staleViewProducesChangedShell() {
        assertEquals(UiPageBootstrapOutcome.SAFE_RESOURCE_CHANGED_SHELL, service(UiDisplayMode.STALE_RELOAD).bootstrap(command()).outcome());
    }
    @Test void unknownPageContextFailsClosed() {
        UiPageBootstrapCommand value = new UiPageBootstrapCommand(auth(), new UiPageBootstrapRequest("1.0", "unknown", "task-1", null, 1L), "corr", Instant.EPOCH);
        assertThrows(UiPageBootstrapException.class, () -> service(UiDisplayMode.ENABLED).bootstrap(value));
    }
    private static DefaultUiPageBootstrapService service(UiDisplayMode mode) {
        UiCapabilityProjectionService projection = command -> {
            UiCapability cap = capability("task.detail.view", mode);
            UiCapability update = capability("task.detail.update", mode == UiDisplayMode.ENABLED ? UiDisplayMode.DISABLE_WITH_REASON : mode);
            UiCapability participant = capability("task.participant.manage", mode == UiDisplayMode.ENABLED ? UiDisplayMode.DISABLE_WITH_REASON : mode);
            return new UiCapabilityEnvelope("1.0", command.context().contextId(), "tenant-a", 1, 2, 3,
                    "opaque-ref", 4L, "FORMAL", List.of(cap, update, participant), Instant.EPOCH.plusSeconds(60), Instant.EPOCH.plusSeconds(30), "");
        };
        return new DefaultUiPageBootstrapService(new CanonicalUiPageCatalogResolver(), projection, (t,p,r,e) -> "nonce");
    }
    private static UiCapability capability(String id, UiDisplayMode mode) {
        if (mode == UiDisplayMode.ENABLED) return new UiCapability(id, mode, null, false, false, VisibilityLevel.STANDARD, false, "");
        if (mode == UiDisplayMode.HIDE) return new UiCapability(id, mode, null, false, false, VisibilityLevel.NONE, false, "");
        UiReasonCategory reason = switch(mode) {
            case STEP_UP_REQUIRED -> UiReasonCategory.STEP_UP_REQUIRED;
            case REQUEST_ACCESS -> UiReasonCategory.NOT_ALLOWED;
            case STALE_RELOAD -> UiReasonCategory.RESOURCE_CHANGED;
            default -> UiReasonCategory.NOT_ALLOWED;
        };
        return new UiCapability(id, mode, reason, mode == UiDisplayMode.STEP_UP_REQUIRED, false, VisibilityLevel.NONE, mode == UiDisplayMode.REQUEST_ACCESS, "");
    }
    private static UiPageBootstrapCommand command() {
        return new UiPageBootstrapCommand(auth(), new UiPageBootstrapRequest("1.0", "task.detail", "task-1", null, 1L), "corr", Instant.EPOCH);
    }
    private static AuthenticationContext auth() {
        Instant now = Instant.parse("2026-08-01T00:00:00Z");
        return new AuthenticationContext(
                new SubjectRef(SubjectRef.IdentityType.HUMAN_USER, "subject-a"),
                new PrincipalRef(PrincipalRef.PrincipalType.USER, "user-a"),
                TenantRef.tenant("tenant-a"), Optional.empty(), AuthenticationAssurance.passwordOnly(now),
                new SecurityEpoch(0, 0, 1), Optional.empty(), now.minusSeconds(10), now.plusSeconds(600));
    }
}
