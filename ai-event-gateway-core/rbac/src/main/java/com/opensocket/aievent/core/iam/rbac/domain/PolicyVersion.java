package com.opensocket.aievent.core.iam.rbac.domain;

import java.time.Instant;
import java.util.Objects;
public record PolicyVersion(ScopeType scopeType,String scopeId,String tenantId,long value,Instant updatedAt,String updatedBy){
    public PolicyVersion{Objects.requireNonNull(scopeType,"scopeType");scopeId=required(scopeId,"scopeId");tenantId=tenantId==null?"":tenantId.trim();if(scopeType==ScopeType.INSTANCE&&!tenantId.isEmpty())throw new IllegalArgumentException("instance policy cannot carry tenantId");if(scopeType==ScopeType.TENANT&&(tenantId.isEmpty()||!scopeId.equals(tenantId)))throw new IllegalArgumentException("tenant policy scope mismatch");if(value<0)throw new IllegalArgumentException("policy version must be non-negative");Objects.requireNonNull(updatedAt,"updatedAt");updatedBy=required(updatedBy,"updatedBy");}
    private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
