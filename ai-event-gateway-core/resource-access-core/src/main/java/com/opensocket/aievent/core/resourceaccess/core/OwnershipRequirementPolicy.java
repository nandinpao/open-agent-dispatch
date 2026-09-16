package com.opensocket.aievent.core.resourceaccess.core;

import com.opensocket.aievent.core.resourceaccess.contract.ResourceType;
import java.util.EnumSet;
import java.util.Set;

/** Resource types that must have an operational owner before ordinary access can be enabled. */
public final class OwnershipRequirementPolicy {
    private static final Set<ResourceType> REQUIRED = EnumSet.of(
            ResourceType.TASK, ResourceType.TASK_CHAIN, ResourceType.A2A_REQUEST, ResourceType.A2A_APPROVAL,
            ResourceType.AGENT, ResourceType.AGENT_POOL, ResourceType.AGENT_SERVICE_SCOPE, ResourceType.AGENT_CREDENTIAL_METADATA,
            ResourceType.ISSUE_CONNECTION, ResourceType.ISSUE_PRINCIPAL, ResourceType.ISSUE_CREDENTIAL_METADATA,
            ResourceType.ISSUE_PROJECT_MAPPING, ResourceType.TASK_ISSUE_LINK, ResourceType.ISSUE_CONTEXT_SNAPSHOT,
            ResourceType.ISSUE_ATTACHMENT, ResourceType.ISSUE_CONFLICT, ResourceType.ISSUE_DEAD_LETTER,
            ResourceType.ISSUE_TOPOLOGY, ResourceType.SERVICE_ACCOUNT, ResourceType.ACCESS_TOKEN_METADATA);
    public boolean requiresOwner(ResourceType type) { return REQUIRED.contains(type); }
}
