package com.opensocket.aievent.core.iam.api.application.port;

import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.request.*;
import com.opensocket.aievent.core.iam.api.response.*;

public interface IamAuthenticationApiPort {
    LoginResponse login(LoginRequest request, IamApiRequestContext context);
    LoginResponse verifyMfa(VerifyLoginMfaRequest request, IamApiRequestContext context);
    SessionResponse currentSession(IamApiRequestContext context);
    SessionResponse switchTenant(SwitchTenantRequest request, IamApiRequestContext context);
    void logout(IamApiRequestContext context);
    void logoutAll(IamApiRequestContext context);
    void changePassword(ChangePasswordRequest request, IamApiRequestContext context);
    void requestPasswordReset(ForgotPasswordRequest request, IamApiRequestContext context);
    void resetPassword(ResetPasswordRequest request, IamApiRequestContext context);
    void activateInvitation(ActivateInvitationRequest request, IamApiRequestContext context);
    MfaEnrollmentResponse beginUserMfa(BeginMfaEnrollmentRequest request, IamApiRequestContext context);
    void confirmUserMfa(ConfirmMfaEnrollmentRequest request, IamApiRequestContext context);
}
