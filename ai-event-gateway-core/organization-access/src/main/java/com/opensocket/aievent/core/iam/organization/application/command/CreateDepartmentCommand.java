package com.opensocket.aievent.core.iam.organization.application.command;

public record CreateDepartmentCommand(String tenantId, String departmentId, String code, String name, String parentDepartmentId, String managerUserId, int displayOrder, String reason, String actorId, String correlationId, String eventId) { }
