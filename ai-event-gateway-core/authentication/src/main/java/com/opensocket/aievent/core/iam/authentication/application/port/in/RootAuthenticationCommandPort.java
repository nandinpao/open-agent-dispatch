package com.opensocket.aievent.core.iam.authentication.application.port.in;
import com.opensocket.aievent.core.iam.authentication.application.command.*;
import com.opensocket.aievent.core.iam.authentication.application.result.*;
import com.opensocket.aievent.core.iam.authentication.domain.*;
public interface RootAuthenticationCommandPort {
    RootBootstrapState recordStep(RootBootstrapStepCommand command);
    RootRecoveryGrantResult issue(IssueRootRecoveryGrantCommand command);
    RootRecoverySessionResult consume(ConsumeRootRecoveryGrantCommand command);
    void closeRecoverySession(CloseRootRecoverySessionCommand command);
}
