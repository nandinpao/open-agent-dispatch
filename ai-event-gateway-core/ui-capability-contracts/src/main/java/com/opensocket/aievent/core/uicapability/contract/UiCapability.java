package com.opensocket.aievent.core.uicapability.contract;

import com.opensocket.aievent.core.resourceaccess.contract.VisibilityLevel;

import java.util.Objects;

/** Minimum UI projection for one action. It is not executable authorization evidence. */
public record UiCapability(
        String uiActionId,
        UiDisplayMode displayMode,
        UiReasonCategory reasonCategory,
        boolean stepUpRequired,
        boolean approvalRequired,
        VisibilityLevel visibilityCeiling,
        boolean requestAccessAllowed,
        String relatedHelpId) {

    public UiCapability {
        uiActionId = requireActionId(uiActionId);
        Objects.requireNonNull(displayMode, "displayMode");
        visibilityCeiling = visibilityCeiling == null ? VisibilityLevel.NONE : visibilityCeiling;
        relatedHelpId = relatedHelpId == null ? "" : relatedHelpId.trim();

        if (displayMode == UiDisplayMode.ENABLED && reasonCategory != null) {
            throw new IllegalArgumentException("ENABLED capability must not expose a denial reason");
        }
        if (displayMode == UiDisplayMode.HIDE && reasonCategory != null) {
            throw new IllegalArgumentException("HIDE capability must not expose a reason");
        }
        if (displayMode != UiDisplayMode.ENABLED && displayMode != UiDisplayMode.HIDE && reasonCategory == null) {
            throw new IllegalArgumentException("non-enabled visible capability requires a safe reason");
        }
        if (stepUpRequired != (displayMode == UiDisplayMode.STEP_UP_REQUIRED)) {
            throw new IllegalArgumentException("stepUpRequired must match STEP_UP_REQUIRED display mode");
        }
        if (approvalRequired != (displayMode == UiDisplayMode.APPROVAL_REQUIRED)) {
            throw new IllegalArgumentException("approvalRequired must match APPROVAL_REQUIRED display mode");
        }
        if (requestAccessAllowed != (displayMode == UiDisplayMode.REQUEST_ACCESS)) {
            throw new IllegalArgumentException("requestAccessAllowed must match REQUEST_ACCESS display mode");
        }
        if (displayMode == UiDisplayMode.STEP_UP_REQUIRED && reasonCategory != UiReasonCategory.STEP_UP_REQUIRED) {
            throw new IllegalArgumentException("STEP_UP_REQUIRED display mode requires STEP_UP_REQUIRED reason");
        }
        if (displayMode == UiDisplayMode.APPROVAL_REQUIRED && reasonCategory != UiReasonCategory.APPROVAL_REQUIRED) {
            throw new IllegalArgumentException("APPROVAL_REQUIRED display mode requires APPROVAL_REQUIRED reason");
        }
    }

    private static String requireActionId(String value) {
        if (value == null || !value.matches("[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*){2,}")) {
            throw new IllegalArgumentException("uiActionId must be a canonical dotted identifier");
        }
        return value;
    }
}
