package com.opensocket.aievent.core.observability;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class OperationalSummary {
    private OffsetDateTime generatedAt;
    private int sampleLimit;
    private Map<String, Object> stores = new LinkedHashMap<>();
    private Map<String, Integer> incidents = new LinkedHashMap<>();
    private Map<String, Integer> tasks = new LinkedHashMap<>();
    private Map<String, Integer> dispatchRequests = new LinkedHashMap<>();
    private Map<String, Integer> adapterActions = new LinkedHashMap<>();
    private Map<String, Integer> moduleEvents = new LinkedHashMap<>();
    private Map<String, Integer> integrationEvents = new LinkedHashMap<>();
    private Map<String, Integer> agents = new LinkedHashMap<>();
    private Map<String, Integer> gatewayNodes = new LinkedHashMap<>();
    private Map<String, Object> riskSignals = new LinkedHashMap<>();
    private Map<String, Object> sloMetrics = new LinkedHashMap<>();

    public OffsetDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(OffsetDateTime generatedAt) { this.generatedAt = generatedAt; }
    public int getSampleLimit() { return sampleLimit; }
    public void setSampleLimit(int sampleLimit) { this.sampleLimit = sampleLimit; }
    public Map<String, Object> getStores() { return stores; }
    public void setStores(Map<String, Object> stores) { this.stores = stores == null ? new LinkedHashMap<>() : stores; }
    public Map<String, Integer> getIncidents() { return incidents; }
    public void setIncidents(Map<String, Integer> incidents) { this.incidents = incidents == null ? new LinkedHashMap<>() : incidents; }
    public Map<String, Integer> getTasks() { return tasks; }
    public void setTasks(Map<String, Integer> tasks) { this.tasks = tasks == null ? new LinkedHashMap<>() : tasks; }
    public Map<String, Integer> getDispatchRequests() { return dispatchRequests; }
    public void setDispatchRequests(Map<String, Integer> dispatchRequests) { this.dispatchRequests = dispatchRequests == null ? new LinkedHashMap<>() : dispatchRequests; }
    public Map<String, Integer> getAdapterActions() { return adapterActions; }
    public void setAdapterActions(Map<String, Integer> adapterActions) { this.adapterActions = adapterActions == null ? new LinkedHashMap<>() : adapterActions; }
    public Map<String, Integer> getModuleEvents() { return moduleEvents; }
    public void setModuleEvents(Map<String, Integer> moduleEvents) { this.moduleEvents = moduleEvents == null ? new LinkedHashMap<>() : moduleEvents; }
    public Map<String, Integer> getIntegrationEvents() { return integrationEvents; }
    public void setIntegrationEvents(Map<String, Integer> integrationEvents) { this.integrationEvents = integrationEvents == null ? new LinkedHashMap<>() : integrationEvents; }
    public Map<String, Integer> getAgents() { return agents; }
    public void setAgents(Map<String, Integer> agents) { this.agents = agents == null ? new LinkedHashMap<>() : agents; }
    public Map<String, Integer> getGatewayNodes() { return gatewayNodes; }
    public void setGatewayNodes(Map<String, Integer> gatewayNodes) { this.gatewayNodes = gatewayNodes == null ? new LinkedHashMap<>() : gatewayNodes; }
    public Map<String, Object> getRiskSignals() { return riskSignals; }
    public void setRiskSignals(Map<String, Object> riskSignals) { this.riskSignals = riskSignals == null ? new LinkedHashMap<>() : riskSignals; }
    public Map<String, Object> getSloMetrics() { return sloMetrics; }
    public void setSloMetrics(Map<String, Object> sloMetrics) { this.sloMetrics = sloMetrics == null ? new LinkedHashMap<>() : sloMetrics; }
}
