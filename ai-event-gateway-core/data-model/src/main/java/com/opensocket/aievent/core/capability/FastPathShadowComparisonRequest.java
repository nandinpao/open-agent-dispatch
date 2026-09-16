package com.opensocket.aievent.core.capability;
/** Observe what a SHADOW pattern would have planned; actualPlan remains authoritative. */
public record FastPathShadowComparisonRequest(String patternId,String actualPlanId,Integer actualPlanRevision){}
