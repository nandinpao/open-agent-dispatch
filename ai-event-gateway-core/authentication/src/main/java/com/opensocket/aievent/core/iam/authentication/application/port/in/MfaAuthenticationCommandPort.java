package com.opensocket.aievent.core.iam.authentication.application.port.in;
import com.opensocket.aievent.core.iam.authentication.application.command.*;
import com.opensocket.aievent.core.iam.authentication.application.result.TotpEnrollmentResult;
import com.opensocket.aievent.core.iam.authentication.domain.MfaMethod;
public interface MfaAuthenticationCommandPort {
    TotpEnrollmentResult begin(BeginTotpEnrollmentCommand command);
    MfaMethod confirm(ConfirmTotpEnrollmentCommand command);
    boolean verify(VerifyMfaCommand command);
    void reset(ResetMfaCommand command);
}
