package com.opensocket.aievent.core.resourceaccess.core;
import static org.junit.jupiter.api.Assertions.*;import com.opensocket.aievent.core.resourceaccess.contract.*;import java.util.Set;import org.junit.jupiter.api.Test;
class ShadowDecisionComparatorV2Test {
 private final ShadowDecisionComparatorV2 comparator=new ShadowDecisionComparatorV2();
 @Test void unexpectedAllowIsCritical(){var c=comparator.compare(e("DENY",Set.of(),VisibilityLevel.NONE,"DENY",true),e("ALLOW",Set.of("TENANT:T1"),VisibilityLevel.FULL,"ALLOW",true));assertEquals(ShadowMismatchCategoryV2.UNEXPECTED_ALLOW,c);assertEquals("CRITICAL",comparator.severity(c));}
 @Test void scopeWideningWinsBeforeReasonDifference(){var c=comparator.compare(e("ALLOW",Set.of("DEPARTMENT:D1"),VisibilityLevel.SUMMARY,"A",true),e("ALLOW",Set.of("TENANT:T1"),VisibilityLevel.SUMMARY,"B",true));assertEquals(ShadowMismatchCategoryV2.SCOPE_WIDENED,c);}
 @Test void visibilityWideningIsDetected(){var c=comparator.compare(e("ALLOW",Set.of("TENANT:T1"),VisibilityLevel.SUMMARY,"A",true),e("ALLOW",Set.of("TENANT:T1"),VisibilityLevel.FULL,"A",true));assertEquals(ShadowMismatchCategoryV2.VISIBILITY_WIDENED,c);}
 @Test void incompleteLegacyContextBlocksReadiness(){var c=comparator.compare(e("ALLOW",Set.of(),VisibilityLevel.NONE,"A",false),e("ALLOW",Set.of("TENANT:T1"),VisibilityLevel.SUMMARY,"A",true));assertEquals(ShadowMismatchCategoryV2.CONTEXT_INCOMPLETE,c);}
 @Test void secretMetadataUsesPolicyScaleNotEnumOrdinal(){var c=comparator.compare(e("ALLOW",Set.of("TENANT:T1"),VisibilityLevel.FULL,"A",true),e("ALLOW",Set.of("TENANT:T1"),VisibilityLevel.SECRET_METADATA,"A",true));assertEquals(ShadowMismatchCategoryV2.VISIBILITY_WIDENED,c);}
 private ShadowDecisionEvidenceV2 e(String effect,Set<String> scopes,VisibilityLevel visibility,String reason,boolean complete){return new ShadowDecisionEvidenceV2(effect,scopes,visibility,reason,complete,"","D");}
}
