package com.opensocket.aievent.core.runtime;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Core-side runtime enforcement settings.
 *
 * <p>Governance state is owned by Core, but existing TCP/WebSocket sessions live in Netty.
 * These settings allow Core admin transitions such as disable/reject/revoke/suspend to
 * request a runtime disconnect from Netty after the governance transition is committed.</p>
 */
@ConfigurationProperties(prefix = "core.runtime-enforcement.disconnect")
public class RuntimeDisconnectProperties {
    /** Enables Core-initiated runtime disconnect after blocking governance transitions. */
    private boolean enabled = true;

    /** Default Netty Admin API base URL used when no node-specific URL is configured. */
    private String defaultGatewayBaseUrl = "http://localhost:18081";

    /** Optional owner-node base URL map, keyed by gateway node id. */
    private Map<String, String> gatewayBaseUrls = new LinkedHashMap<>();

    /** Enables discovery of gateway admin URLs from a Netty cluster registry endpoint. */
    private boolean gatewayRegistryEnabled = false;

    /** Optional Netty cluster gateway registry URL, for example http://node-001:18081/api/cluster/gateway-registry. */
    private String gatewayRegistryUrl = "";

    /** Admin/Internal token accepted by Netty Admin API. */
    private String adminToken = "dev-admin-token-change-me";

    /** Header used to pass the Netty Admin token. */
    private String adminTokenHeader = "Authorization";

    /** Prefix used when adminTokenHeader is Authorization. */
    private String authorizationScheme = "Bearer";

    private Duration requestTimeout = Duration.ofSeconds(3);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getDefaultGatewayBaseUrl() { return defaultGatewayBaseUrl; }
    public void setDefaultGatewayBaseUrl(String defaultGatewayBaseUrl) { this.defaultGatewayBaseUrl = defaultGatewayBaseUrl; }

    public Map<String, String> getGatewayBaseUrls() { return gatewayBaseUrls; }
    public void setGatewayBaseUrls(Map<String, String> gatewayBaseUrls) { this.gatewayBaseUrls = gatewayBaseUrls == null ? new LinkedHashMap<>() : new LinkedHashMap<>(gatewayBaseUrls); }

    public boolean isGatewayRegistryEnabled() { return gatewayRegistryEnabled; }
    public void setGatewayRegistryEnabled(boolean gatewayRegistryEnabled) { this.gatewayRegistryEnabled = gatewayRegistryEnabled; }

    public String getGatewayRegistryUrl() { return gatewayRegistryUrl; }
    public void setGatewayRegistryUrl(String gatewayRegistryUrl) { this.gatewayRegistryUrl = gatewayRegistryUrl == null ? "" : gatewayRegistryUrl.trim(); }

    public String getAdminToken() { return adminToken; }
    public void setAdminToken(String adminToken) { this.adminToken = adminToken; }

    public String getAdminTokenHeader() { return adminTokenHeader; }
    public void setAdminTokenHeader(String adminTokenHeader) { this.adminTokenHeader = adminTokenHeader; }

    public String getAuthorizationScheme() { return authorizationScheme; }
    public void setAuthorizationScheme(String authorizationScheme) { this.authorizationScheme = authorizationScheme; }

    public Duration getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout == null ? Duration.ofSeconds(3) : requestTimeout; }

    public String baseUrlFor(String gatewayNodeId) {
        List<String> candidates = candidateBaseUrlsFor(gatewayNodeId);
        return candidates.isEmpty() ? "" : candidates.get(0);
    }

    /**
     * Returns Netty Admin API candidate base URLs in owner-first order.
     *
     * <p>The local Netty disconnect endpoint is node-local. If Core calls a non-owner
     * node, Netty can legitimately return 404 because that node has no active channel
     * for the Agent. Therefore the Core client must try the owner URL first and then
     * fall back to the default URL and all configured gateway URLs.</p>
     */
    public List<String> candidateBaseUrlsFor(String gatewayNodeId) {
        List<String> candidates = new ArrayList<>();
        if (gatewayNodeId != null && !gatewayNodeId.isBlank()) {
            addCandidate(candidates, gatewayBaseUrls.get(gatewayNodeId));
        }
        addCandidate(candidates, defaultGatewayBaseUrl);
        for (String value : gatewayBaseUrls.values()) {
            addCandidate(candidates, value);
        }
        return candidates;
    }

    private void addCandidate(List<String> candidates, String value) {
        String normalized = trimTrailingSlash(value);
        if (normalized.isBlank() || candidates.contains(normalized)) return;
        candidates.add(normalized);
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) return "";
        String result = value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }
}
