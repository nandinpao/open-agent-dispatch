package com.opensocket.aievent.core.iam.api.application.port;

import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.request.MfaPolicyRequest;
import com.opensocket.aievent.core.iam.api.request.PasswordPolicyRequest;
import com.opensocket.aievent.core.iam.api.request.SessionPolicyRequest;
import com.opensocket.aievent.core.iam.api.request.TokenPolicyRequest;
import com.opensocket.aievent.core.iam.api.response.SecurityPolicyResponse;

public interface IamSecurityPolicyApiPort {
    SecurityPolicyResponse read(IamApiRequestContext context);
    SecurityPolicyResponse updatePassword(PasswordPolicyRequest request,long expectedVersion,IamApiRequestContext context);
    SecurityPolicyResponse updateMfa(MfaPolicyRequest request,long expectedVersion,IamApiRequestContext context);
    SecurityPolicyResponse updateSession(SessionPolicyRequest request,long expectedVersion,IamApiRequestContext context);
    SecurityPolicyResponse updateToken(TokenPolicyRequest request,long expectedVersion,IamApiRequestContext context);
    SecurityPolicyResponse restore(String policyKind,String revisionId,long expectedVersion,IamApiRequestContext context);
}
