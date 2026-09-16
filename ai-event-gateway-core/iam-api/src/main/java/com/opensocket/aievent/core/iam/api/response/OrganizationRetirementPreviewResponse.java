package com.opensocket.aievent.core.iam.api.response;

/** Canonical impact preview for Department / Group retirement. */
public record OrganizationRetirementPreviewResponse(
        String tenantId,
        String organizationType,
        String organizationId,
        String organizationName,
        long peopleCount,
        long primaryPeopleCount,
        long childCount,
        long ownedGroupCount,
        long activeRoleBindingCount,
        long sourceSystemCount,
        long dispatchFlowCount,
        long agentPoolCount,
        long a2aPolicyCount,
        long hardBlockerCount,
        boolean canDelete) { }
