package com.opensocket.aievent.core.integration.handoff;
import com.opensocket.aievent.core.organization.SensitivityLevel;
public record HandoffContextField(String fieldPath,String classification,SensitivityLevel sensitivityLevel,HandoffFieldShareDecision shareDecision,String maskingMethod,String sourceReference,Object projectedValue,String originalValueHash) {}
