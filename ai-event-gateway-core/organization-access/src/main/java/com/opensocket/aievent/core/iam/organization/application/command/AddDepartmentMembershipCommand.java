package com.opensocket.aievent.core.iam.organization.application.command;

import com.opensocket.aievent.core.iam.organization.domain.DepartmentMembershipType;
import java.time.Instant;
public record AddDepartmentMembershipCommand(String membershipId, String tenantId, String userId, String departmentId, DepartmentMembershipType membershipType, boolean primary, Instant expiresAt, String actorId, String correlationId, String eventId) { }
