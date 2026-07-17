package com.opensocket.aievent.gateway.netty.agent;

import com.opensocket.aievent.gateway.netty.agent.dto.AgentRegisterPayload;
import com.opensocket.aievent.gateway.netty.config.AgentProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;

/**
 * Validates the lightweight Agent onboarding token used by TCP and WebSocket Agent connectors.
 *
 * <p>P8 intentionally keeps this as a static-token scaffold. It prevents accidental open Agent
 * registration in production while leaving room for mTLS, OAuth2 client credentials, signed Agent
 * manifests, or per-Agent secrets in later phases.</p>
 */
@Component
public class AgentOnboardingTokenValidator {

    private static final String[] METADATA_TOKEN_KEYS = {"onboardingToken", "agentToken", "token"};
    private static final String[] SENSITIVE_METADATA_KEYS = {"credentialToken", "credential", "authToken", "authorization", "auth"};

    private final AgentProperties agentProperties;

    public AgentOnboardingTokenValidator(AgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    public boolean authEnabled() {
        return agentProperties.authEnabled();
    }

    public boolean webSocketHandshakeAuthEnabled() {
        return agentProperties.webSocketHandshakeAuthEnabled();
    }

    public boolean configured() {
        return hasText(agentProperties.onboardingToken()) || agentProperties.additionalOnboardingTokens().stream().anyMatch(this::hasText);
    }

    public Optional<String> validationFailure(AgentRegisterPayload payload, boolean alreadyAuthenticated) {
        if (!authEnabled()) {
            return Optional.empty();
        }
        if (alreadyAuthenticated) {
            return Optional.empty();
        }
        if (!configured()) {
            return Optional.of("AGENT_ONBOARDING_TOKEN is not configured while AGENT_AUTH_ENABLED=true");
        }
        var providedToken = resolveToken(payload);
        if (!matchesAnyConfiguredToken(providedToken)) {
            return Optional.of("missing or invalid Agent onboarding token");
        }
        return Optional.empty();
    }

    public boolean isAuthorizedToken(String providedToken) {
        if (!authEnabled()) {
            return true;
        }
        if (!configured()) {
            return false;
        }
        return matchesAnyConfiguredToken(providedToken);
    }

    public String resolveToken(AgentRegisterPayload payload) {
        if (payload == null) {
            return "";
        }
        if (payload.onboardingToken() != null && !payload.onboardingToken().isBlank()) {
            return payload.onboardingToken().trim();
        }
        return resolveToken(payload.metadata());
    }

    public String resolveToken(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "";
        }
        for (String key : METADATA_TOKEN_KEYS) {
            var value = metadata.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString().trim();
            }
        }
        return "";
    }

    public static boolean isSensitiveMetadataKey(String key) {
        if (key == null) {
            return false;
        }
        for (String tokenKey : METADATA_TOKEN_KEYS) {
            if (tokenKey.equalsIgnoreCase(key)) {
                return true;
            }
        }
        for (String tokenKey : SENSITIVE_METADATA_KEYS) {
            if (tokenKey.equalsIgnoreCase(key)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesAnyConfiguredToken(String providedToken) {
        if (constantTimeEquals(agentProperties.onboardingToken(), providedToken)) {
            return true;
        }
        return agentProperties.additionalOnboardingTokens().stream()
                .anyMatch(token -> constantTimeEquals(token, providedToken));
    }

    private boolean constantTimeEquals(String expected, String provided) {
        if (!hasText(expected) || !hasText(provided)) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8)
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
