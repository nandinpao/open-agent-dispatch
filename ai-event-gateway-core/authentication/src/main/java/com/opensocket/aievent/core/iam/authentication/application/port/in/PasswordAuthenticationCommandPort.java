package com.opensocket.aievent.core.iam.authentication.application.port.in;
import com.opensocket.aievent.core.iam.authentication.application.command.*;
import com.opensocket.aievent.core.iam.authentication.application.result.PasswordAuthenticationResult;
import com.opensocket.aievent.core.iam.authentication.domain.PasswordCredential;
public interface PasswordAuthenticationCommandPort {
    PasswordCredential setPassword(SetPasswordCommand command);
    PasswordCredential changePassword(ChangePasswordCommand command);
    PasswordAuthenticationResult authenticate(AuthenticatePasswordCommand command);
}
