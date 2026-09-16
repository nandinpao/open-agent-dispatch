package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;
import java.util.Objects;

/** Tenant-bound explicit resource scope grant. This is policy data, not an authorization decision. */
public record ScopeGrantRecord(
        String tenantId, String grantId, ScopePrincipalType principalType, String principalId,
        String permissionCode, ResourceType resourceType, ScopeType scopeType, String scopeRefId,
        VisibilityLevel visibilityLevel, Instant validFrom, Instant validTo, ScopeGrantSource grantSource,
        String grantReason, String createdBy, String approvedBy, ScopeGrantState state,
        String idempotencyKey, long version, Instant createdAt, Instant updatedAt) {
    public ScopeGrantRecord {
        tenantId=required(tenantId,"tenantId"); grantId=required(grantId,"grantId");
        Objects.requireNonNull(principalType,"principalType"); principalId=required(principalId,"principalId");
        permissionCode=required(permissionCode,"permissionCode"); Objects.requireNonNull(resourceType,"resourceType");
        Objects.requireNonNull(scopeType,"scopeType"); scopeRefId=normalizeScope(scopeType,scopeRefId,tenantId);
        Objects.requireNonNull(visibilityLevel,"visibilityLevel"); Objects.requireNonNull(validFrom,"validFrom");
        if(validTo!=null&&!validTo.isAfter(validFrom))throw new IllegalArgumentException("validTo must be after validFrom");
        Objects.requireNonNull(grantSource,"grantSource"); grantReason=required(grantReason,"grantReason");
        createdBy=required(createdBy,"createdBy"); approvedBy=approvedBy==null?"":approvedBy.trim();
        Objects.requireNonNull(state,"state"); idempotencyKey=required(idempotencyKey,"idempotencyKey");
        if(version<1)throw new IllegalArgumentException("version must be positive");
        Objects.requireNonNull(createdAt,"createdAt"); Objects.requireNonNull(updatedAt,"updatedAt");
        if(state==ScopeGrantState.ACTIVE&&approvedBy.isEmpty())
            throw new IllegalArgumentException("active grant requires independently verified approver");
        if(!approvedBy.isEmpty()&&approvedBy.equals(createdBy))
            throw new IllegalArgumentException("grant creator cannot approve the same grant");
    }
    public boolean effectiveAt(Instant at){return state==ScopeGrantState.ACTIVE&&!at.isBefore(validFrom)&&(validTo==null||at.isBefore(validTo));}
    private static String normalizeScope(ScopeType type,String value,String tenant){
        String normalized=value==null?"":value.trim();
        if(type==ScopeType.TENANT){if(!normalized.isEmpty()&&!normalized.equals(tenant))throw new IllegalArgumentException("TENANT scopeRefId must equal tenantId");return normalized.isEmpty()?tenant:normalized;}
        if(type==ScopeType.OWNER||type==ScopeType.PARTICIPANT||type==ScopeType.CREATED_BY_ME||type==ScopeType.ASSIGNED_TO_ME)return normalized;
        if(normalized.isEmpty())throw new IllegalArgumentException("scopeRefId is required for "+type);
        return normalized;
    }
    private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
