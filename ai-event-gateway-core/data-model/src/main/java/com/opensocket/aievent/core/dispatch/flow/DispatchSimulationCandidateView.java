package com.opensocket.aievent.core.dispatch.flow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Candidate evidence projected by no-side-effect Dispatch Simulation. */
public class DispatchSimulationCandidateView {
    private String agentId;
    private String status;
    private Integer score;
    private Boolean eligible = false;
    private Boolean selected = false;
    private List<String> blockingReasons = new ArrayList<>();
    private String reason;
    private Map<String, Object> scoreBreakdown = new LinkedHashMap<>();

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public Boolean getEligible() { return eligible; }
    public void setEligible(Boolean eligible) { this.eligible = eligible; }
    public Boolean getSelected() { return selected; }
    public void setSelected(Boolean selected) { this.selected = selected; }
    public List<String> getBlockingReasons() { return blockingReasons; }
    public void setBlockingReasons(List<String> blockingReasons) { this.blockingReasons = blockingReasons == null ? new ArrayList<>() : new ArrayList<>(blockingReasons); }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Map<String, Object> getScoreBreakdown() { return scoreBreakdown; }
    public void setScoreBreakdown(Map<String, Object> scoreBreakdown) { this.scoreBreakdown = scoreBreakdown == null ? new LinkedHashMap<>() : new LinkedHashMap<>(scoreBreakdown); }
}
