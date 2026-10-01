package com.opensocket.aievent.core.kernel.configuration.cutover;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Release-governed ordered cutover wave. Wave membership is deployment governance, not a mutable
 * business Runtime Configuration value.
 */
public record RuntimeConfigurationCutoverWave(
        String waveId,
        int sequenceNo,
        String displayName,
        String riskTier,
        int requiredAuthorityContractVersion,
        String releaseStage,
        List<Member> members) {
    public RuntimeConfigurationCutoverWave {
        waveId = required(waveId, "waveId");
        displayName = required(displayName, "displayName");
        riskTier = required(riskTier, "riskTier").toUpperCase();
        if (!Set.of("LOW","MEDIUM","HIGH","CRITICAL").contains(riskTier)) throw new IllegalArgumentException("Unsupported riskTier: " + riskTier);
        releaseStage = required(releaseStage, "releaseStage");
        if (sequenceNo < 1) throw new IllegalArgumentException("sequenceNo must be >= 1");
        if (requiredAuthorityContractVersion < 2) throw new IllegalArgumentException("cutover wave requires authority contract v2 or later");
        members = members == null ? List.of() : List.copyOf(members);
        if (members.isEmpty()) throw new IllegalArgumentException("cutover wave members must not be empty");
        Set<String> setKeys = new HashSet<>();
        Set<Integer> memberSequences = new HashSet<>();
        for (Member member : members) {
            if (!setKeys.add(member.setKey())) throw new IllegalArgumentException("duplicate cutover wave setKey: " + member.setKey());
            if (!memberSequences.add(member.sequenceNo())) throw new IllegalArgumentException("duplicate cutover wave member sequence: " + member.sequenceNo());
        }
    }

    public record Member(String setKey, int sequenceNo, int expectedRuntimeKeyCount) {
        public Member {
            setKey = required(setKey, "setKey");
            if (sequenceNo < 1) throw new IllegalArgumentException("member sequenceNo must be >= 1");
            if (expectedRuntimeKeyCount < 1) throw new IllegalArgumentException("expectedRuntimeKeyCount must be >= 1");
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
