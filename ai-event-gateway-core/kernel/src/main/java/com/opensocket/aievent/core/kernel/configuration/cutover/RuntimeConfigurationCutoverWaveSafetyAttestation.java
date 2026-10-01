package com.opensocket.aievent.core.kernel.configuration.cutover;

import java.time.OffsetDateTime;

/** Durable pre-cutover safety evidence for a high-risk configuration cutover wave. */
public record RuntimeConfigurationCutoverWaveSafetyAttestation(
        String attestationId,String waveId,String status,String evidenceJson,
        String attestedBy,String reason,OffsetDateTime capturedAt,OffsetDateTime expiresAt) {
    public RuntimeConfigurationCutoverWaveSafetyAttestation {
        attestationId=required(attestationId,"attestationId");waveId=required(waveId,"waveId");
        status=required(status,"status").toUpperCase();attestedBy=required(attestedBy,"attestedBy");
        reason=required(reason,"reason");evidenceJson=evidenceJson==null?"{}":evidenceJson;
        if(!"PASS".equals(status)&&!"FAIL".equals(status)) throw new IllegalArgumentException("safety attestation status must be PASS or FAIL");
        if(capturedAt==null||expiresAt==null||!expiresAt.isAfter(capturedAt)) throw new IllegalArgumentException("valid safety attestation time window is required");
    }
    private static String required(String value,String field){if(value==null||value.isBlank())throw new IllegalArgumentException(field+" is required");return value.trim();}
}
