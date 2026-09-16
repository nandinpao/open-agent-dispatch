package com.opensocket.aievent.core.a2a.application.port.in;

import java.util.List;

import com.opensocket.aievent.core.a2a.A2ALateResultResolution;
import com.opensocket.aievent.core.a2a.A2AResultQuarantine;

/** Inbound boundary for manual governance of quarantined result evidence. */
public interface A2ALateResultGovernanceUseCase {
    List<A2AResultQuarantine> open(String tenantId, int limit);
    A2AResultQuarantine resolve(String tenantId, String quarantineId, A2ALateResultResolution decision,
                                String actorId, String reason);
}
