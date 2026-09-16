package com.opensocket.aievent.core.issuetracking.recovery;
public record ReconciliationRepairResult(boolean successful,boolean retryable,String evidenceReference,String failureCode,String safeMessage){public static ReconciliationRepairResult unavailable(){return new ReconciliationRepairResult(false,true,null,"RECOVERY_GATEWAY_UNAVAILABLE","Scoped provider recovery gateway is unavailable.");}}
