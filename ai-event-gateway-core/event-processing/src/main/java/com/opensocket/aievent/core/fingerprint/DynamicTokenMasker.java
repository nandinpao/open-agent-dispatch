package com.opensocket.aievent.core.fingerprint;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Component;

/** Normalizes dynamic tokens in messages before they are used for fingerprinting. */
@Component
public class DynamicTokenMasker {
    private final FingerprintRuntimeConfigurationView runtimeConfiguration;
    private volatile String compiledVersion = "";
    private volatile List<CompiledRule> compiledRules = List.of();

    @Autowired
    public DynamicTokenMasker(FingerprintRuntimeConfigurationView runtimeConfiguration) {
        this.runtimeConfiguration = runtimeConfiguration;
    }

    public DynamicTokenMasker(FingerprintPolicyProperties properties) {
        this(new FingerprintRuntimeConfigurationView(properties));
    }

    public DynamicTokenMasker() {
        this(new FingerprintRuntimeConfigurationView(new FingerprintPolicyProperties()));
    }

    public String mask(String normalizedMessage) {
        if (normalizedMessage == null || normalizedMessage.isBlank()) {
            return "";
        }
        String value = normalizedMessage.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        if (!runtimeConfiguration.maskingEnabled()) {
            return value;
        }
        for (CompiledRule rule : compiledRules()) {
            value = rule.pattern().matcher(value).replaceAll(Matcher.quoteReplacement(rule.replacement()));
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private List<CompiledRule> compiledRules() {
        String version = runtimeConfiguration.snapshotVersionToken();
        List<CompiledRule> local = compiledRules;
        if (version.equals(compiledVersion) && !local.isEmpty()) return local;
        synchronized (this) {
            if (!version.equals(compiledVersion) || compiledRules.isEmpty()) {
                compiledRules = compile(runtimeConfiguration.maskingRules(), runtimeConfiguration.replacementToken());
                compiledVersion = version;
            }
            return compiledRules;
        }
    }

    private List<CompiledRule> compile(List<FingerprintPolicyProperties.MaskRule> configuredRules, String replacementToken) {
        List<CompiledRule> rules = new ArrayList<>();
        if (configuredRules == null) return List.of();
        for (FingerprintPolicyProperties.MaskRule rule : configuredRules) {
            if (rule == null || rule.getRegex() == null || rule.getRegex().isBlank()) continue;
            String replacement = rule.getReplacement();
            if (replacement == null || replacement.isBlank()) replacement = replacementToken == null ? "<var>" : replacementToken;
            try {
                rules.add(new CompiledRule(Pattern.compile(rule.getRegex(), Pattern.CASE_INSENSITIVE), replacement));
            } catch (PatternSyntaxException ex) {
                throw new IllegalArgumentException("Invalid fingerprint masking regex for rule '" + rule.getName() + "': " + rule.getRegex(), ex);
            }
        }
        return List.copyOf(rules);
    }

    private record CompiledRule(Pattern pattern, String replacement) {
    }
}
