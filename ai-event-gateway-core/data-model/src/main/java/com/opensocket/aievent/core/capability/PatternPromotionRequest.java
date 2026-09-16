package com.opensocket.aievent.core.capability;
/** Human-governed lifecycle transition. Promotion never occurs from learning evidence alone. */
public record PatternPromotionRequest(String targetStatus,String reason){}
