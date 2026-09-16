package com.opensocket.aievent.core.iam.organization.application.command;

import java.util.Set;
public record CaptureOrganizationSnapshotCommand(String snapshotId, String tenantId, String departmentId, Set<String> groupIds, String actorId, String correlationId, String eventId) { }
