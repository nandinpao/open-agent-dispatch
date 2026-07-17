package com.opensocket.aievent.core.agent.governance;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgentConnectionAuthorizationRequest {
    private String gatewayNodeId;
    private String claimedAgentId;
    private String agentId;
    private String agentSessionId;
    private String credentialToken;
    private String credentialHash;
    private String publicKeyFingerprint;
    private String fingerprint;
    private String remoteAddress;
    private String transport;
    private List<String> capabilities = List.of();
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public String effectiveAgentId() {
        return !blank(agentId) ? agentId : claimedAgentId;
    }

    public String getGatewayNodeId() { return gatewayNodeId; }
    public void setGatewayNodeId(String gatewayNodeId) { this.gatewayNodeId = gatewayNodeId; }
    public String getClaimedAgentId() { return claimedAgentId; }
    public void setClaimedAgentId(String claimedAgentId) { this.claimedAgentId = claimedAgentId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getAgentSessionId() { return agentSessionId; }
    public void setAgentSessionId(String agentSessionId) { this.agentSessionId = agentSessionId; }
    public String getCredentialToken() { return credentialToken; }
    public void setCredentialToken(String credentialToken) { this.credentialToken = credentialToken; }
    /** Backward-compatible alias for older Netty clients that used `credential`. */
    public String getCredential() { return credentialToken; }
    public void setCredential(String credential) { if (blank(this.credentialToken)) this.credentialToken = credential; }
    public String getCredentialHash() { return credentialHash; }
    public void setCredentialHash(String credentialHash) { this.credentialHash = credentialHash; }
    public String getPublicKeyFingerprint() { return publicKeyFingerprint; }
    public void setPublicKeyFingerprint(String publicKeyFingerprint) { this.publicKeyFingerprint = publicKeyFingerprint; }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
        if (blank(this.publicKeyFingerprint)) this.publicKeyFingerprint = fingerprint;
    }
    public String getRemoteAddress() { return remoteAddress; }
    public void setRemoteAddress(String remoteAddress) { this.remoteAddress = remoteAddress; }
    public String getTransport() { return transport; }
    public void setTransport(String transport) { this.transport = transport; }
    public List<String> getCapabilities() { return capabilities; }
    public void setCapabilities(List<String> capabilities) { this.capabilities = capabilities == null ? List.of() : List.copyOf(capabilities); }
    /** Backward-compatible alias for Netty authorization request bodies. */
    public List<String> getClaimedCapabilities() { return capabilities; }
    public void setClaimedCapabilities(List<String> claimedCapabilities) { setCapabilities(claimedCapabilities); }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata); }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
