package com.opensocket.aievent.core.iam.identity.application.port.in;

import com.opensocket.aievent.core.iam.identity.application.command.ChangeHumanUserStatusCommand;
import com.opensocket.aievent.core.iam.identity.application.command.ChangeRootIdentityStatusCommand;
import com.opensocket.aievent.core.iam.identity.application.command.CreateHumanUserCommand;
import com.opensocket.aievent.core.iam.identity.application.command.CreateRootIdentityCommand;
import com.opensocket.aievent.core.iam.identity.application.command.UpdateHumanUserProfileCommand;
import com.opensocket.aievent.core.iam.identity.domain.HumanUser;
import com.opensocket.aievent.core.iam.identity.domain.RootIdentity;

public interface IdentityCommandPort {
    HumanUser createHumanUser(CreateHumanUserCommand command);
    HumanUser changeHumanUserStatus(ChangeHumanUserStatusCommand command);
    HumanUser updateHumanUserProfile(UpdateHumanUserProfileCommand command);
    RootIdentity createRootIdentity(CreateRootIdentityCommand command);
    RootIdentity changeRootIdentityStatus(ChangeRootIdentityStatusCommand command);
}
