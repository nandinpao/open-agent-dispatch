package com.opensocket.aievent.core.resourceaccess.core;

import static com.opensocket.aievent.core.resourceaccess.contract.DescriptorAuthority.*;
import static com.opensocket.aievent.core.resourceaccess.contract.ResourceType.*;
import static com.opensocket.aievent.core.resourceaccess.contract.SensitivityLevel.*;

import com.opensocket.aievent.core.resourceaccess.contract.ResourceCatalogEntry;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceType;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** P4RA-A catalog baseline. Adding a ResourceType requires an explicit authority and capability decision. */
public final class DefaultResourceCatalog implements ResourceCatalog {
    private final Map<ResourceType, ResourceCatalogEntry> entries;
    public DefaultResourceCatalog() {
        EnumMap<ResourceType, ResourceCatalogEntry> map = new EnumMap<>(ResourceType.class);
        add(map, TASK, com.opensocket.aievent.core.resourceaccess.contract.ResourceCategory.TASK, TASK_DOMAIN, true, true, true, true, CONFIDENTIAL, "Dispatch task");
        add(map, TASK_CHAIN, com.opensocket.aievent.core.resourceaccess.contract.ResourceCategory.TASK, TASK_DOMAIN, true, true, true, false, CONFIDENTIAL, "Task relationship graph");
        add(map, A2A_REQUEST, com.opensocket.aievent.core.resourceaccess.contract.ResourceCategory.A2A, A2A_DOMAIN, true, true, true, true, CONFIDENTIAL, "A2A handoff request");
        add(map, A2A_APPROVAL, com.opensocket.aievent.core.resourceaccess.contract.ResourceCategory.A2A, A2A_DOMAIN, true, true, true, false, RESTRICTED, "A2A approval package");
        add(map, TASK_CONTEXT_SNAPSHOT, com.opensocket.aievent.core.resourceaccess.contract.ResourceCategory.TASK, TASK_DOMAIN, true, true, true, true, RESTRICTED, "Assignment-bound task context");
        add(map, TASK_RESULT, com.opensocket.aievent.core.resourceaccess.contract.ResourceCategory.TASK, TASK_DOMAIN, true, true, true, true, CONFIDENTIAL, "Task result and approved summary");
        add(map, TASK_ATTACHMENT, com.opensocket.aievent.core.resourceaccess.contract.ResourceCategory.TASK, TASK_DOMAIN, true, true, true, true, RESTRICTED, "Task attachment metadata and content");
        add(map, AGENT, com.opensocket.aievent.core.resourceaccess.contract.ResourceCategory.AGENT, AGENT_CONTROL, true, false, true, true, RESTRICTED, "Registered execution agent");
        add(map, AGENT_POOL, com.opensocket.aievent.core.resourceaccess.contract.ResourceCategory.AGENT, AGENT_CONTROL, true, true, true, false, INTERNAL, "Agent pool administration");
        add(map, SOURCE_SYSTEM, com.opensocket.aievent.core.resourceaccess.contract.ResourceCategory.CONFIGURATION, DISPATCH_CONFIGURATION, true, true, true, false, INTERNAL, "Tenant source-system configuration");
        add(map, EVENT, com.opensocket.aievent.core.resourceaccess.contract.ResourceCategory.EVENT, EVENT_PROCESSING, true, true, true, false, RESTRICTED, "Business event metadata and payload evidence");
        add(map, INCIDENT, com.opensocket.aievent.core.resourceaccess.contract.ResourceCategory.EVENT, EVENT_PROCESSING, true, true, true, false, CONFIDENTIAL, "Event-derived incident aggregate");
        add(map, DISPATCH_FLOW, com.opensocket.aievent.core.resourceaccess.contract.ResourceCategory.CONFIGURATION, DISPATCH_CONFIGURATION, true, true, true, false, INTERNAL, "Dispatch flow configuration");
        add(map, A2A_POLICY, com.opensocket.aievent.core.resourceaccess.contract.ResourceCategory.CONFIGURATION, A2A_DOMAIN, true, true, true, false, CONFIDENTIAL, "A2A governance policy");
        add(map, AGENT_SERVICE_SCOPE, com.opensocket.aievent.core.resourceaccess.contract.ResourceCategory.AGENT, AGENT_CONTROL, true, false, true, false, RESTRICTED, "Agent service scope");
        add(map, AGENT_CREDENTIAL_METADATA, com.opensocket.aievent.core.resourceaccess.contract.ResourceCategory.SECURITY, AGENT_CONTROL, true, false, true, true, SECRET, "Agent credential metadata only");
        add(map, ISSUE_CONNECTION, com.opensocket.aievent.core.resourceaccess.contract.ResourceCategory.INTEGRATION, ISSUE_TRACKING, true, true, true, false, RESTRICTED, "Issue provider connection");
        add(map, ISSUE_PRINCIPAL, com.opensocket.aievent.core.resourceaccess.contract.ResourceCategory.INTEGRATION, ISSUE_TRACKING, true, true, true, false, RESTRICTED, "Issue provider principal metadata");
        add(map, ISSUE_CREDENTIAL_METADATA, com.opensocket.aievent.core.resourceaccess.contract.ResourceCategory.SECURITY, ISSUE_TRACKING, true, false, true, false, SECRET, "Issue credential metadata only");
        add(map, ISSUE_PROJECT_MAPPING, com.opensocket.aievent.core.resourceaccess.contract.ResourceCategory.INTEGRATION, ISSUE_TRACKING, true, true, true, false, CONFIDENTIAL, "Provider project mapping");
        add(map, TASK_ISSUE_LINK, com.opensocket.aievent.core.resourceaccess.contract.ResourceCategory.ISSUE, ISSUE_TRACKING, true, true, true, false, CONFIDENTIAL, "Task-to-issue link");
        add(map, ISSUE_CONTEXT_SNAPSHOT, com.opensocket.aievent.core.resourceaccess.contract.ResourceCategory.ISSUE, ISSUE_TRACKING, true, true, true, false, CONFIDENTIAL, "Task-bound issue snapshot");
        add(map, ISSUE_ATTACHMENT, com.opensocket.aievent.core.resourceaccess.contract.ResourceCategory.ISSUE, ISSUE_TRACKING, true, true, true, false, RESTRICTED, "Issue attachment projection");
        add(map, ISSUE_CONFLICT, com.opensocket.aievent.core.resourceaccess.contract.ResourceCategory.ISSUE, ISSUE_TRACKING, true, true, true, false, RESTRICTED, "Issue synchronization conflict");
        add(map, ISSUE_DEAD_LETTER, com.opensocket.aievent.core.resourceaccess.contract.ResourceCategory.INTEGRATION, ISSUE_TRACKING, true, true, true, false, RESTRICTED, "Issue integration dead letter");
        add(map, ISSUE_TOPOLOGY, com.opensocket.aievent.core.resourceaccess.contract.ResourceCategory.ISSUE, ISSUE_TRACKING, true, true, true, false, CONFIDENTIAL, "Issue projection topology");
        add(map, TENANT, com.opensocket.aievent.core.resourceaccess.contract.ResourceCategory.IDENTITY, IAM, true, false, true, false, RESTRICTED, "Tenant administration");
        add(map, DEPARTMENT, com.opensocket.aievent.core.resourceaccess.contract.ResourceCategory.ORGANIZATION, ORGANIZATION_ACCESS, true, true, true, false, INTERNAL, "Department organization node");
        add(map, GROUP, com.opensocket.aievent.core.resourceaccess.contract.ResourceCategory.ORGANIZATION, ORGANIZATION_ACCESS, true, true, true, false, INTERNAL, "Organization access group");
        add(map, USER, com.opensocket.aievent.core.resourceaccess.contract.ResourceCategory.IDENTITY, IAM, true, false, true, false, RESTRICTED, "Human identity metadata");
        add(map, ROLE, com.opensocket.aievent.core.resourceaccess.contract.ResourceCategory.IDENTITY, IAM, true, false, true, false, RESTRICTED, "RBAC role metadata");
        add(map, SERVICE_ACCOUNT, com.opensocket.aievent.core.resourceaccess.contract.ResourceCategory.SECURITY, IAM, true, false, true, true, SECRET, "Service identity metadata");
        add(map, ACCESS_TOKEN_METADATA, com.opensocket.aievent.core.resourceaccess.contract.ResourceCategory.SECURITY, IAM, true, false, true, true, SECRET, "Access token metadata only");
        if (map.size() != ResourceType.values().length) throw new IllegalStateException("Resource catalog is incomplete");
        entries = Map.copyOf(map);
    }
    private static void add(EnumMap<ResourceType, ResourceCatalogEntry> map, ResourceType type,
            com.opensocket.aievent.core.resourceaccess.contract.ResourceCategory category,
            com.opensocket.aievent.core.resourceaccess.contract.DescriptorAuthority authority,
            boolean ownership, boolean participants, boolean visibility, boolean lease,
            com.opensocket.aievent.core.resourceaccess.contract.SensitivityLevel sensitivity, String description) {
        if (map.put(type, new ResourceCatalogEntry(type, category, authority, ownership, participants, visibility, lease, sensitivity, description)) != null)
            throw new IllegalStateException("Duplicate ResourceType: " + type);
    }
    @Override public Optional<ResourceCatalogEntry> find(ResourceType resourceType) { return Optional.ofNullable(entries.get(resourceType)); }
    @Override public Collection<ResourceCatalogEntry> entries() { return entries.values(); }
}
