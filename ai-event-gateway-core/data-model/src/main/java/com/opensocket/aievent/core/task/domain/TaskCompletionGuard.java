package com.opensocket.aievent.core.task.domain;
import com.opensocket.aievent.core.task.TaskRecord; import com.opensocket.aievent.core.task.TaskStatus;
public interface TaskCompletionGuard { void requireCompletionAllowed(TaskRecord task,TaskStatus targetStatus,String correlationId); }
