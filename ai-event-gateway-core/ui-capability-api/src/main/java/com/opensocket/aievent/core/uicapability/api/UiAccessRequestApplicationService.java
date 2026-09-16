package com.opensocket.aievent.core.uicapability.api;

import com.opensocket.aievent.core.uiaccessrequest.application.GovernedAccessRequestResult;
import com.opensocket.aievent.core.uiaccessrequest.application.GovernedAccessRequestService;
import com.opensocket.aievent.core.uiaccessrequest.application.ReviewGovernedAccessRequestCommand;
import com.opensocket.aievent.core.uiaccessrequest.application.SubmitGovernedAccessRequestCommand;
import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

/** Transaction boundary for the governed access-request application workflow. */
public class UiAccessRequestApplicationService {
    private final GovernedAccessRequestService delegate;

    public UiAccessRequestApplicationService(GovernedAccessRequestService delegate) {
        this.delegate = delegate;
    }

    @Transactional
    public GovernedAccessRequestResult submit(SubmitGovernedAccessRequestCommand command) {
        return delegate.submit(command);
    }

    @Transactional(readOnly = true)
    public GovernedAccessRequestResult findForRequester(AuthenticationContext authentication, String requestId) {
        return delegate.findForRequester(authentication, requestId);
    }

    @Transactional(readOnly = true)
    public GovernedAccessRequestResult findForReviewer(
            AuthenticationContext authentication,
            String requestId,
            String correlationId,
            Instant requestedAt) {
        return delegate.findForReviewer(authentication, requestId, correlationId, requestedAt);
    }

    @Transactional
    public GovernedAccessRequestResult approve(ReviewGovernedAccessRequestCommand command) {
        return delegate.approve(command);
    }

    @Transactional
    public GovernedAccessRequestResult reject(ReviewGovernedAccessRequestCommand command) {
        return delegate.reject(command);
    }
}
