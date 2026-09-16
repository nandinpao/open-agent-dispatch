package com.opensocket.aievent.core.iam.api.application.port;

import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.response.CredentialSetupResponse;

public interface IamCredentialAdministrationApiPort {
    void initiatePasswordReset(String userId,String reason,IamApiRequestContext context);
    CredentialSetupResponse initiatePasswordSetup(String userId,String deliveryMethod,String reason,IamApiRequestContext context);
    void setTemporaryPassword(String userId,String temporaryPassword,String reason,IamApiRequestContext context);
    void resetMfa(String userId,String reason,IamApiRequestContext context);
}
