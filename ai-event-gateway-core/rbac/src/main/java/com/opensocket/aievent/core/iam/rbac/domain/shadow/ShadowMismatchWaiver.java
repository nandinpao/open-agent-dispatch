package com.opensocket.aievent.core.iam.rbac.domain.shadow;
import java.time.Instant;import java.util.Optional;
public record ShadowMismatchWaiver(String waiverId,String caseId,String reason,String approvedBy,Instant approvedAt,Instant expiresAt,String status,Optional<Instant> revokedAt,Optional<String> revokedBy,long version){}
