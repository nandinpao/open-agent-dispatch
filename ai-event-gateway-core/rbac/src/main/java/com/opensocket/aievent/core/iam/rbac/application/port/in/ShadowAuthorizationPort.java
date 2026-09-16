package com.opensocket.aievent.core.iam.rbac.application.port.in;
import com.opensocket.aievent.core.iam.rbac.domain.LegacyDecision;
import com.opensocket.aievent.core.iam.rbac.domain.ShadowDecisionRecord;
import com.opensocket.aievent.core.iam.security.contract.AuthorizationDecision;
import com.opensocket.aievent.core.iam.security.contract.AuthorizationRequest;
public interface ShadowAuthorizationPort { ShadowDecisionRecord compare(LegacyDecision legacy, AuthorizationRequest request, AuthorizationDecision decision, String route); }
