package com.opensocket.aievent.core.iam.api.application.port;

import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.response.UserInvitationStatusResponse;

public interface IamUserInvitationApiPort {
    UserInvitationStatusResponse status(String userId, IamApiRequestContext context);
    UserInvitationStatusResponse resend(String userId, String deliveryMethod, IamApiRequestContext context);
    UserInvitationStatusResponse revoke(String userId, IamApiRequestContext context);
}
