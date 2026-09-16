package com.opensocket.aievent.core.integration.handoff;
import java.time.OffsetDateTime; import org.springframework.stereotype.Component; import com.opensocket.aievent.core.a2a.*; import com.opensocket.aievent.core.task.*; import com.opensocket.aievent.core.task.domain.TaskCompletionGuard;
@Component
public class HandoffContextCompletionGuard implements TaskCompletionGuard {
 private final A2APolicyRepository policies; private final HandoffContextRepository contexts;
 public HandoffContextCompletionGuard(A2APolicyRepository policies,HandoffContextRepository contexts){this.policies=policies;this.contexts=contexts;}
 public void requireCompletionAllowed(TaskRecord task,TaskStatus targetStatus,String correlationId){if(targetStatus==null||!(targetStatus.canonical()==TaskStatus.SUCCEEDED||targetStatus==TaskStatus.COMPLETED))return;if(task.getA2aPolicyId()==null||task.getA2aPolicyId().isBlank())return;A2APolicy p=policies.findById(task.getTenantId(),task.getA2aPolicyId()).orElse(null);if(p==null||!"REQUIRED_BEFORE_COMPLETION".equalsIgnoreCase(p.getHandoffContextRequirement()))return;boolean ready=contexts.latestApprovedSnapshotForTask(task.getTenantId(),task.getTaskId()).filter(s->s.expiresAt()==null||s.expiresAt().isAfter(OffsetDateTime.now())).isPresent();if(!ready)throw new IllegalStateException("HANDOFF_CONTEXT_REQUIRED_BEFORE_COMPLETION");}
}
