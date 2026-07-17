package com.opensocket.aievent.core.fingerprint;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable fingerprint policy for Phase 2 event deduplication.
 *
 * <p>The goal is to prevent ERP/MES/BPM logs that describe the same root cause
 * from being split into many incidents only because the message contains dynamic
 * order numbers, timestamps, batch numbers, UUIDs, IPs, or other transient IDs.</p>
 */
@ConfigurationProperties(prefix = "core.fingerprint")
public class FingerprintPolicyProperties {
    private boolean enabled = true;
    private String policyVersion = "v2";
    private List<String> defaultFields = new ArrayList<>(List.of(
            "tenantId",
            "sourceSystem",
            "siteId",
            "plantId",
            "objectType",
            "objectId",
            "eventType",
            "errorCode",
            "maskedMessage"
    ));
    private MessageMasking masking = new MessageMasking();
    private List<Policy> policies = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(String policyVersion) { this.policyVersion = policyVersion; }
    public List<String> getDefaultFields() { return defaultFields; }
    public void setDefaultFields(List<String> defaultFields) { this.defaultFields = defaultFields; }
    public MessageMasking getMasking() { return masking; }
    public void setMasking(MessageMasking masking) { this.masking = masking; }
    public List<Policy> getPolicies() { return policies; }
    public void setPolicies(List<Policy> policies) { this.policies = policies; }

    public static class MessageMasking {
        private boolean enabled = true;
        private String replacementToken = "<var>";
        private List<MaskRule> rules = new ArrayList<>(List.of(
                new MaskRule("uuid", "\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b", "<uuid>"),
                new MaskRule("iso-date-time", "\\b\\d{4}-\\d{2}-\\d{2}[t\\s]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:z|[+-]\\d{2}:?\\d{2})?\\b", "<datetime>"),
                new MaskRule("date", "\\b\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}\\b", "<date>"),
                new MaskRule("time", "\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\b", "<time>"),
                new MaskRule("ip-address", "\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b", "<ip>"),
                new MaskRule("hex-hash", "\\b[0-9a-f]{16,}\\b", "<hex>"),
                new MaskRule("erp-order-like-id", "\\b[a-z]{2,}-?\\d{4,}\\b", "<id>"),
                new MaskRule("long-number", "\\b\\d{4,}\\b", "<num>")
        ));

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getReplacementToken() { return replacementToken; }
        public void setReplacementToken(String replacementToken) { this.replacementToken = replacementToken; }
        public List<MaskRule> getRules() { return rules; }
        public void setRules(List<MaskRule> rules) { this.rules = rules; }
    }

    public static class MaskRule {
        private String name;
        private String regex;
        private String replacement;

        public MaskRule() {
        }

        public MaskRule(String name, String regex, String replacement) {
            this.name = name;
            this.regex = regex;
            this.replacement = replacement;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getRegex() { return regex; }
        public void setRegex(String regex) { this.regex = regex; }
        public String getReplacement() { return replacement; }
        public void setReplacement(String replacement) { this.replacement = replacement; }
    }

    public static class Policy {
        private String name;
        private List<String> sourceSystems = new ArrayList<>();
        private List<String> eventTypes = new ArrayList<>();
        private List<String> objectTypes = new ArrayList<>();
        private List<String> errorCodes = new ArrayList<>();
        private List<String> fields = new ArrayList<>();

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public List<String> getSourceSystems() { return sourceSystems; }
        public void setSourceSystems(List<String> sourceSystems) { this.sourceSystems = sourceSystems; }
        public List<String> getEventTypes() { return eventTypes; }
        public void setEventTypes(List<String> eventTypes) { this.eventTypes = eventTypes; }
        public List<String> getObjectTypes() { return objectTypes; }
        public void setObjectTypes(List<String> objectTypes) { this.objectTypes = objectTypes; }
        public List<String> getErrorCodes() { return errorCodes; }
        public void setErrorCodes(List<String> errorCodes) { this.errorCodes = errorCodes; }
        public List<String> getFields() { return fields; }
        public void setFields(List<String> fields) { this.fields = fields; }
    }
}
