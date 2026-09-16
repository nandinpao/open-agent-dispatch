package com.opensocket.aievent.core.resourceaccess.contract;
/** Resource Access side of Provider write authorization. It never resolves Provider credentials. */
public interface ExternalWriteAuthorizationPort {
    ExternalWriteAuthorizationContext authorizeHumanWrite(ExternalWriteAuthorizationCommand command);
}
