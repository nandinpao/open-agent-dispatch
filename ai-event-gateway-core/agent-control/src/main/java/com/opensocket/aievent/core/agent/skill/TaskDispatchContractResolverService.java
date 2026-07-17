package com.opensocket.aievent.core.agent.skill;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

@Service
public class TaskDispatchContractResolverService {
    private final AgentSkillRegistryService skillRegistryService;

    public TaskDispatchContractResolverService(AgentSkillRegistryService skillRegistryService) {
        this.skillRegistryService = skillRegistryService;
    }

    public TaskDispatchContractResolveResult resolve(TaskDispatchContractResolveRequest input) {
        TaskDispatchContractResolveRequest request = input == null ? new TaskDispatchContractResolveRequest() : input;
        LinkedHashSet<String> capabilities = new LinkedHashSet<>(normalizeList(request.getRequiredCapabilities()));
        LinkedHashSet<String> explicitDispatchCapabilities = explicitDispatchCapabilities(capabilities);
        boolean directCapabilityAuthority = !explicitDispatchCapabilities.isEmpty();
        LinkedHashSet<String> dataClasses = new LinkedHashSet<>(normalizeList(request.getDataClasses()));
        List<String> reasons = new ArrayList<>();

        String domain = firstNonBlank(normalize(request.getDomain()), prefixed(capabilities, "DOMAIN"), metadata(request.getPayloadMetadata(), "domain"));
        String provider = firstNonBlank(normalize(request.getProvider()), prefixed(capabilities, "PROVIDER"), metadata(request.getPayloadMetadata(), "provider"), metadata(request.getPayloadMetadata(), "sourceSystem"));
        String taskType = firstNonBlank(normalize(request.getTaskType()), knownTaskFromCapabilities(capabilities), metadata(request.getPayloadMetadata(), "taskType"));
        String operation = firstNonBlank(normalize(request.getOperation()), prefixed(capabilities, "OPERATION"), metadata(request.getPayloadMetadata(), "operation"), "ANALYZE");
        String toolPolicy = firstNonBlank(normalize(request.getRequiredToolPolicy()), prefixed(capabilities, "TOOL_POLICY"), prefixed(capabilities, "POLICY"), metadata(request.getPayloadMetadata(), "requiredToolPolicy"));
        String siteCode = firstNonBlank(normalize(request.getSiteCode()), normalize(request.getPlantId()), metadata(request.getPayloadMetadata(), "siteCode"), metadata(request.getPayloadMetadata(), "site"));
        addPrefixed(dataClasses, capabilities, "DATA_CLASS");
        addPrefixed(dataClasses, capabilities, "DATA");
        addMetadataList(dataClasses, request.getPayloadMetadata(), "dataClasses");

        final String candidateDomain = domain;
        final String candidateTaskType = taskType;
        final String candidateProvider = provider;
        final String candidateOperation = operation;
        final String candidateToolPolicy = toolPolicy;
        List<AgentSkillDefinition> matched = skillRegistryService.search(candidateDomain, true).stream()
                .filter(skill -> matches(skill, candidateTaskType, candidateProvider, candidateOperation, candidateToolPolicy, capabilities))
                .toList();
        if (!matched.isEmpty()) {
            AgentSkillDefinition first = matched.get(0);
            domain = firstNonBlank(domain, normalize(first.getDomain()));
            taskType = firstNonBlank(taskType, first(first.getTaskTypes()), normalize(first.getSkillCode()));
            provider = firstNonBlank(provider, first(first.getProviders()));
            operation = firstNonBlank(operation, first(first.getOperations()));
            toolPolicy = firstNonBlank(toolPolicy, first(first.getToolPolicies()));
            if (!directCapabilityAuthority) {
                capabilities.add(normalize(first.getSkillCode()));
                capabilities.addAll(normalizeList(first.getTaskTypes()));
            }
            dataClasses.addAll(normalizeList(first.getDataClasses()));
            reasons.add("matchedSkillRegistry=" + first.getSkillCode());
        }

        List<String> matchedSkillCodes = directCapabilityAuthority
                ? explicitDispatchCapabilities.stream().toList()
                : matched.stream().map(AgentSkillDefinition::getSkillCode).distinct().toList();
        if (directCapabilityAuthority) {
            capabilities.clear();
            capabilities.addAll(explicitDispatchCapabilities);
            reasons.add("contractSource=ADMIN_MANAGED_DIRECT_CAPABILITY");
        }
        if (domain != null) reasons.add("domain=" + domain);
        if (provider != null) reasons.add("provider=" + provider);
        if (taskType != null) reasons.add("taskType=" + taskType);
        if (operation != null) reasons.add("operation=" + operation);
        if (toolPolicy != null) reasons.add("toolPolicy=" + toolPolicy);

        TaskDispatchContractResolveResult result = new TaskDispatchContractResolveResult();
        result.setTaxonomyVersion(AgentSkillRegistryService.TAXONOMY_VERSION);
        result.setDomain(domain);
        result.setProvider(provider);
        result.setTaskType(taskType);
        result.setOperation(operation);
        result.setRequiredToolPolicy(toolPolicy);
        result.setSiteCode(siteCode);
        result.setRequiredCapabilities(new ArrayList<>(capabilities));
        result.setDataClasses(new ArrayList<>(dataClasses));
        result.setMatchedSkillCodes(matchedSkillCodes);
        result.setResolutionReasons(reasons);
        result.setResolved(taskType != null || domain != null || !capabilities.isEmpty() || !matched.isEmpty() || !matchedSkillCodes.isEmpty());
        result.setResolvedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return result;
    }

    private LinkedHashSet<String> explicitDispatchCapabilities(Set<String> capabilities) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (capabilities == null) return values;
        for (String capability : capabilities) {
            String normalized = normalize(capability);
            if (normalized == null || normalized.isBlank()) continue;
            if (isMetadataCapability(normalized) || isSkillVersionHint(normalized)) continue;
            values.add(normalized);
        }
        return values;
    }

    private boolean isMetadataCapability(String value) {
        if (value == null) return false;
        return value.startsWith("DOMAIN:")
                || value.startsWith("PROVIDER:")
                || value.startsWith("OPERATION:")
                || value.startsWith("TOOL_POLICY:")
                || value.startsWith("POLICY:")
                || value.startsWith("DATA_CLASS:")
                || value.startsWith("DATA:");
    }


    private boolean isSkillVersionHint(String value) {
        return value != null && (value.startsWith("SKILL_VERSION:") || value.contains("@"));
    }

    private boolean matches(AgentSkillDefinition skill, String taskType, String provider, String operation, String toolPolicy, Set<String> capabilities) {
        if (skill == null || !skill.isEnabled()) return false;
        String skillCode = normalize(skill.getSkillCode());
        if (capabilities.contains(skillCode)) return true;
        if (intersects(skill.getTaskTypes(), capabilities)) return true;
        if (taskType != null && contains(skill.getTaskTypes(), taskType)) return true;
        if (provider != null && contains(skill.getProviders(), provider) && operation != null && contains(skill.getOperations(), operation)) return true;
        if (toolPolicy != null && contains(skill.getToolPolicies(), toolPolicy) && taskType != null && contains(skill.getTaskTypes(), taskType)) return true;
        return false;
    }


    private String knownTaskFromCapabilities(Set<String> capabilities) {
        if (capabilities == null) return null;
        for (AgentSkillDefinition skill : skillRegistryService.search(null, true)) {
            String skillCode = normalize(skill.getSkillCode());
            if (capabilities.contains(skillCode)) return firstNonBlank(first(skill.getTaskTypes()), skillCode);
            for (String taskType : normalizeList(skill.getTaskTypes())) {
                if (capabilities.contains(taskType)) return taskType;
            }
        }
        return null;
    }


    private String prefixed(Set<String> values, String prefix) {
        if (values == null) return null;
        String normalizedPrefix = normalize(prefix);
        for (String value : values) {
            if (value != null && value.startsWith(normalizedPrefix + ":")) {
                return normalize(value.substring((normalizedPrefix + ":").length()));
            }
        }
        return null;
    }

    private void addPrefixed(Set<String> target, Set<String> source, String prefix) {
        String value = prefixed(source, prefix);
        if (value != null) target.add(value);
    }

    private void addMetadataList(Set<String> target, Map<String, Object> metadata, String key) {
        if (target == null || metadata == null || !metadata.containsKey(key)) return;
        Object value = metadata.get(key);
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) if (item != null) target.add(normalize(item.toString()));
        } else if (value != null) {
            target.add(normalize(value.toString()));
        }
    }

    private String metadata(Map<String, Object> metadata, String key) {
        if (metadata == null || key == null || !metadata.containsKey(key) || metadata.get(key) == null) return null;
        return normalize(metadata.get(key).toString());
    }

    private boolean intersects(List<String> values, Set<String> candidates) {
        if (values == null || candidates == null) return false;
        for (String value : values) if (candidates.contains(normalize(value))) return true;
        return false;
    }

    private boolean contains(List<String> values, String candidate) {
        String normalized = normalize(candidate);
        return values != null && normalized != null && values.stream().map(this::normalize).anyMatch(normalized::equals);
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(value -> !blank(value)).map(this::normalize).distinct().toList();
    }

    private String first(List<String> values) {
        return values == null || values.isEmpty() ? null : normalize(values.get(0));
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) if (!blank(value)) return value;
        return null;
    }

    private String normalize(String value) {
        return blank(value) ? null : value.trim().replace('-', '_').replace('.', '_').toUpperCase(Locale.ROOT);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
