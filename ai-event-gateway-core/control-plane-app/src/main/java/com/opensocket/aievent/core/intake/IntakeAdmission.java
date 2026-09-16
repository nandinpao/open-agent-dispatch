package com.opensocket.aievent.core.intake;

import com.opensocket.aievent.core.decision.EventIntakeDecisionResponse;

public record IntakeAdmission(
        String tenantId,
        String ingestionId,
        WorkloadSourceRegistrationView registration,
        IntakeAuthorityView authorityView,
        boolean proceedToLegacyMaterialization,
        EventIntakeDecisionResponse replayResponse) {

    public boolean replay() { return replayResponse != null; }
}
