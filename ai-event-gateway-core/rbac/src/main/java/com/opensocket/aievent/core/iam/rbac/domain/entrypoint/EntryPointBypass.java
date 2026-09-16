package com.opensocket.aievent.core.iam.rbac.domain.entrypoint;
import java.time.Instant;import java.util.Optional;
public record EntryPointBypass(String bypassId,String entryPointId,String ownerId,String reason,String replacement,
 Instant expiresAt,String status,Instant createdAt,String createdBy,Optional<Instant> revokedAt,Optional<String> revokedBy,
 Optional<String> revokeReason,long version) {}
