package com.opensocket.aievent.core.iam.organization.application.command;

import com.opensocket.aievent.core.iam.organization.domain.GroupType;
public record CreateGroupCommand(String tenantId, String groupId, String code, String name, GroupType type, String parentGroupId, String ownerDepartmentId, String description, String actorId, String correlationId, String eventId) { }
