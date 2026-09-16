package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;
import java.util.Objects;

/** Explicit principal clearance. Sensitivity comparison must never rely on enum ordinal outside the policy service. */
public record PrincipalClearanceRecord(
        String tenantId, String clearanceId, ScopePrincipalType principalType, String principalId,
        SensitivityLevel clearanceLevel, Instant validFrom, Instant validTo, ClearanceStatus status,
        String approvedBy, String reason, long version, Instant createdAt, Instant updatedAt) {
    public PrincipalClearanceRecord {
        tenantId=required(tenantId,"tenantId");clearanceId=required(clearanceId,"clearanceId");Objects.requireNonNull(principalType,"principalType");principalId=required(principalId,"principalId");
        Objects.requireNonNull(clearanceLevel,"clearanceLevel");Objects.requireNonNull(validFrom,"validFrom");if(validTo!=null&&!validTo.isAfter(validFrom))throw new IllegalArgumentException("validTo must be after validFrom");
        Objects.requireNonNull(status,"status");approvedBy=approvedBy==null?"":approvedBy.trim();reason=required(reason,"reason");if(version<1)throw new IllegalArgumentException("version must be positive");
        Objects.requireNonNull(createdAt,"createdAt");Objects.requireNonNull(updatedAt,"updatedAt");
        if(status==ClearanceStatus.ACTIVE&&approvedBy.isEmpty())throw new IllegalArgumentException("active clearance requires approver");
    }
    public boolean effectiveAt(Instant at){return status==ClearanceStatus.ACTIVE&&!at.isBefore(validFrom)&&(validTo==null||at.isBefore(validTo));}
    private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
