package com.opensocket.aievent.core.agent.setup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentSetupStartCommand {
    private String runtimeType;
    private String gatewayUrl;
    private String command;
    private String dockerCommand;
    private String localCommand;
    private String remoteCommand;
    private String healthCheckCommand;
    private String logsCommand;
    private String verifyConnectionCommand;
    private List<String> expectedCapabilities = new ArrayList<>();
    private String capabilityEnvironmentVariable;
    private List<String> startupSteps = new ArrayList<>();
    private List<AgentSetupTroubleshootingStep> troubleshooting = new ArrayList<>();
    private Map<String, Object> environment = new LinkedHashMap<>();
    private Map<String, Object> diagnostics = new LinkedHashMap<>();

    public void setEnvironment(Map<String, Object> environment) {
        this.environment = environment == null ? new LinkedHashMap<>() : new LinkedHashMap<>(environment);
    }

    public void setDiagnostics(Map<String, Object> diagnostics) {
        this.diagnostics = diagnostics == null ? new LinkedHashMap<>() : new LinkedHashMap<>(diagnostics);
    }

    public void setExpectedCapabilities(List<String> expectedCapabilities) {
        this.expectedCapabilities = expectedCapabilities == null ? new ArrayList<>() : new ArrayList<>(expectedCapabilities);
    }

    public void setStartupSteps(List<String> startupSteps) {
        this.startupSteps = startupSteps == null ? new ArrayList<>() : new ArrayList<>(startupSteps);
    }

    public void setTroubleshooting(List<AgentSetupTroubleshootingStep> troubleshooting) {
        this.troubleshooting = troubleshooting == null ? new ArrayList<>() : new ArrayList<>(troubleshooting);
    }
}
