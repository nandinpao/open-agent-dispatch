package com.opensocket.aievent.core.capability;

import java.util.List;

public record PlanAdmissionResult(PlanAdmissionDecision decision,List<BindingAuthorizationEnvelope> envelopes) {
    public PlanAdmissionResult { envelopes=envelopes==null?List.of():List.copyOf(envelopes); }
}
