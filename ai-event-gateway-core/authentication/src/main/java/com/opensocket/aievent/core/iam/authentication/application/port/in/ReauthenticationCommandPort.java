package com.opensocket.aievent.core.iam.authentication.application.port.in;
import com.opensocket.aievent.core.iam.authentication.application.command.ReauthenticateCommand;
import com.opensocket.aievent.core.iam.authentication.domain.*;
public interface ReauthenticationCommandPort {
    ReauthenticationGrant reauthenticate(ReauthenticateCommand command, SessionPolicy policy);
}
