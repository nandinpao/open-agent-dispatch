package com.opensocket.aievent.core.iam.api.application.port;

import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.request.UpdateUserRequest;
import com.opensocket.aievent.core.iam.api.response.UserResponse;

/**
 * Tenant-aware user administration orchestration.
 *
 * <p>The implementation must atomically validate the active Tenant membership and
 * caller administration boundary before changing the global HumanUser record. It
 * must also apply durable idempotency using the key carried by the request context.</p>
 */
public interface IamUserAdministrationApiPort {
    UserResponse updateUser(
            String userId,
            UpdateUserRequest request,
            long expectedVersion,
            IamApiRequestContext context);

}
