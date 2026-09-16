package com.opensocket.aievent.core.uicapability.core;

import com.opensocket.aievent.core.uicapability.contract.*;
import java.util.List;
import java.util.Objects;

/** Builds a safe first-paint contract from the existing capability projection authority adapter. */
public final class DefaultUiPageBootstrapService implements UiPageBootstrapService {
    private final UiPageCatalogResolver pages;
    private final UiCapabilityProjectionService projection;
    private final UiHydrationNoncePort nonces;

    public DefaultUiPageBootstrapService(UiPageCatalogResolver pages, UiCapabilityProjectionService projection,
                                         UiHydrationNoncePort nonces) {
        this.pages = Objects.requireNonNull(pages);
        this.projection = Objects.requireNonNull(projection);
        this.nonces = Objects.requireNonNull(nonces);
    }

    @Override public UiPageBootstrap bootstrap(UiPageBootstrapCommand command) {
        requireCompatible(command.request().contractVersion());
        UiPageDefinition page = pages.resolve(command.request().routeContext()).orElseThrow(() ->
                new UiPageBootstrapException(UiPageBootstrapException.Code.UI_PAGE_CONTEXT_NOT_FOUND,
                        "Unknown server-owned page context"));
        UiCapabilityContextRequest context = new UiCapabilityContextRequest(
                "page:" + page.routeContext(), command.request().resourceId(), command.request().resourceVersion(),
                command.request().presentedPrincipalEpoch(), page.pageActionIds());
        UiCapabilityEnvelope envelope = projection.project(new UiCapabilityProjectionCommand(
                command.request().contractVersion(), command.authentication(), context,
                command.correlationId(), command.requestedAt()));
        UiCapability view = envelope.capabilities().stream()
                .filter(cap -> cap.uiActionId().equals(page.viewActionId())).findFirst()
                .orElseThrow(() -> new IllegalStateException("PAGE_VIEW_CAPABILITY_MISSING"));
        UiPageBootstrapOutcome outcome = outcome(view.displayMode());
        String summaryRef = outcome == UiPageBootstrapOutcome.PAGE ? envelope.resourceRefHash() : "";
        String nonce = nonces.issue(envelope.tenantId(), command.authentication().principal().principalId(),
                page.routeContext(), envelope.expiresAt());
        return new UiPageBootstrap(UiCapabilityContract.VERSION, page.routeContext(),
                page.canonicalPath(command.request().resourceId()), envelope.tenantId(), envelope.principalEpoch(),
                envelope.catalogRevision(), envelope.policyVersion(), outcome, List.of(), envelope.capabilities(),
                summaryRef, envelope.resourceVersion(), envelope.expiresAt(), nonce);
    }

    static UiPageBootstrapOutcome outcome(UiDisplayMode mode) {
        return switch (mode) {
            case ENABLED, READ_ONLY -> UiPageBootstrapOutcome.PAGE;
            case STEP_UP_REQUIRED -> UiPageBootstrapOutcome.STEP_UP_SHELL;
            case REQUEST_ACCESS -> UiPageBootstrapOutcome.REQUEST_ACCESS_SHELL;
            case STALE_RELOAD -> UiPageBootstrapOutcome.SAFE_RESOURCE_CHANGED_SHELL;
            case HIDE, DISABLE_WITH_REASON, APPROVAL_REQUIRED, LOCKED_SECURITY ->
                    UiPageBootstrapOutcome.ANTI_ENUMERATION_NOT_FOUND_SHELL;
        };
    }
    private static void requireCompatible(String version) {
        String major = version == null ? "" : version.trim().split("\\.", 2)[0];
        if (!"1".equals(major)) throw new UiPageBootstrapException(
                UiPageBootstrapException.Code.UI_PAGE_CONTRACT_UNSUPPORTED, "Unsupported UI bootstrap contract");
    }
}
