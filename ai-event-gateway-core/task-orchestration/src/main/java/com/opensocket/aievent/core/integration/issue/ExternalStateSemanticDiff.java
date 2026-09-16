package com.opensocket.aievent.core.integration.issue;
public record ExternalStateSemanticDiff(String diffJson,String diffHash,int changedFieldCount,String comparisonMode) {}
