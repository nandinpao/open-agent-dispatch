package com.opensocket.aievent.core.iam.rbac.application.port.in;
import com.opensocket.aievent.core.iam.security.contract.AuthorizationDecision;
import com.opensocket.aievent.core.iam.security.contract.AuthorizationRequest;
public interface AuthorizationPort { AuthorizationDecision authorize(AuthorizationRequest request); }
