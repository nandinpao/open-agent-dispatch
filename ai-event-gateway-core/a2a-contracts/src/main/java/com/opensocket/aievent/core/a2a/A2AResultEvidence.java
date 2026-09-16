package com.opensocket.aievent.core.a2a;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class A2AResultEvidence {
    private String tenantId;
    private String evidenceId;
    private String attemptId;
    private String requestId;
    private String evidenceType;
    private String evidenceReference;
    private String evidenceHash;
    private String verificationDecision;
    private A2AResultClassification classification;
    private String resultFingerprint;
    private String reasonCode;
    private OffsetDateTime verifiedAt;
}
