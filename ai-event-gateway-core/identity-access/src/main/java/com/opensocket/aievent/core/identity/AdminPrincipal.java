package com.opensocket.aievent.core.identity;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Serializable session principal for human Admin UI users. */
public final class AdminPrincipal implements UserDetails, CredentialsContainer, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String userId;
    private final String username;
    private final String displayName;
    private String passwordHash;
    private final Set<AdminRole> roles;
    private final Set<String> permissions;
    private final Set<String> allowedTenantIds;
    private final String selectedTenantId;
    private final boolean enabled;

    private AdminPrincipal(AdminAccount account, String selectedTenantId, String passwordHash) {
        this.userId = account.userId();
        this.username = account.username();
        this.displayName = account.displayName();
        this.passwordHash = passwordHash;
        this.roles = Set.copyOf(account.roles());
        this.permissions = permissionCodes(account.roles());
        this.allowedTenantIds = Set.copyOf(account.allowedTenantIds());
        this.selectedTenantId = selectedTenantId;
        this.enabled = account.enabled();
    }

    public static AdminPrincipal from(AdminAccount account) {
        String selected = account.defaultTenantId();
        if (selected == null || selected.isBlank()) {
            selected = account.allowedTenantIds().stream().findFirst().orElse("");
        }
        return new AdminPrincipal(account, selected, account.passwordHash());
    }

    public AdminPrincipal withSelectedTenant(String tenantId) {
        String normalized = tenantId == null ? "" : tenantId.trim();
        if (!allowedTenantIds.contains(normalized)) {
            throw new IllegalArgumentException("Tenant is not allowed for the authenticated user: " + normalized);
        }
        AdminAccount account = new AdminAccount(userId, username, displayName, passwordHash, roles, allowedTenantIds, normalized, enabled);
        return new AdminPrincipal(account, normalized, passwordHash);
    }

    public String userId() { return userId; }
    public String displayName() { return displayName; }
    public Set<AdminRole> roles() { return roles; }
    public Set<String> permissions() { return permissions; }
    public Set<String> allowedTenantIds() { return allowedTenantIds; }
    public String selectedTenantId() { return selectedTenantId; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        LinkedHashSet<GrantedAuthority> authorities = new LinkedHashSet<>();
        roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name())));
        addCompatibilityRoles(authorities);
        permissions.forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
        return Set.copyOf(authorities);
    }

    private void addCompatibilityRoles(Set<GrantedAuthority> authorities) {
        if (roles.contains(AdminRole.VIEWER) || roles.contains(AdminRole.OPERATOR)
                || roles.contains(AdminRole.RECOVERY_APPROVER) || roles.contains(AdminRole.SUPPORT) || roles.contains(AdminRole.ADMIN)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_VIEWER"));
        }
        if (roles.contains(AdminRole.OPERATOR) || roles.contains(AdminRole.ADMIN)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_OPERATOR"));
            authorities.add(new SimpleGrantedAuthority("ROLE_RECOVERY_OPERATOR"));
        }
        if (roles.contains(AdminRole.RECOVERY_APPROVER) || roles.contains(AdminRole.ADMIN)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_RECOVERY_APPROVER"));
        }
        if (roles.contains(AdminRole.ADMIN)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_RECOVERY_ADMIN"));
        }
    }

    private static Set<String> permissionCodes(Set<AdminRole> roles) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (roles != null) {
            roles.stream().flatMap(role -> role.permissions().stream()).map(AdminPermission::code).forEach(result::add);
        }
        return Set.copyOf(result);
    }

    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void eraseCredentials() { this.passwordHash = null; }
}
