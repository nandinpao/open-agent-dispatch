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
    private final FingerprintPolicyProperties properties;
    private final List<CompiledRule> compiledRules;

    @Autowired
    public DynamicTokenMasker(FingerprintPolicyProperties properties) {
        this.properties = properties;
        this.compiledRules = compile(properties);
    }

    public DynamicTokenMasker() {
        this(new FingerprintPolicyProperties());
    }

    public String mask(String normalizedMessage) {
        if (normalizedMessage == null || normalizedMessage.isBlank()) {
            return "";
        }
        String value = normalizedMessage.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        FingerprintPolicyProperties.MessageMasking masking = properties.getMasking();
        if (masking == null || !masking.isEnabled()) {
            return value;
        }
        for (CompiledRule rule : compiledRules) {
            value = rule.pattern().matcher(value).replaceAll(Matcher.quoteReplacement(rule.replacement()));
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private List<CompiledRule> compile(FingerprintPolicyProperties properties) {
        List<CompiledRule> rules = new ArrayList<>();
        FingerprintPolicyProperties.MessageMasking masking = properties.getMasking();
        if (masking == null || masking.getRules() == null) {
            return List.of();
        }
        for (FingerprintPolicyProperties.MaskRule rule : masking.getRules()) {
            if (rule == null || rule.getRegex() == null || rule.getRegex().isBlank()) {
                continue;
            }
            String replacement = rule.getReplacement();
            if (replacement == null || replacement.isBlank()) {
                replacement = masking.getReplacementToken() == null ? "<var>" : masking.getReplacementToken();
            }
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
