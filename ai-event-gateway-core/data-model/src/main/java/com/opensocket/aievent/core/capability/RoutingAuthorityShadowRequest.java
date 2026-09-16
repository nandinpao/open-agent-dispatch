package com.opensocket.aievent.core.capability;

/** Request to evaluate one already-admitted Plan Step through the A0-R6 shadow path. */
public record RoutingAuthorityShadowRequest(Integer planRevision,String stepId,String routingProfileId) {}
