package com.opensocket.aievent.core.a2a.application.port.in;

import com.opensocket.aievent.core.a2a.A2ACutoverSnapshot;
import com.opensocket.aievent.core.a2a.A2ACutoverState;
import com.opensocket.aievent.core.a2a.A2ACutoverTransitionCommand;

public interface A2ACutoverUseCase {
    A2ACutoverSnapshot current(String scopeId, int evidenceLimit);
    A2ACutoverState advance(A2ACutoverTransitionCommand command);
}
