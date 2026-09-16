package com.opensocket.aievent.core.resourceaccess.contract;

import java.util.Objects;

public record RuntimeResultFenceResult(
        RuntimeResultDisposition disposition,
        RuntimeAuthorizationLease lease,
        RuntimeLateResultQuarantine quarantine,
        String reasonCode) {
    public RuntimeResultFenceResult {
        Objects.requireNonNull(disposition,"disposition"); Objects.requireNonNull(lease,"lease");
        reasonCode=reasonCode==null?"":reasonCode.trim();
        if(disposition==RuntimeResultDisposition.QUARANTINED&&quarantine==null)throw new IllegalArgumentException("quarantine is required");
        if(disposition==RuntimeResultDisposition.ACCEPTED&&quarantine!=null)throw new IllegalArgumentException("accepted result cannot carry quarantine");
    }
}
