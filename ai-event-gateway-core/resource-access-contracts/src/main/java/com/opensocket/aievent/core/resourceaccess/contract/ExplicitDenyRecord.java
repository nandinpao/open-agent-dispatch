package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;
import java.util.Objects;

/** Explicit deny evidence. A matching active deny has precedence over all grants. */
public record ExplicitDenyRecord(
        String tenantId, String denyId, ScopePrincipalType principalType, String principalId,
        String permissionCode, ResourceType resourceType, ScopeType scopeType, String scopeRefId,
        String denyReason, DenySeverity severity, Instant validFrom, Instant validTo,
        String createdBy, String approvedBy, ScopeDenyState state, String idempotencyKey,
        long version, Instant createdAt, Instant updatedAt) {
    public ExplicitDenyRecord {
        tenantId=required(tenantId,"tenantId");denyId=required(denyId,"denyId");
        Objects.requireNonNull(principalType,"principalType");principalId=required(principalId,"principalId");
        permissionCode=permissionCode==null?"":permissionCode.trim();Objects.requireNonNull(resourceType,"resourceType");
        Objects.requireNonNull(scopeType,"scopeType");scopeRefId=normalizeScope(scopeType,scopeRefId,tenantId);
        denyReason=required(denyReason,"denyReason");Objects.requireNonNull(severity,"severity");
        Objects.requireNonNull(validFrom,"validFrom");if(validTo!=null&&!validTo.isAfter(validFrom))throw new IllegalArgumentException("validTo must be after validFrom");
        createdBy=required(createdBy,"createdBy");approvedBy=approvedBy==null?"":approvedBy.trim();
        Objects.requireNonNull(state,"state");idempotencyKey=required(idempotencyKey,"idempotencyKey");
        if(version<1)throw new IllegalArgumentException("version must be positive");Objects.requireNonNull(createdAt,"createdAt");Objects.requireNonNull(updatedAt,"updatedAt");
        if(state==ScopeDenyState.ACTIVE&&approvedBy.isEmpty())throw new IllegalArgumentException("active deny requires approver");
    }
    public boolean effectiveAt(Instant at){return state==ScopeDenyState.ACTIVE&&!at.isBefore(validFrom)&&(validTo==null||at.isBefore(validTo));}
    public boolean coversPermission(String requested){return permissionCode.isEmpty()||permissionCode.equals(requested);}
    private static String normalizeScope(ScopeType type,String value,String tenant){String n=value==null?"":value.trim();if(type==ScopeType.TENANT){if(!n.isEmpty()&&!n.equals(tenant))throw new IllegalArgumentException("TENANT scopeRefId must equal tenantId");return n.isEmpty()?tenant:n;}if(type==ScopeType.OWNER||type==ScopeType.PARTICIPANT||type==ScopeType.CREATED_BY_ME||type==ScopeType.ASSIGNED_TO_ME)return n;if(n.isEmpty())throw new IllegalArgumentException("scopeRefId is required for "+type);return n;}
    private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
