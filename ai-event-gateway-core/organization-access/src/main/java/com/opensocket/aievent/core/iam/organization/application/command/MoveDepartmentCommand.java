package com.opensocket.aievent.core.iam.organization.application.command;

public record MoveDepartmentCommand(String tenantId, String departmentId, String newParentDepartmentId, long expectedVersion, String reason, String actorId, String correlationId, String eventId) { }
