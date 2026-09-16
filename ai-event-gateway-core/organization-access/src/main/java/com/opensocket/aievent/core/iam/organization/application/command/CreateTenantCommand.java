package com.opensocket.aievent.core.iam.organization.application.command;

public record CreateTenantCommand(String tenantId, String tenantCode, String tenantName, String legalName, String timezone, String locale, String dataRegion, String actorId, String correlationId, String eventId) { }
