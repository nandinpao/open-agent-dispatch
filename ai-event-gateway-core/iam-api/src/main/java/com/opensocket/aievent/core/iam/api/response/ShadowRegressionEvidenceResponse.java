package com.opensocket.aievent.core.iam.api.response;
import com.opensocket.aievent.core.iam.rbac.domain.shadow.ShadowRegressionEvidence;import java.time.Instant;
public record ShadowRegressionEvidenceResponse(String evidenceId,String caseId,String testReference,String result,String detailsJson,Instant executedAt,String executedBy,Instant createdAt){public static ShadowRegressionEvidenceResponse from(ShadowRegressionEvidence v){return new ShadowRegressionEvidenceResponse(v.evidenceId(),v.caseId(),v.testReference(),v.result(),v.detailsJson(),v.executedAt(),v.executedBy(),v.createdAt());}}
