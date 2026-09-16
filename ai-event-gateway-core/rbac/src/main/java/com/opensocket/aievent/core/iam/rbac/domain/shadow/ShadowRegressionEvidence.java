package com.opensocket.aievent.core.iam.rbac.domain.shadow;
import java.time.Instant;
public record ShadowRegressionEvidence(String evidenceId,String caseId,String testReference,String result,String detailsJson,Instant executedAt,String executedBy,Instant createdAt){}
