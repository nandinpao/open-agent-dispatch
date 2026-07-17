package com.opensocket.aievent.service.adapter;

import java.util.Map;

public record AdapterWorkerCompletionRequest(
        String workerId,
        String responseRef,
        Map<String, Object> responsePayload) {
}
