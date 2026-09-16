package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;
import java.util.Objects;

/** Secret-free result commit evidence presented against an authorization lease and fencing version. */
public record RuntimeResultSubmission(
        String tenantId,
        String submissionId,
        String leaseId,
        long presentedFencingVersion,
        ResourceRef resourceRef,
        String assignmentId,
        Integer attemptNo,
        String payloadHash,
        String correlationId,
        Instant observedAt) {
    public RuntimeResultSubmission {
        tenantId=required(tenantId,"tenantId"); submissionId=required(submissionId,"submissionId");
        leaseId=required(leaseId,"leaseId"); if(presentedFencingVersion<0)throw new IllegalArgumentException("presentedFencingVersion must be non-negative");
        Objects.requireNonNull(resourceRef,"resourceRef"); if(!tenantId.equals(resourceRef.tenantId()))throw new IllegalArgumentException("tenant mismatch");
        assignmentId=assignmentId==null?"":assignmentId.trim(); if(attemptNo!=null&&attemptNo<0)throw new IllegalArgumentException("attemptNo must be non-negative");
        payloadHash=required(payloadHash,"payloadHash").toLowerCase(java.util.Locale.ROOT); if(!payloadHash.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("payloadHash must be lowercase SHA-256 hex"); correlationId=required(correlationId,"correlationId"); Objects.requireNonNull(observedAt,"observedAt");
    }
    private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
