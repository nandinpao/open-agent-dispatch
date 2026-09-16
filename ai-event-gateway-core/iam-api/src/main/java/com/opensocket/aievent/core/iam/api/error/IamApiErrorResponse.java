package com.opensocket.aievent.core.iam.api.error;
import java.time.Instant;import java.util.List;
public record IamApiErrorResponse(String errorCode,String message,String correlationId,List<IamFieldError> fieldErrors,
                                  String requiredPermission,String activeTenantId,Instant occurredAt){
    public IamApiErrorResponse{fieldErrors=fieldErrors==null?List.of():List.copyOf(fieldErrors);requiredPermission=requiredPermission==null?"":requiredPermission;activeTenantId=activeTenantId==null?"":activeTenantId;}
}
