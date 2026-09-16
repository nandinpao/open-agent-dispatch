package com.opensocket.aievent.core.iam.api.application.port;

import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.request.BeginMfaEnrollmentRequest;
import com.opensocket.aievent.core.iam.api.request.CompleteBootstrapRequest;
import com.opensocket.aievent.core.iam.api.request.ConfirmMfaEnrollmentRequest;
import com.opensocket.aievent.core.iam.api.request.CreateTenantAdminRequest;
import com.opensocket.aievent.core.iam.api.request.CreateTenantRequest;
import com.opensocket.aievent.core.iam.api.response.BootstrapStatusResponse;
import com.opensocket.aievent.core.iam.api.response.MfaEnrollmentResponse;
import com.opensocket.aievent.core.iam.api.response.SessionResponse;
import com.opensocket.aievent.core.iam.api.response.TenantResponse;
import com.opensocket.aievent.core.iam.api.response.UserResponse;

/** Installation creates Root; this port completes MFA, Tenant and administrator bootstrap only. */
public interface IamBootstrapApiPort {
    BootstrapStatusResponse status();
    MfaEnrollmentResponse beginRootMfa(BeginMfaEnrollmentRequest request, IamApiRequestContext context);
    SessionResponse confirmRootMfa(ConfirmMfaEnrollmentRequest request, IamApiRequestContext context);
    TenantResponse createFirstTenant(CreateTenantRequest request, IamApiRequestContext context);
    UserResponse createFirstTenantAdmin(CreateTenantAdminRequest request, IamApiRequestContext context);
    BootstrapStatusResponse complete(CompleteBootstrapRequest request, IamApiRequestContext context);
}
