package com.opensocket.aievent.core.iam.organization.application.command;

import com.opensocket.aievent.core.iam.organization.domain.TenantStatus;
public record ChangeTenantStatusCommand(String tenantId, TenantStatus targetStatus, long expectedVersion, String actorId, String correlationId, String eventId) { }
