package com.opensocket.aievent.core.iam.api.application.port;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.request.OidcStartRequest;
import com.opensocket.aievent.core.iam.api.response.*;
import java.util.List;
public interface IamFederationAuthenticationApiPort {
    List<FederationProviderPublicResponse> publicProviders(String tenantId, IamApiRequestContext context);
    OidcStartResponse startOidc(OidcStartRequest request, IamApiRequestContext context);
    FederatedSessionResponse completeOidc(String code, String state, IamApiRequestContext context);
}
