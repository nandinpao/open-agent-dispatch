package com.opensocket.aievent.core.routing.governance.routing;

import java.util.LinkedHashSet;
import java.util.Set;

import com.opensocket.aievent.core.agent.AgentSnapshot;

public class GenericCandidateAgent {
    private final String agentId;
    private AgentSnapshot runtime;
    private final LinkedHashSet<CandidatePoolOrigin> origins = new LinkedHashSet<>();
    public GenericCandidateAgent(String agentId){this.agentId=agentId;}
    public String getAgentId(){return agentId;}
    public AgentSnapshot getRuntime(){return runtime;} public void setRuntime(AgentSnapshot v){runtime=v;}
    public Set<CandidatePoolOrigin> getOrigins(){return Set.copyOf(origins);}
    public void addOrigin(CandidatePoolOrigin origin){if(origin!=null) origins.add(origin);}
}
