package com.opensocket.aievent.core.dispatch;

import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.opensocket.aievent.core.task.TaskExecutionLifecyclePort;

@Service
public class ExecutionTaskLifecycleAdapter implements TaskExecutionLifecyclePort {
    private final DispatchRequestRepository repository;
    public ExecutionTaskLifecycleAdapter(DispatchRequestRepository repository){this.repository=repository;}
    @Override
    @Transactional
    public boolean cancelOpenDispatchByAssignment(String assignmentId,String reason,OffsetDateTime now){
        return repository.findOpenByAssignmentId(assignmentId).map(request->{
            request.setStatus(DispatchRequestStatus.CANCELLED);
            request.setReason(reason==null||reason.isBlank()?"Lifecycle cancelled dispatch":reason);
            request.setUpdatedAt(now);
            repository.save(request);
            return true;
        }).orElse(false);
    }
}
