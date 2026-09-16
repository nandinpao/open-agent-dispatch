package com.opensocket.aievent.core.capability;

import java.util.List;
import java.util.Map;

/** normalized completion input. SUCCEEDED may carry one Artifact; raw transport payload is not a Plan dependency contract. */
public record PlanStepCompletionRequest(String result,String finding,Double confidence,List<String> evidenceRefs,Map<String,Object> output,String dataClassification,String failureReason) { public PlanStepCompletionRequest { evidenceRefs=evidenceRefs==null?List.of():List.copyOf(evidenceRefs); output=output==null?Map.of():Map.copyOf(output); } }
