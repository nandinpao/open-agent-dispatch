package com.opensocket.aievent.core.a2a.core;

import static org.junit.jupiter.api.Assertions.*;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.opensocket.aievent.core.a2a.*;

class A2AResultEvidenceGuardTest {
    private A2AResultSubmission submission(String evidenceHash) {
        return new A2AResultSubmission("tenant-a", "req-1", A2AResultStatus.SUCCEEDED, "done", null, List.of("cb"),
                "agent-1", "asg-1", "exec-1", 1, "disp-1", "dhash", "fhash", "session-1",
                "callback-1", "payload-hash", 1, evidenceHash, "idem-1", "corr-1", OffsetDateTime.now());
    }
    @Test void acceptsValidEvidenceHash() {
        A2AResultSubmission unsigned = submission("pending");
        A2AResultSubmission signed = new A2AResultSubmission(unsigned.tenantId(), unsigned.requestId(),
                unsigned.resultStatus(), unsigned.resultSummary(), unsigned.resultPayloadRef(), unsigned.evidenceRefs(),
                unsigned.completedByAgentId(), unsigned.assignmentId(), unsigned.executionAttemptId(), unsigned.attemptNo(),
                unsigned.dispatchRequestId(), unsigned.dispatchTokenHash(), unsigned.fencingTokenHash(),
                unsigned.agentSessionId(), unsigned.callbackInboxId(), unsigned.payloadHash(), unsigned.resultSchemaVersion(),
                A2AResultFingerprint.evidence(unsigned), unsigned.idempotencyKey(), unsigned.correlationId(), unsigned.occurredAt());
        assertDoesNotThrow(() -> new A2AResultEvidenceGuard().requireEvidence(signed));
    }
    @Test void rejectsTamperedEvidenceHash() {
        assertThrows(IllegalArgumentException.class, () -> new A2AResultEvidenceGuard().requireEvidence(submission("bad")));
    }
}
