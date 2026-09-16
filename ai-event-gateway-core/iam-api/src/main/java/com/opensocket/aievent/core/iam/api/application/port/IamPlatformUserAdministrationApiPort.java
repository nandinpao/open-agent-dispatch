package com.opensocket.aievent.core.iam.api.application.port;

import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.request.ChangeUserStatusRequest;
import com.opensocket.aievent.core.iam.api.request.CreateUserRequest;
import com.opensocket.aievent.core.iam.api.request.UpdateUserRequest;
import com.opensocket.aievent.core.iam.api.response.UserResponse;

/** INSTANCE-scope administration of global Human User identities. */
public interface IamPlatformUserAdministrationApiPort {
    UserResponse createUser(CreateUserRequest request, IamApiRequestContext context);
    UserResponse updateUser(String userId, UpdateUserRequest request, long expectedVersion, IamApiRequestContext context);
    UserResponse changeUserStatus(String userId, ChangeUserStatusRequest request, long expectedVersion, IamApiRequestContext context);
    void requirePasswordChange(String userId, IamApiRequestContext context);
    void revokeAllSessions(String userId, IamApiRequestContext context);
}
