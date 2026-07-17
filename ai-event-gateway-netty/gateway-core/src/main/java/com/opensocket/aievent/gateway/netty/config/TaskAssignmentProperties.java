package com.opensocket.aievent.gateway.netty.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Guards the production boundary between Netty transport delivery and Core assignment ownership.
 *
 * <p>Netty may deliver commands to a known connected Agent, but it must not become the assignment
 * authority. Client-originated TASK_SUBMIT and TASK_DISPATCH messages are rejected in production
 * runtime so task creation, assignment, dispatch token generation, retry, and recovery remain owned
 * by ai-event-gateway-core.</p>
 */
@ConfigurationProperties(prefix = "gateway.task-assignment")
public class TaskAssignmentProperties {

    private TaskAssignmentMode mode = TaskAssignmentMode.CORE_ONLY;
    private boolean rejectExternalTaskDispatch = true;

    /** Required for Spring Boot ConfigurationProperties JavaBean binding. */
    public TaskAssignmentProperties() {
    }

    public TaskAssignmentProperties(TaskAssignmentMode mode, boolean rejectExternalTaskDispatch) {
        this.mode = mode == null ? TaskAssignmentMode.CORE_ONLY : mode;
        this.rejectExternalTaskDispatch = rejectExternalTaskDispatch;
    }

    public TaskAssignmentMode getMode() {
        return mode;
    }

    public void setMode(TaskAssignmentMode mode) {
        this.mode = mode == null ? TaskAssignmentMode.CORE_ONLY : mode;
    }


    public boolean isRejectExternalTaskDispatch() {
        return rejectExternalTaskDispatch;
    }

    public void setRejectExternalTaskDispatch(boolean rejectExternalTaskDispatch) {
        this.rejectExternalTaskDispatch = rejectExternalTaskDispatch;
    }

    public boolean coreOnly() {
        return mode == TaskAssignmentMode.CORE_ONLY;
    }

    public boolean disabled() {
        return mode == TaskAssignmentMode.DISABLED;
    }
}
