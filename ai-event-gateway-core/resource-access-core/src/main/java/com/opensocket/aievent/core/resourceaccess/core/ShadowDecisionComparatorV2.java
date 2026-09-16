package com.opensocket.aievent.core.resourceaccess.core;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.util.*;
/** Pure Phase 5I comparator. Security-widening categories take precedence over availability differences. */
public final class ShadowDecisionComparatorV2 {
    public ShadowMismatchCategoryV2 compare(ShadowDecisionEvidenceV2 legacy,ShadowDecisionEvidenceV2 target){
        Objects.requireNonNull(legacy,"legacy");Objects.requireNonNull(target,"target");
        if("ERROR".equals(target.effect())||!target.errorCode().isBlank())return ShadowMismatchCategoryV2.TARGET_ERROR;
        if("NOT_AVAILABLE".equals(legacy.effect()))return ShadowMismatchCategoryV2.LEGACY_UNAVAILABLE;
        if("ERROR".equals(legacy.effect()))return ShadowMismatchCategoryV2.CONTEXT_INCOMPLETE;
        if("DENY".equals(legacy.effect())&&"ALLOW".equals(target.effect()))return ShadowMismatchCategoryV2.UNEXPECTED_ALLOW;
        if("ALLOW".equals(legacy.effect())&&"DENY".equals(target.effect()))return ShadowMismatchCategoryV2.UNEXPECTED_DENY;
        if("ALLOW".equals(legacy.effect())&&"ALLOW".equals(target.effect())){
            if(legacy.contextComplete()&&target.contextComplete()&&scopeWidened(legacy.effectiveScopes(),target.effectiveScopes()))return ShadowMismatchCategoryV2.SCOPE_WIDENED;
            if(!ResourcePolicyScale.visibilityCovers(legacy.visibility(),target.visibility()))return ShadowMismatchCategoryV2.VISIBILITY_WIDENED;
        }
        if(!legacy.contextComplete()||!target.contextComplete())return ShadowMismatchCategoryV2.CONTEXT_INCOMPLETE;
        if(!normalize(legacy.reasonCode()).equals(normalize(target.reasonCode())))return ShadowMismatchCategoryV2.REASON_DIFFERENT;
        return ShadowMismatchCategoryV2.MATCH;
    }
    public String severity(ShadowMismatchCategoryV2 category){return switch(category){
        case UNEXPECTED_ALLOW,SCOPE_WIDENED,VISIBILITY_WIDENED,TARGET_ERROR -> "CRITICAL";
        case CONTEXT_INCOMPLETE -> "HIGH";
        case UNEXPECTED_DENY -> "MEDIUM";
        case REASON_DIFFERENT,LEGACY_UNAVAILABLE -> "LOW";
        case MATCH -> "INFO";
    };}
    private boolean scopeWidened(Set<String> legacy,Set<String> target){return !target.isEmpty()&&!legacy.containsAll(target);}
    private String normalize(String value){return value==null?"":value.trim().toUpperCase(Locale.ROOT);}
}
