package com.opensocket.aievent.core.action;

import com.opensocket.aievent.core.callback.TaskCallbackRequest;
import com.opensocket.aievent.core.callback.TaskCallbackType;
import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.events.TaskTerminalEvent;
import com.opensocket.aievent.core.outbox.ModuleEventHandler;
import com.opensocket.aievent.core.task.TaskPriority;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;
import com.opensocket.aievent.core.task.TaskType;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles callback-side terminal events for compatibility actions only.
 *
 * <p>HF21 authority boundary: canonical Issue projection is deliberately NOT invoked from this
 * handler. A callback-side terminal event can be emitted before the A0-R2 finalization transaction
 * has closed the Task. Canonical Issue side effects are owned exclusively by
 * {@code TaskFinalizationProjectionService} after {@code TaskLifecycle=CLOSED}. Keeping the Issue
 * authority out of this handler prevents TASK_FINALIZATION_NOT_COMPLETE from turning an otherwise
 * successful terminal callback into an exponentially retried module-outbox event.</p>
 */
@Component
public class TaskTerminalEventHandler implements ModuleEventHandler<TaskTerminalEvent> {
    private static final Logger log = LoggerFactory.getLogger(TaskTerminalEventHandler.class);

    private final AdapterActionService service;

    public TaskTerminalEventHandler(AdapterActionService service) {
        this.service = service;
    }

    @Override public String eventType(){return TaskTerminalEvent.TYPE;}
    @Override public Class<TaskTerminalEvent> payloadType(){return TaskTerminalEvent.class;}

    @Override
    public void handle(TaskTerminalEvent event) {
        log.info("task_terminal_compatibility_action_received eventId={} taskId={} callbackType={} callbackId={} dispatchRequestId={} assignmentId={} agentId={} taskStatus={} incidentId={} canonicalIssueProjection=DEFERRED_TO_FINALIZATION",
                event.eventId(), event.taskId(), event.callbackType(), event.callbackId(), event.dispatchRequestId(), event.assignmentId(), event.agentId(), event.taskStatus(), event.incidentId());

        // Compatibility path remains for MCP and historical AdapterAction operations. Canonical
        // Issue projection is intentionally absent here; TaskFinalizationProjectionService owns it.
        service.evaluateAfterTaskCallback(task(event), dispatch(event), callback(event), callbackType(event), event.eventId());
    }

    private TaskRecord task(TaskTerminalEvent e){
        TaskRecord t=new TaskRecord();t.setTaskId(e.taskId());t.setIncidentId(e.incidentId());t.setSourceEventId(e.sourceEventId());
        t.setStatus(enumValue(TaskStatus.class,e.taskStatus()));t.setTaskType(enumValue(TaskType.class,e.taskType()));t.setPriority(enumValue(TaskPriority.class,e.priority()));
        t.setTenantId(e.tenantId());t.setSiteId(e.siteId());t.setPlantId(e.plantId());t.setObjectType(e.objectType());t.setObjectId(e.objectId());t.setEventType(e.sourceEventType());t.setErrorCode(e.errorCode());t.setRoutingPolicy(e.routingPolicy());t.setRequiredCapabilities(e.requiredCapabilities());
        t.setCorrelationId(e.correlationId());t.setOriginCorrelationId(e.correlationId());t.setTraceId(e.traceId());t.setActorPrincipalType(e.actorType());t.setActorPrincipalId(e.actorId());
        t.setTerminalAt(e.occurredAt());t.setUpdatedAt(e.occurredAt());return t;
    }
    private DispatchRequest dispatch(TaskTerminalEvent e){
        if(e.dispatchRequestId()==null||e.dispatchRequestId().isBlank())return null;DispatchRequest d=new DispatchRequest();d.setDispatchRequestId(e.dispatchRequestId());d.setAssignmentId(e.assignmentId());d.setTaskId(e.taskId());d.setIncidentId(e.incidentId());d.setAgentId(e.agentId());d.setOwnerGatewayNodeId(e.ownerGatewayNodeId());d.setAgentSessionId(e.agentSessionId());d.setSiteId(e.siteId());return d;
    }
    private TaskCallbackRequest callback(TaskTerminalEvent e){TaskCallbackRequest c=new TaskCallbackRequest();c.setCallbackId(e.callbackId());c.setTaskId(e.taskId());c.setDispatchRequestId(e.dispatchRequestId());c.setAssignmentId(e.assignmentId());c.setAgentId(e.agentId());c.setOwnerGatewayNodeId(e.ownerGatewayNodeId());c.setAgentSessionId(e.agentSessionId());c.setMessage(e.callbackMessage());c.setResultStatus(e.resultStatus());c.setErrorCode(e.callbackErrorCode());c.setErrorMessage(e.callbackErrorMessage());c.setPayload(e.payload());c.setOccurredAt(e.occurredAt());return c;}
    private TaskCallbackType callbackType(TaskTerminalEvent e){TaskCallbackType type=enumValue(TaskCallbackType.class,e.callbackType());return type==null?(e.taskStatus()!=null&&e.taskStatus().equals("COMPLETED")?TaskCallbackType.RESULT:TaskCallbackType.ERROR):type;}
    private <E extends Enum<E>> E enumValue(Class<E> type,String value){if(value==null||value.isBlank())return null;try{return Enum.valueOf(type,value);}catch(IllegalArgumentException ignored){return null;}}
}
