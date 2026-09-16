package com.opensocket.aievent.core.eventquery;

import java.util.Map;

/** Payload is never embedded in BusinessEventView. */
public record BusinessEventPayloadView(String eventId, Map<String,Object> payload) { }
