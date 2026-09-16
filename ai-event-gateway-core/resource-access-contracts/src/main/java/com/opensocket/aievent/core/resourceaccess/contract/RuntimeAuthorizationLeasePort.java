package com.opensocket.aievent.core.resourceaccess.contract;
/** Stable contract for expiring, epoch-bound and fenceable long-running authorization. */
public interface RuntimeAuthorizationLeasePort {
    RuntimeAuthorizationLease issue(AuthorizationRequest request, AuthorizationDecision decision);
    RuntimeAuthorizationLease check(RuntimeAuthorizationCheckpoint checkpoint);
    RuntimeAuthorizationLease complete(String tenantId, String leaseId, String correlationId);
    RuntimeAuthorizationLease revoke(String tenantId, String leaseId, String reasonCode, String correlationId);
    default RuntimeAuthorizationLease complete(String tenantId,String leaseId){return complete(tenantId,leaseId,"runtime-lease-complete");}
    default RuntimeAuthorizationLease revoke(String tenantId,String leaseId,String reasonCode){return revoke(tenantId,leaseId,reasonCode,"runtime-lease-revoke");}
}
