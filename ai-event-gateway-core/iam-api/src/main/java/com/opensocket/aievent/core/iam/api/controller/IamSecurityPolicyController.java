package com.opensocket.aievent.core.iam.api.controller;

import com.opensocket.aievent.core.iam.api.application.port.IamSecurityPolicyApiPort;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContextFactory;
import com.opensocket.aievent.core.iam.api.request.MfaPolicyRequest;
import com.opensocket.aievent.core.iam.api.request.PasswordPolicyRequest;
import com.opensocket.aievent.core.iam.api.request.SessionPolicyRequest;
import com.opensocket.aievent.core.iam.api.request.TokenPolicyRequest;
import com.opensocket.aievent.core.iam.api.response.SecurityPolicyResponse;
import com.opensocket.aievent.core.iam.api.security.IamPermissionGuard;
import com.opensocket.aievent.core.iam.api.security.IamPermissions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/access/security/policies")
@ConditionalOnBean({IamSecurityPolicyApiPort.class,IamPermissionGuard.class})
@ConditionalOnProperty(prefix="aeg.iam.api",name="enabled",havingValue="true")
public class IamSecurityPolicyController {
    private final IamSecurityPolicyApiPort port;
    private final IamPermissionGuard guard;
    private final IamApiRequestContextFactory contexts;

    public IamSecurityPolicyController(IamSecurityPolicyApiPort port,IamPermissionGuard guard,IamApiRequestContextFactory contexts){
        this.port=port;this.guard=guard;this.contexts=contexts;
    }

    @GetMapping("/")
    public SecurityPolicyResponse read(HttpServletRequest request){
        var context=contexts.from(request);
        guard.requireTenant(context,IamPermissions.POLICY_READ,"SECURITY_POLICY",context.activeTenantId());
        return port.read(context);
    }

    @PutMapping("/password")
    public SecurityPolicyResponse password(@RequestHeader("If-Match") String ifMatch,@Valid @RequestBody PasswordPolicyRequest body,HttpServletRequest request){
        var context=secure(request);return port.updatePassword(body,context.requireExpectedVersion(ifMatch),context);
    }

    @PutMapping("/mfa")
    public SecurityPolicyResponse mfa(@RequestHeader("If-Match") String ifMatch,@Valid @RequestBody MfaPolicyRequest body,HttpServletRequest request){
        var context=secure(request);return port.updateMfa(body,context.requireExpectedVersion(ifMatch),context);
    }

    @PutMapping("/session")
    public SecurityPolicyResponse session(@RequestHeader("If-Match") String ifMatch,@Valid @RequestBody SessionPolicyRequest body,HttpServletRequest request){
        var context=secure(request);return port.updateSession(body,context.requireExpectedVersion(ifMatch),context);
    }

    @PutMapping("/token")
    public SecurityPolicyResponse token(@RequestHeader("If-Match") String ifMatch,@Valid @RequestBody TokenPolicyRequest body,HttpServletRequest request){
        var context=secure(request);return port.updateToken(body,context.requireExpectedVersion(ifMatch),context);
    }

    @PostMapping("/{policyKind}/restore/{revisionId}")
    public SecurityPolicyResponse restore(@PathVariable String policyKind,@PathVariable String revisionId,@RequestHeader("If-Match") String ifMatch,HttpServletRequest request){
        var context=secure(request);return port.restore(policyKind,revisionId,context.requireExpectedVersion(ifMatch),context);
    }

    private IamApiRequestContext secure(HttpServletRequest request){
        var context=contexts.from(request);context.requireAuditReason();context.requireIdempotencyKey();
        guard.requireTenant(context,IamPermissions.POLICY_MANAGE,"SECURITY_POLICY",context.activeTenantId());return context;
    }
}
