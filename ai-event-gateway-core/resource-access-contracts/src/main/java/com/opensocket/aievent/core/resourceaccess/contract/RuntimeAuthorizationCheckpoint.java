package com.opensocket.aievent.core.resourceaccess.contract;
/** Checkpoint evidence presented by a long-running operation. */
public record RuntimeAuthorizationCheckpoint(String tenantId,String leaseId,OperationPhase operationPhase,long presentedFencingVersion,String correlationId){
 public RuntimeAuthorizationCheckpoint{tenantId=required(tenantId,"tenantId");leaseId=required(leaseId,"leaseId");operationPhase=operationPhase==null?OperationPhase.HEARTBEAT:operationPhase;if(presentedFencingVersion<0)throw new IllegalArgumentException("presentedFencingVersion must be non-negative");correlationId=required(correlationId,"correlationId");}
 private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
