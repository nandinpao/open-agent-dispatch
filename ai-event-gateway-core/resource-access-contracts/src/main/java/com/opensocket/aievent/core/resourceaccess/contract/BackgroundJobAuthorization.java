package com.opensocket.aievent.core.resourceaccess.contract;
import java.time.Instant;
import java.util.List;
/** Authorization evidence for one background job iteration, including an epoch-bound lease per allowed resource. */
public record BackgroundJobAuthorization(String authorizationId,String jobCode,String principalId,List<String> decisionIds,List<ResourceRef> resourceRefs,List<RuntimeAuthorizationLease> runtimeLeases,Instant evaluatedAt,boolean executable){
 public BackgroundJobAuthorization{authorizationId=required(authorizationId,"authorizationId");jobCode=required(jobCode,"jobCode");principalId=required(principalId,"principalId");decisionIds=decisionIds==null?List.of():List.copyOf(decisionIds);resourceRefs=resourceRefs==null?List.of():List.copyOf(resourceRefs);runtimeLeases=runtimeLeases==null?List.of():List.copyOf(runtimeLeases);if(evaluatedAt==null)throw new IllegalArgumentException("evaluatedAt is required");if(decisionIds.size()!=resourceRefs.size())throw new IllegalArgumentException("Each background resource requires a decision");if(executable&&runtimeLeases.size()!=resourceRefs.size())throw new IllegalArgumentException("Executable background authorization requires one Runtime Lease per resource");}
 private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
