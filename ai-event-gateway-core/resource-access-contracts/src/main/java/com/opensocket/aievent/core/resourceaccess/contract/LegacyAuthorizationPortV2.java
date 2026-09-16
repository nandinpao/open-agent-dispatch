package com.opensocket.aievent.core.resourceaccess.contract;
/** Optional richer legacy adapter used by Phase 5I. Existing LegacyAuthorizationPort implementations remain supported. */
public interface LegacyAuthorizationPortV2 extends LegacyAuthorizationPort {
    ShadowDecisionEvidenceV2 evaluateEvidence(AuthorizationRequest request);
    @Override default LegacyAuthorizationDecision evaluate(AuthorizationRequest request){
        ShadowDecisionEvidenceV2 evidence=evaluateEvidence(request);
        LegacyAuthorizationDecision.Effect effect=LegacyAuthorizationDecision.Effect.valueOf(evidence.effect());
        return new LegacyAuthorizationDecision(effect,evidence.reasonCode().isBlank()?"LEGACY_REASON_UNAVAILABLE":evidence.reasonCode(),evidence.decisionId());
    }
}
