package com.opensocket.aievent.core.evidence;

import java.util.Map;

/** Human/automation supplied runtime proof. The service never manufactures PASS results. */
public record RuntimeAcceptanceEvidenceRequest(
        String tenantId,
        String scenarioCode,
        String result,
        String environmentRef,
        String evidenceRef,
        Map<String,Object> details) {}
