package com.opensocket.aievent.core.capability;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure deterministic Stage 9 fan-in evaluator. No routing/provider authority is evaluated here. */
public final class PlanExecutionConvergenceEvaluator {
    private PlanExecutionConvergenceEvaluator() {}

    public static Decision evaluate(String policy, Integer quorumRequired, List<StepState> steps, List<Map<String,Object>> artifactOutputs) {
        String p = policy == null || policy.isBlank() ? "ALL_REQUIRED" : policy.trim().toUpperCase();
        if (!Set.of("ALL_REQUIRED","QUORUM","BEST_EFFORT").contains(p)) throw new IllegalArgumentException("Unsupported completion policy: "+p);
        List<StepState> ss = steps == null ? List.of() : List.copyOf(steps);
        int required=0, successRequired=0, failedRequired=0, success=0, nonSuccess=0;
        for (StepState s:ss) {
            if (s.required()) required++;
            if ("SUCCEEDED".equals(s.state())) { success++; if(s.required()) successRequired++; }
            else { nonSuccess++; if(s.required()) failedRequired++; }
        }
        List<Map<String,Object>> conflicts = conflicts(artifactOutputs);
        List<String> reasons = new ArrayList<>();
        String outcome;
        if (!conflicts.isEmpty()) {
            outcome="EVIDENCE_CONFLICT"; reasons.add("STRUCTURED_EVIDENCE_CONFLICT");
        } else if ("QUORUM".equals(p)) {
            int q = quorumRequired == null ? Math.max(1, required) : quorumRequired;
            if (q < 1 || q > Math.max(1,required)) throw new IllegalArgumentException("quorumRequired must be between 1 and required step count");
            if (successRequired >= q) {
                outcome = (failedRequired>0 || nonSuccess>0) ? "PROVISIONAL" : "SUCCEEDED";
                reasons.add(outcome.equals("PROVISIONAL")?"QUORUM_REACHED_WITH_NON_SUCCESS":"QUORUM_ALL_SUCCESS");
            } else { outcome="FAILED"; reasons.add("QUORUM_NOT_REACHED"); }
        } else if ("BEST_EFFORT".equals(p)) {
            if (success==0) { outcome="FAILED"; reasons.add("NO_SUCCESSFUL_STEP"); }
            else if (nonSuccess>0) { outcome="PARTIAL"; reasons.add("BEST_EFFORT_PARTIAL_SUCCESS"); }
            else { outcome="SUCCEEDED"; reasons.add("ALL_STEPS_SUCCEEDED"); }
        } else {
            if (failedRequired>0) { outcome="FAILED"; reasons.add("REQUIRED_STEP_NOT_SUCCESSFUL"); }
            else if (nonSuccess>0) { outcome="PARTIAL"; reasons.add("OPTIONAL_STEP_NOT_SUCCESSFUL"); }
            else { outcome="SUCCEEDED"; reasons.add("ALL_REQUIRED_STEPS_SUCCEEDED"); }
        }
        return new Decision(p,quorumRequired,required,successRequired,failedRequired,success,nonSuccess,
                artifactOutputs==null?0:artifactOutputs.size(),conflicts.size(),outcome,List.copyOf(reasons),List.copyOf(conflicts));
    }

    private static List<Map<String,Object>> conflicts(List<Map<String,Object>> outputs) {
        if (outputs==null || outputs.isEmpty()) return List.of();
        Map<String,Set<String>> claims = new LinkedHashMap<>();
        for (Map<String,Object> output:outputs) {
            if(output==null) continue;
            Object raw=output.get("evidenceClaim");
            if(!(raw instanceof Map<?,?> claim)) continue;
            Object subject=claim.get("subject"),value=claim.get("value");
            if(subject==null||value==null||String.valueOf(subject).isBlank()) continue;
            claims.computeIfAbsent(String.valueOf(subject),k->new LinkedHashSet<>()).add(String.valueOf(value));
        }
        List<Map<String,Object>> out=new ArrayList<>();
        for(var e:claims.entrySet()) if(e.getValue().size()>1) out.add(Map.of("subject",e.getKey(),"values",List.copyOf(e.getValue())));
        return out;
    }

    public record StepState(boolean required,String state) {}
    public record Decision(String completionPolicy,Integer quorumRequired,int requiredStepCount,int successfulRequiredCount,
            int failedRequiredCount,int successfulStepCount,int nonSuccessStepCount,int artifactCount,int conflictCount,
            String outcome,List<String> reasonCodes,List<Map<String,Object>> conflicts) {}
}
