package com.opensocket.aievent.core.a2a.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.a2a.*;
import com.opensocket.aievent.core.a2a.application.service.A2AResultAcceptanceService.A2AResultQuarantinedException;
import com.opensocket.aievent.core.a2a.core.A2AResultFingerprint;
import com.opensocket.aievent.core.a2a.core.port.ResultEvidenceAuthorityPort;
import com.opensocket.aievent.core.a2a.core.port.ResultEvidenceAuthorityPort.Verification;

class A2AResultIdempotencyLedgerC0A4Test {

    @Test
    void transientVerificationFailureMustNotBurnIdempotencyKeyAndRetryCanAccept() {
        String tenant="tenant-a", requestId="req-1", key="result-key-1";
        A2ARequest request=runningRequest(tenant,requestId);
        A2AResultSubmission submission=submission(tenant,requestId,key);

        ResultEvidenceAuthorityPort authority=mock(ResultEvidenceAuthorityPort.class);
        when(authority.verify(eq("child-1"), any())).thenReturn(
                new Verification(A2AResultAcceptanceDecision.MISSING_EVIDENCE,A2AResultClassification.MISSING_EVIDENCE,
                        "EXECUTION_ATTEMPT_NOT_FOUND","Execution Attempt not committed yet","asg-1",null,1,"dr-1","agent-1","session-1",null),
                new Verification(A2AResultAcceptanceDecision.ACCEPTED,null,"A2A_RESULT_EVIDENCE_ACCEPTED","verified",
                        "asg-1","exec-1",1,"dr-1","agent-1","session-1","cbfp"));

        A2ARequestRepository requests=mock(A2ARequestRepository.class);
        when(requests.findById(tenant,requestId)).thenReturn(Optional.of(request));
        when(requests.saveExpectedVersion(any(),anyLong())).thenAnswer(inv->inv.getArgument(0));

        AtomicReference<A2AResult> canonical=new AtomicReference<>();
        A2AResultRepository results=mock(A2AResultRepository.class);
        when(results.findByRequest(tenant,requestId)).thenAnswer(inv->Optional.ofNullable(canonical.get()));
        when(results.findById(eq(tenant),anyString())).thenAnswer(inv->Optional.ofNullable(canonical.get()));
        when(results.save(any())).thenAnswer(inv->{A2AResult r=inv.getArgument(0);canonical.set(r);return r;});

        List<A2AResultAttempt> attemptEvidence=new ArrayList<>();
        A2AResultAttemptRepository attempts=mock(A2AResultAttemptRepository.class);
        when(attempts.save(any())).thenAnswer(inv->{A2AResultAttempt a=inv.getArgument(0);attemptEvidence.add(a);return a;});

        Map<String,A2AResultIdempotencyClaim> ledger=new HashMap<>();
        A2AResultIdempotencyClaimRepository claims=mock(A2AResultIdempotencyClaimRepository.class);
        when(claims.findByIdempotencyKey(tenant,key)).thenAnswer(inv->Optional.ofNullable(ledger.get(key)));
        when(claims.save(any())).thenAnswer(inv->{A2AResultIdempotencyClaim c=inv.getArgument(0);ledger.put(c.getIdempotencyKey(),c);return c;});

        A2AResultEvidenceRepository evidence=mock(A2AResultEvidenceRepository.class);
        when(evidence.save(any())).thenAnswer(inv->inv.getArgument(0));
        A2AResultQuarantineRepository quarantine=mock(A2AResultQuarantineRepository.class);
        when(quarantine.save(any())).thenAnswer(inv->inv.getArgument(0));
        A2AResultProcessingRepository processing=mock(A2AResultProcessingRepository.class);
        when(processing.save(any())).thenAnswer(inv->inv.getArgument(0));
        A2AStateHistoryRepository history=mock(A2AStateHistoryRepository.class);
        when(history.findByIdempotencyKey(anyString(),anyString())).thenReturn(Optional.empty());
        when(history.save(any())).thenAnswer(inv->inv.getArgument(0));
        A2AResultCompletionCoordinator coordinator=mock(A2AResultCompletionCoordinator.class);

        A2AResultAcceptanceService service=new A2AResultAcceptanceService(authority,requests,results,attempts,claims,evidence,quarantine,processing,history,coordinator);

        A2AResultQuarantinedException first=assertThrows(A2AResultQuarantinedException.class,()->service.accept(submission));
        assertThat(first.getReasonCode()).isEqualTo("EXECUTION_ATTEMPT_NOT_FOUND");
        assertThat(attemptEvidence).hasSize(1);
        assertThat(ledger).isEmpty();

        A2AResult accepted=service.accept(submission);
        assertThat(accepted.getIdempotencyKey()).isEqualTo(key);
        assertThat(attemptEvidence).hasSize(2);
        assertThat(ledger).containsKey(key);
        assertThat(ledger.get(key).getResultId()).isEqualTo(accepted.getResultId());

        A2AResult replay=service.accept(submission);
        assertThat(replay.getResultId()).isEqualTo(accepted.getResultId());
        assertThat(attemptEvidence).hasSize(2);
        verify(authority,times(2)).verify(eq("child-1"),any());
    }

    private A2ARequest runningRequest(String tenant,String requestId){
        A2ARequest r=new A2ARequest();r.setTenantId(tenant);r.setRequestId(requestId);r.setRootTaskId("root-1");r.setSourceTaskId("parent-1");r.setChildTaskId("child-1");r.setRequestStatus(A2ARequestStatus.RUNNING);r.setOperationalStage(A2AOperationalStage.RUNNING);r.setVersion(7);r.setPolicyVersion(1);r.setCorrelationId("corr-1");return r;
    }
    private A2AResultSubmission submission(String tenant,String requestId,String key){
        OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);
        A2AResultSubmission provisional=new A2AResultSubmission(tenant,requestId,A2AResultStatus.SUCCEEDED,"ok","ref",List.of("ev-1"),"agent-1","asg-1","exec-1",1,"dr-1","dth","fth","session-1","cb-1","payload-hash",1,"placeholder",key,"corr-1",now);
        String evidenceHash=A2AResultFingerprint.evidence(provisional);
        return new A2AResultSubmission(tenant,requestId,A2AResultStatus.SUCCEEDED,"ok","ref",List.of("ev-1"),"agent-1","asg-1","exec-1",1,"dr-1","dth","fth","session-1","cb-1","payload-hash",1,evidenceHash,key,"corr-1",now);
    }
}
