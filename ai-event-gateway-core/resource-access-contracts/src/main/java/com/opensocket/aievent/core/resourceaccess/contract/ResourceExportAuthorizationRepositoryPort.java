package com.opensocket.aievent.core.resourceaccess.contract;
import java.util.Optional;
/** Persistence authority for fixed export authorizations and immutable artifact commit evidence. */
public interface ResourceExportAuthorizationRepositoryPort {
 ResourceExportAuthorization save(ResourceExportAuthorization authorization,String principalId,String purpose,String idempotencyKey,String correlationId);
 Optional<ResourceExportAuthorization> find(String tenantId,String exportAuthorizationId);
 void appendCommit(ExportArtifactCommitResult result,String tenantId,String principalId,String correlationId);
}
