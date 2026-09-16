package com.opensocket.aievent.core.iam.organization.event;

import java.time.Instant;

public final class OrganizationEvents {
    private OrganizationEvents() { }
    public record TenantCreated(String eventId, String tenantId, String subjectId, String actorId, String correlationId, Instant occurredAt) implements OrganizationDomainEvent { public String eventType() { return "TENANT_CREATED"; } }
    public record TenantStatusChanged(String eventId, String tenantId, String subjectId, String previousStatus, String newStatus, String actorId, String correlationId, Instant occurredAt) implements OrganizationDomainEvent { public String eventType() { return "TENANT_STATUS_CHANGED"; } }
    public record TenantMembershipChanged(String eventId, String tenantId, String subjectId, String principalId, String status, String actorId, String correlationId, Instant occurredAt) implements OrganizationDomainEvent { public String eventType() { return "TENANT_MEMBERSHIP_CHANGED"; } }
    public record DepartmentCreated(String eventId, String tenantId, String subjectId, String actorId, String correlationId, Instant occurredAt) implements OrganizationDomainEvent { public String eventType() { return "DEPARTMENT_CREATED"; } }
    public record DepartmentUpdated(String eventId, String tenantId, String subjectId, String managerUserId, String actorId, String correlationId, Instant occurredAt) implements OrganizationDomainEvent { public String eventType() { return "DEPARTMENT_UPDATED"; } }
    public record DepartmentStatusChanged(String eventId, String tenantId, String subjectId, String previousStatus, String newStatus, String actorId, String correlationId, Instant occurredAt) implements OrganizationDomainEvent { public String eventType() { return "DEPARTMENT_STATUS_CHANGED"; } }
    public record DepartmentMoved(String eventId, String tenantId, String subjectId, String previousParentId, String newParentId, long revision, String actorId, String correlationId, Instant occurredAt) implements OrganizationDomainEvent { public String eventType() { return "DEPARTMENT_MOVED"; } }
    public record DepartmentRevisionCreated(String eventId, String tenantId, String subjectId, long revision, String changeType, String actorId, String correlationId, Instant occurredAt) implements OrganizationDomainEvent { public String eventType() { return "DEPARTMENT_REVISION_CREATED"; } }
    public record DepartmentMembershipChanged(String eventId, String tenantId, String subjectId, String principalId, boolean primary, String actorId, String correlationId, Instant occurredAt) implements OrganizationDomainEvent { public String eventType() { return "DEPARTMENT_MEMBERSHIP_CHANGED"; } }
    public record GroupCreated(String eventId, String tenantId, String subjectId, String actorId, String correlationId, Instant occurredAt) implements OrganizationDomainEvent { public String eventType() { return "GROUP_CREATED"; } }
    public record GroupUpdated(String eventId, String tenantId, String subjectId, String actorId, String correlationId, Instant occurredAt) implements OrganizationDomainEvent { public String eventType() { return "GROUP_UPDATED"; } }
    public record GroupStatusChanged(String eventId, String tenantId, String subjectId, String previousStatus, String newStatus, String actorId, String correlationId, Instant occurredAt) implements OrganizationDomainEvent { public String eventType() { return "GROUP_STATUS_CHANGED"; } }
    public record GroupMembershipChanged(String eventId, String tenantId, String subjectId, String principalId, String actorId, String correlationId, Instant occurredAt) implements OrganizationDomainEvent { public String eventType() { return "GROUP_MEMBERSHIP_CHANGED"; } }
    public record OrganizationSnapshotCaptured(String eventId, String tenantId, String subjectId, String contentHash, String actorId, String correlationId, Instant occurredAt) implements OrganizationDomainEvent { public String eventType() { return "ORGANIZATION_SNAPSHOT_CAPTURED"; } }
}
