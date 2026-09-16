package com.opensocket.aievent.core.resourceaccess.contract;
import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import java.util.List;
/** Trusted scheduler command. A background job must use an authenticated Service/System principal and an explicit resource set. */
public record BackgroundJobAuthorizationCommand(String jobCode,AuthenticationContext serviceAuthentication,List<BackgroundJobResourceRequest> resources,String purpose,String correlationId){
 public BackgroundJobAuthorizationCommand{jobCode=required(jobCode,"jobCode");if(serviceAuthentication==null)throw new IllegalArgumentException("serviceAuthentication is required");if(serviceAuthentication.principal().principalType()!=com.opensocket.aievent.core.iam.security.contract.PrincipalRef.PrincipalType.SERVICE_ACCOUNT&&serviceAuthentication.principal().principalType()!=com.opensocket.aievent.core.iam.security.contract.PrincipalRef.PrincipalType.SYSTEM_SERVICE)throw new IllegalArgumentException("Background jobs require Service Account or System Service principal");resources=resources==null?List.of():List.copyOf(resources);if(resources.isEmpty())throw new IllegalArgumentException("Background jobs require an explicit resource set");purpose=required(purpose,"purpose");correlationId=required(correlationId,"correlationId");}
 private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
