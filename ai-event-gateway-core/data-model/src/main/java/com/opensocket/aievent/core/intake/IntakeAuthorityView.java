package com.opensocket.aievent.core.intake;

import java.time.OffsetDateTime;

/**
 * Public, additive acknowledgement metadata for A0-R1 Intake Authority.
 * Intake disposition is intentionally separate from Task outcome.
 */
public record IntakeAuthorityView(
        String ingestionId,
        String sourceRegistrationId,
        String disposition,
        String dispositionReason,
        String idempotencyStatus,
        boolean replay,
        boolean keyExpired,
        String authenticatedPrincipalRef,
        String originPrincipalRef,
        OffsetDateTime recordedAt) {

    public IntakeAuthorityView {
        ingestionId = clean(ingestionId);
        sourceRegistrationId = clean(sourceRegistrationId);
        disposition = clean(disposition);
        dispositionReason = clean(dispositionReason);
        idempotencyStatus = clean(idempotencyStatus);
        authenticatedPrincipalRef = clean(authenticatedPrincipalRef);
        originPrincipalRef = clean(originPrincipalRef);
    }

    public static IntakeAuthorityView notEvaluated() {
        return new IntakeAuthorityView("", "", "NOT_EVALUATED", "", "NOT_EVALUATED", false, false, "", "", null);
    }

    public IntakeAuthorityView asReplay() {
        return new IntakeAuthorityView(ingestionId, sourceRegistrationId, disposition, dispositionReason,
                "IDEMPOTENT_REPLAY", true, keyExpired, authenticatedPrincipalRef, originPrincipalRef, recordedAt);
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
