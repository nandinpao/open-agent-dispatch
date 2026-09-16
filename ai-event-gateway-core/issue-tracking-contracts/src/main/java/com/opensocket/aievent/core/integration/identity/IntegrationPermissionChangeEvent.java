package com.opensocket.aievent.core.integration.identity;
import java.time.OffsetDateTime;
public record IntegrationPermissionChangeEvent(String tenantId,String changeEventId,String principalId,String mappingId,String previousProbeId,String currentProbeId,String previousFingerprint,String currentFingerprint,String changeSummary,OffsetDateTime detectedAt,String correlationId) {}
