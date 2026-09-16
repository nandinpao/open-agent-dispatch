package com.opensocket.aievent.core.capability;
import java.time.OffsetDateTime;import java.util.List;
/** SHADOW evidence only. It must never affect the real Task, Provider selection, dispatch or result. */
public record FastPathShadowComparison(String comparisonId,String tenantId,String patternId,int patternVersion,String actualPlanId,int actualPlanRevision,String result,boolean planMatch,boolean dependencyMatch,double capabilityCoverage,String patternTemplateHash,String actualTemplateHash,List<String> reasonCodes,OffsetDateTime observedAt){public FastPathShadowComparison{reasonCodes=reasonCodes==null?List.of():List.copyOf(reasonCodes);}}
