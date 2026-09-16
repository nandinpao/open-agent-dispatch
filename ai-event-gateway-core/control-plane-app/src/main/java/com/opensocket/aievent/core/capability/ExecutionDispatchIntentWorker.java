package com.opensocket.aievent.core.capability;

import com.opensocket.aievent.core.assignment.TaskAssignment;
import com.opensocket.aievent.core.assignment.TaskAssignmentRepository;
import com.opensocket.aievent.core.dispatch.DispatchDecisionResult;
import com.opensocket.aievent.core.dispatch.DispatchRequestService;
import com.opensocket.aievent.core.task.TaskOrchestrationFacade;
import com.opensocket.aievent.core.task.TaskRecord;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Bridges the committed A0-R7 DispatchIntent to the existing durable dispatch_requests runtime.
 * It never performs network I/O itself; the existing DispatchExecutionService remains the sender
 * and is protected by A0R7ExecutionLeaseDispatchSafetyGuard immediately before network delivery.
 */
@Service
public class ExecutionDispatchIntentWorker {
    private final ExecutionDispatchIntentStore intents;
    private final TaskAssignmentRepository assignments;
    private final TaskOrchestrationFacade tasks;
    private final DispatchRequestService dispatchRequests;

    public ExecutionDispatchIntentWorker(ExecutionDispatchIntentStore intents, TaskAssignmentRepository assignments,
                                         TaskOrchestrationFacade tasks, DispatchRequestService dispatchRequests) {
        this.intents=intents;this.assignments=assignments;this.tasks=tasks;this.dispatchRequests=dispatchRequests;
    }

    public Map<String,Object> handoffNext(String tenantId,String workerId) {
        ExecutionDispatchIntentV206 claimed=intents.claimNext(tenantId,workerId,60);
        if(claimed==null)return Map.of("claimed",false,"message","No pending A0-R7 DispatchIntent");
        try {
            ExecutionDispatchIntentV206 started=intents.revalidateClaim(tenantId,claimed.intentId(),workerId);
            TaskAssignment assignment=assignments.findById(started.assignmentId())
                    .orElseThrow(() -> new IllegalStateException("A0_R7_COMPATIBILITY_ASSIGNMENT_NOT_FOUND"));
            TaskRecord task=tasks.findTask(started.taskId())
                    .orElseThrow(() -> new IllegalStateException("A0_R7_TASK_NOT_FOUND"));
            DispatchDecisionResult result=dispatchRequests.createIfEligible(assignment,task);
            if(result.dispatchRequestId()==null||result.dispatchRequestId().isBlank()) {
                intents.markBlocked(tenantId,started.intentId(),"A0_R7_RUNTIME_OUTBOX_HANDOFF_REJECTED",result.reason());
                return response(started,false,null,result.reason());
            }
            ExecutionDispatchIntentV206 handed=intents.markHandedOff(tenantId,started.intentId(),workerId,"dispatch:"+result.dispatchRequestId());
            return response(handed,true,result.dispatchRequestId(),result.reason());
        } catch (RuntimeException ex) {
            try { intents.markBlocked(tenantId,claimed.intentId(),"A0_R7_RUNTIME_OUTBOX_HANDOFF_FAILED",root(ex)); } catch (RuntimeException ignored) { }
            throw ex;
        }
    }

    private Map<String,Object> response(ExecutionDispatchIntentV206 intent,boolean handed,String dispatchRequestId,String reason){
        Map<String,Object> m=new LinkedHashMap<>();m.put("claimed",true);m.put("handedOff",handed);m.put("intentId",intent.intentId());
        m.put("assignmentId",intent.assignmentId());m.put("fencingToken",intent.fencingToken());m.put("status",intent.status());
        m.put("dispatchRequestId",dispatchRequestId);m.put("reason",reason);return m;
    }
    private String root(Throwable t){Throwable x=t;while(x.getCause()!=null&&x.getCause()!=x)x=x.getCause();return x.getMessage()==null?x.getClass().getSimpleName():x.getMessage();}
}
