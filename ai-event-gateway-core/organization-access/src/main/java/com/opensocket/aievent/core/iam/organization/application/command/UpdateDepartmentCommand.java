package com.opensocket.aievent.core.iam.organization.application.command;
public record UpdateDepartmentCommand(String tenantId,String departmentId,String code,String name,String parentDepartmentId,String managerUserId,int displayOrder,long expectedVersion,String reason,String actorId,String correlationId,String eventId) { }
