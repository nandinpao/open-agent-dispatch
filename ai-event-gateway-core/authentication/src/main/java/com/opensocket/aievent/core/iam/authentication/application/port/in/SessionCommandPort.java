package com.opensocket.aievent.core.iam.authentication.application.port.in;

import com.opensocket.aievent.core.iam.authentication.application.command.CreateBrowserSessionCommand;
import com.opensocket.aievent.core.iam.authentication.application.command.RevokeSessionCommand;
import com.opensocket.aievent.core.iam.authentication.application.command.RotateSessionCommand;
import com.opensocket.aievent.core.iam.authentication.application.command.TouchSessionCommand;
import com.opensocket.aievent.core.iam.authentication.domain.BrowserSession;
import com.opensocket.aievent.core.iam.authentication.domain.SessionPolicy;

public interface SessionCommandPort {
    BrowserSession create(CreateBrowserSessionCommand command, SessionPolicy policy);

    BrowserSession touch(TouchSessionCommand command, SessionPolicy policy);

    BrowserSession rotate(RotateSessionCommand command, SessionPolicy policy);

    BrowserSession revoke(RevokeSessionCommand command);
}
