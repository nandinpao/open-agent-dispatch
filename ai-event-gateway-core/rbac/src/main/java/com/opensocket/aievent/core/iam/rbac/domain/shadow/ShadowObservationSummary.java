package com.opensocket.aievent.core.iam.rbac.domain.shadow;
import java.util.Map;
public record ShadowObservationSummary(long total,Map<String,Long> byCategory,long critical,long openCases,long overdueCases,long activeWaivers,long cutoverBlockers){}
