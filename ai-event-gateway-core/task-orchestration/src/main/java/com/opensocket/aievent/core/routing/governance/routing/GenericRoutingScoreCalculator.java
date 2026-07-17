package com.opensocket.aievent.core.routing.governance.routing;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.routing.governance.GenericRoutingStrategy;
import com.opensocket.aievent.core.routing.governance.TaskRequirementEvidence;
import com.opensocket.aievent.core.task.TaskRecord;

@Component
public class GenericRoutingScoreCalculator {
    public GenericRoutingScore score(GenericRoutingStrategy strategy, TaskRecord task, TaskRequirementEvidence requirement, AgentSnapshot agent) {
        GenericRoutingStrategy effective=strategy==null?GenericRoutingStrategy.WEIGHTED_SCORE:strategy;
        int health=agent==null?0:agent.getHealthScore();
        int capacity=capacityScore(agent);
        int locality=localityScore(task,agent);
        int capability=requirement!=null && !requirement.getRequiredCapabilities().isEmpty()?100:50;
        LinkedHashMap<String,Object> breakdown=new LinkedHashMap<>();
        breakdown.put("health",health); breakdown.put("capacity",capacity); breakdown.put("locality",locality); breakdown.put("capability",capability);
        int score=switch(effective){
            case LEGACY,WEIGHTED_SCORE -> weighted(health,capacity,locality,capability);
            case CAPABILITY_FIRST -> weightedParts(capability,55,health,20,capacity,20,locality,5);
            case LOWEST_LOAD -> weightedParts(capacity,70,health,25,locality,5,0,0);
            case LOCAL_FIRST -> weightedParts(locality,70,capacity,20,health,10,0,0);
            case ROUND_ROBIN -> roundRobin(task,agent);
            case MANUAL_REVIEW -> 0;
        };
        breakdown.put("strategy",effective.name()); breakdown.put("score",score);
        if(effective==GenericRoutingStrategy.ROUND_ROBIN) breakdown.put("deterministicRotation",score);
        if(effective==GenericRoutingStrategy.MANUAL_REVIEW) breakdown.put("manualReviewRequired",true);
        return new GenericRoutingScore(score,breakdown);
    }
    private static int weighted(int h,int c,int l,int cap){return weightedParts(cap,30,c,30,h,25,l,15);}
    private static int weightedParts(int a,int aw,int b,int bw,int c,int cw,int d,int dw){return (a*aw+b*bw+c*cw+d*dw)/Math.max(1,aw+bw+cw+dw);}
    private static int capacityScore(AgentSnapshot a){
        if(a==null) return 0; int max=Math.max(1,a.getMaxConcurrentTasks()); int used=Math.max(0,a.getEffectiveTaskCount());
        double util=a.getCapacityUtilization()>0?a.getCapacityUtilization():Math.min(1d,(double)used/max); return (int)Math.round((1d-util)*100d);
    }
    private static int localityScore(TaskRecord task,AgentSnapshot agent){
        if(task==null||agent==null||blank(task.getSiteId())||blank(agent.getSiteId())) return 50; return task.getSiteId().trim().equalsIgnoreCase(agent.getSiteId().trim())?100:0;
    }
    private static int roundRobin(TaskRecord task,AgentSnapshot agent){return Math.floorMod(Objects.hash(task==null?null:task.getTaskId(),agent==null?null:agent.getAgentId()),100);}
    private static boolean blank(String v){return v==null||v.isBlank();}
}
