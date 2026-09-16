package com.opensocket.aievent.core.capability;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable explanation of one TrustAssurancePolicy evaluation. */
public record TrustAssuranceEvaluation(
        String policyId,
        long policyVersion,
        PeerTrustEvidenceSubjectType subjectType,
        String peerId,
        String interfaceId,
        Set<PeerTrustAssuranceGrant> grants,
        Set<PeerTrustEvidenceType> effectiveEvidence,
        Map<PeerTrustAssuranceGrant,List<List<PeerTrustEvidenceType>>> requiredEvidenceSets,
        boolean activePolicyPresent) {
    public boolean grants(PeerTrustAssuranceGrant grant) { return grants.contains(grant); }
}
