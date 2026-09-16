package com.opensocket.aievent.core.iam.api.application.port;

import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.response.IamUiSessionResponse;

/** Read model used by the Admin UI after a browser session has been authenticated. */
public interface IamUiSessionApiPort {
    IamUiSessionResponse current(IamApiRequestContext context);
}
