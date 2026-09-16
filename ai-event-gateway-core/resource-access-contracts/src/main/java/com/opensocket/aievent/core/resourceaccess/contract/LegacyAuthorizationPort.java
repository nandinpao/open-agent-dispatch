package com.opensocket.aievent.core.resourceaccess.contract;
/** Optional legacy decision bridge. Absence is recorded as NOT_AVAILABLE, never treated as allow. */
public interface LegacyAuthorizationPort { LegacyAuthorizationDecision evaluate(AuthorizationRequest request); }
