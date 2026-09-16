package com.opensocket.aievent.core.a2a.application.port.in;

import com.opensocket.aievent.core.a2a.A2AResult;
import com.opensocket.aievent.core.a2a.A2AResultSubmission;

/** Inbound boundary for evidence-backed canonical result acceptance. */
public interface A2AResultAcceptanceUseCase {
    A2AResult accept(A2AResultSubmission submission);
}
