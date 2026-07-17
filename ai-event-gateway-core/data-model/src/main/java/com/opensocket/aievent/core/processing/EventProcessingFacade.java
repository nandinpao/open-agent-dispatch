package com.opensocket.aievent.core.processing;

import com.opensocket.aievent.core.event.EventIntakeRequest;

/**
 * Stable application boundary for the normalize/fingerprint/dedup/incident-observation pipeline.
 * Callers must not reach into event-processing repositories or Incident repositories directly.
 */
public interface EventProcessingFacade {
    EventProcessingResult process(EventIntakeRequest request);
}
