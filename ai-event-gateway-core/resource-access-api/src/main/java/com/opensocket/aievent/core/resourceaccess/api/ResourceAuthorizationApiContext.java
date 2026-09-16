package com.opensocket.aievent.core.resourceaccess.api;
import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;import java.time.Instant;
/** Trusted authenticated context for decision APIs. */
public record ResourceAuthorizationApiContext(AuthenticationContext authentication,String correlationId,Instant requestedAt){
 public ResourceAuthorizationApiContext{if(authentication==null)throw new IllegalArgumentException("authentication is required");if(correlationId==null||correlationId.isBlank())throw new IllegalArgumentException("correlationId is required");correlationId=correlationId.trim();if(requestedAt==null)throw new IllegalArgumentException("requestedAt is required");}
}
