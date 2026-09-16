package com.opensocket.aievent.core.capability;

import java.util.List;
import java.util.Map;

/** Machine/runtime completion evidence for a RUNTIME Step. Not exposed as a Human Admin completion authority. */
public record PlanRuntimeStepCompletion(String externalExecutionRef,String result,String finding,Double confidence,List<String> evidenceRefs,Map<String,Object> output,String dataClassification,String failureReason) {
    public PlanRuntimeStepCompletion { evidenceRefs=evidenceRefs==null?List.of():List.copyOf(evidenceRefs); output=output==null?Map.of():Map.copyOf(output); }
}
