package com.opensocket.aievent.core.resourceaccess.contract;
/** Append-only Human-side audit written before Issue Tracking resolves any Provider credential. */
public interface ExternalWriteAuthorizationAuditPort {
 void append(ExternalWriteAuthorizationCommand command,ExternalWriteAuthorizationContext context);
}
