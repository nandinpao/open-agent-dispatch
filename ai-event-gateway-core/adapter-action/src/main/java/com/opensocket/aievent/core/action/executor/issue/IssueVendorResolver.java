package com.opensocket.aievent.core.action.executor.issue;

import java.util.Locale;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.action.AdapterAction;
import com.opensocket.aievent.core.action.executor.AdapterActionExecutionProperties;

@Component
public class IssueVendorResolver {
    private final AdapterActionExecutionProperties properties;

    public IssueVendorResolver(AdapterActionExecutionProperties properties) {
        this.properties = properties;
    }

    public IssueVendor resolve(AdapterAction action) {
        IssueVendor fromPayload = parse(action == null || action.getPayload() == null
                ? null
                : action.getPayload().get("vendor"));
        if (fromPayload != null) return fromPayload;

        IssueVendor fromAdapterName = parse(action == null ? null : action.getAdapterName());
        if (fromAdapterName != null) return fromAdapterName;

        return parse(properties.getIssue().getDefaultVendor());
    }

    private IssueVendor parse(Object value) {
        if (value == null) return null;
        String raw = String.valueOf(value);
        if (raw.isBlank()) return null;
        String normalized = raw.toUpperCase(Locale.ROOT);
        if (normalized.contains("JIRA")) return IssueVendor.JIRA;
        if (normalized.contains("REDMINE")) return IssueVendor.REDMINE;
        if (normalized.contains("GITLAB")) return IssueVendor.GITLAB;
        if (normalized.contains("MOCK")) return IssueVendor.MOCK;
        return null;
    }
}
