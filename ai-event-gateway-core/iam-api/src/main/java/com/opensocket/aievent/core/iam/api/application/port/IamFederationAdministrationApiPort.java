package com.opensocket.aievent.core.iam.api.application.port;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.request.*;
import com.opensocket.aievent.core.iam.api.response.*;
import java.util.List;
public interface IamFederationAdministrationApiPort {
    List<AuthenticationProviderResponse> providers(String tenantId, IamApiRequestContext context);
    AuthenticationProviderResponse upsertProvider(String tenantId, UpsertAuthenticationProviderRequest request, long expectedVersion, IamApiRequestContext context);
    AuthenticationProviderResponse changeProviderStatus(String tenantId, String providerId, String status, long expectedVersion, IamApiRequestContext context);
    FederationPolicyResponse policy(String tenantId, IamApiRequestContext context);
    FederationPolicyResponse updatePolicy(String tenantId, UpdateFederationPolicyRequest request, long expectedVersion, IamApiRequestContext context);
    List<ExternalIdentityLinkResponse> userLinks(String tenantId, String userId, IamApiRequestContext context);
    ExternalIdentityLinkResponse linkUser(String tenantId, String userId, LinkExternalIdentityRequest request, IamApiRequestContext context);
    void unlinkUser(String tenantId, String userId, String credentialLinkId, long expectedVersion, IamApiRequestContext context);
}
