package com.opensocket.aievent.core.resourceaccess.contract;
/** Streaming start evidence bound to a Runtime Authorization Lease. */
public record StreamingAuthorizationSession(AuthorizationDecision decision,RuntimeAuthorizationLease lease){public StreamingAuthorizationSession{if(decision==null||lease==null)throw new IllegalArgumentException("decision and lease are required");}}
