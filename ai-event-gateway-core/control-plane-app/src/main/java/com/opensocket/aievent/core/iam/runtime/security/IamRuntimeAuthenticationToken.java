package com.opensocket.aievent.core.iam.runtime.security;

import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/** Spring Security token backed by canonical IAM authentication (browser session or human PAT). */
public final class IamRuntimeAuthenticationToken extends AbstractAuthenticationToken {
    private final AuthenticationContext context;
    private final Set<String> credentialPermissionBoundary;
    private final String credentialId;
    private final String authenticationMethod;

    public IamRuntimeAuthenticationToken(AuthenticationContext context) {
        this(context, List.of(), Set.of(), "", "BROWSER_SESSION");
    }

    public IamRuntimeAuthenticationToken(AuthenticationContext context, Collection<? extends GrantedAuthority> authorities) {
        this(context, authorities, Set.of(), "", "BROWSER_SESSION");
    }

    public IamRuntimeAuthenticationToken(AuthenticationContext context,
                                         Collection<? extends GrantedAuthority> authorities,
                                         Set<String> credentialPermissionBoundary,
                                         String credentialId,
                                         String authenticationMethod) {
        super(authorities == null ? List.of() : authorities);
        this.context = context;
        this.credentialPermissionBoundary = credentialPermissionBoundary == null ? Set.of() : Set.copyOf(credentialPermissionBoundary);
        this.credentialId = credentialId == null ? "" : credentialId.trim();
        this.authenticationMethod = authenticationMethod == null || authenticationMethod.isBlank() ? "UNKNOWN" : authenticationMethod.trim();
        setAuthenticated(true);
    }

    public Set<String> credentialPermissionBoundary() { return credentialPermissionBoundary; }
    public boolean credentialBounded() { return !credentialPermissionBoundary.isEmpty(); }
    public boolean permitsCredentialPermission(String permission) { return !credentialBounded() || credentialPermissionBoundary.contains(permission); }
    public String credentialId() { return credentialId; }
    public String authenticationMethod() { return authenticationMethod; }

    @Override public Object getCredentials() { return ""; }
    @Override public AuthenticationContext getPrincipal() { return context; }
}
