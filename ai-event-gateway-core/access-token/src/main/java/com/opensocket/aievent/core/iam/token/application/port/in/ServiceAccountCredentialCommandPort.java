package com.opensocket.aievent.core.iam.token.application.port.in;

import com.opensocket.aievent.core.iam.token.application.command.IssueServiceAccountCredentialCommand;
import com.opensocket.aievent.core.iam.token.application.command.RevokeServiceAccountCredentialCommand;
import com.opensocket.aievent.core.iam.token.application.command.RotateServiceAccountCredentialCommand;
import com.opensocket.aievent.core.iam.token.application.command.ValidateServiceAccountCredentialCommand;
import com.opensocket.aievent.core.iam.token.application.result.IssuedServiceAccountCredentialResult;
import com.opensocket.aievent.core.iam.token.application.result.ValidatedServiceAccountCredentialResult;

public interface ServiceAccountCredentialCommandPort {
    IssuedServiceAccountCredentialResult issue(IssueServiceAccountCredentialCommand command);
    IssuedServiceAccountCredentialResult rotate(RotateServiceAccountCredentialCommand command);
    void revoke(RevokeServiceAccountCredentialCommand command);
    ValidatedServiceAccountCredentialResult validate(ValidateServiceAccountCredentialCommand command);
}
