package com.opensocket.aievent.core.iam.runtime.eventintake;

import com.opensocket.aievent.core.iam.security.contract.MachineAuthenticationContext;

/** Creation-time evidence emitted by the Event Intake authorization boundary. */
public record EventIntakeAuthorizationEvidence(String decisionId, String authenticationMethod, MachineAuthenticationContext machineContext) {
    public EventIntakeAuthorizationEvidence {
        decisionId = decisionId == null ? "" : decisionId.trim();
        authenticationMethod = authenticationMethod == null || authenticationMethod.isBlank() ? "UNKNOWN" : authenticationMethod.trim();
    }
    public static EventIntakeAuthorizationEvidence unenforced() { return new EventIntakeAuthorizationEvidence("", "UNENFORCED", null); }
}
