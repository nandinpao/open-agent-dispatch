package com.opensocket.aievent.core.iam.api.response;
import com.opensocket.aievent.core.iam.rbac.domain.shadow.ShadowObservationSummary;import java.util.Map;
public record ShadowObservationSummaryResponse(long total,Map<String,Long> byCategory,long critical,long openCases,long overdueCases,long activeWaivers,long cutoverBlockers){public static ShadowObservationSummaryResponse from(ShadowObservationSummary v){return new ShadowObservationSummaryResponse(v.total(),v.byCategory(),v.critical(),v.openCases(),v.overdueCases(),v.activeWaivers(),v.cutoverBlockers());}}
