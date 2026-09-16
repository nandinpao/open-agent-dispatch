package com.opensocket.aievent.core.iam.api.application.port;

import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.request.UserOnboardingRequest;
import com.opensocket.aievent.core.iam.api.response.UserOnboardingResponse;

/** Application boundary for atomic user onboarding and future lifecycle orchestration. */
public interface IamUserLifecycleApiPort {
    UserOnboardingResponse onboard(UserOnboardingRequest request, IamApiRequestContext context);
}
