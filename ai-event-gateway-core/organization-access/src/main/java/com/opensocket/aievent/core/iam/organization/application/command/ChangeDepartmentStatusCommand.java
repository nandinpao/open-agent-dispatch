package com.opensocket.aievent.core.iam.organization.application.command;
import com.opensocket.aievent.core.iam.organization.domain.DepartmentStatus;
public record ChangeDepartmentStatusCommand(String tenantId,String departmentId,DepartmentStatus status,long expectedVersion,String reason,String actorId,String correlationId,String eventId) { }
