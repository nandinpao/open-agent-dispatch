package com.opensocket.aievent.core.capability;
/** Human accountability update. Root cause remains aggregation-evidence-derived; review records decision/accountability/status only. */
public record CaseReviewRequest(String status,String humanDecision,String accountableRef,String reason){}
