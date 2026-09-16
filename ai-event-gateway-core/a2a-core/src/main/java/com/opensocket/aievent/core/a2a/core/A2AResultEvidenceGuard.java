package com.opensocket.aievent.core.a2a.core;

import com.opensocket.aievent.core.a2a.A2AResultSubmission;

/** Structural and cryptographic guard before authority readback. */
public final class A2AResultEvidenceGuard {
    public void requireEvidence(A2AResultSubmission submission) {
        if (submission == null) throw new IllegalArgumentException("A2A result submission is required");
        require(submission.tenantId(), "tenantId");
        require(submission.requestId(), "requestId");
        if (submission.resultStatus() == null) throw new IllegalArgumentException("resultStatus is required");
        require(submission.assignmentId(), "assignmentId");
        require(submission.dispatchRequestId(), "dispatchRequestId");
        require(submission.callbackInboxId(), "callbackInboxId");
        require(submission.dispatchTokenHash(), "dispatchTokenHash");
        require(submission.fencingTokenHash(), "fencingTokenHash");
        require(submission.agentSessionId(), "agentSessionId");
        require(submission.payloadHash(), "payloadHash");
        require(submission.resultEvidenceHash(), "resultEvidenceHash");
        require(submission.idempotencyKey(), "Idempotency-Key");
        if (submission.resultSchemaVersion() < 1) throw new IllegalArgumentException("resultSchemaVersion must be greater than zero");
        if (submission.attemptNo() == null || submission.attemptNo() < 1) {
            throw new IllegalArgumentException("attemptNo must be greater than zero");
        }
        if (!A2AResultFingerprint.evidence(submission).equals(submission.resultEvidenceHash())) {
            throw new IllegalArgumentException("A2A_RESULT_EVIDENCE_HASH_INVALID");
        }
    }

    private void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
