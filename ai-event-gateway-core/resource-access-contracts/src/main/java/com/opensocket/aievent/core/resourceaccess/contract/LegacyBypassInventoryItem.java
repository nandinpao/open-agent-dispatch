package com.opensocket.aievent.core.resourceaccess.contract;
import java.time.Instant;
/** Explicit inventory of a compatibility or bypass path. */
public record LegacyBypassInventoryItem(String bypassId,String tenantId,String component,String pathPattern,String owner,String rationale,Instant expiresAt,boolean active,String replacementRef,long version){
 public LegacyBypassInventoryItem{bypassId=req(bypassId,"bypassId");tenantId=req(tenantId,"tenantId");component=req(component,"component");pathPattern=req(pathPattern,"pathPattern");owner=req(owner,"owner");rationale=req(rationale,"rationale");replacementRef=req(replacementRef,"replacementRef");if(version<0)throw new IllegalArgumentException("version must be non-negative");if(active&&expiresAt==null)throw new IllegalArgumentException("active bypass requires expiresAt");}
 private static String req(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
