package com.opensocket.aievent.core.identity;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "core.security.admin")
public class AdminIdentityProperties {
    private boolean enabled = false;
    private Duration sessionTimeout = Duration.ofMinutes(30);
    private String sessionCookieName = "OPENDISPATCH_ADMIN_SESSION";
    private String sessionRedisNamespace = "opendispatch:core:admin:sessions";
    private boolean cookieSecure = false;
    private String cookieSameSite = "lax";
    private final Bootstrap bootstrap = new Bootstrap();
    private List<Bootstrap> accounts = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Duration getSessionTimeout() { return sessionTimeout; }
    public void setSessionTimeout(Duration sessionTimeout) { this.sessionTimeout = sessionTimeout == null ? Duration.ofMinutes(30) : sessionTimeout; }
    public String getSessionCookieName() { return sessionCookieName; }
    public void setSessionCookieName(String sessionCookieName) { this.sessionCookieName = normalized(sessionCookieName, "OPENDISPATCH_ADMIN_SESSION"); }
    public String getSessionRedisNamespace() { return sessionRedisNamespace; }
    public void setSessionRedisNamespace(String sessionRedisNamespace) { this.sessionRedisNamespace = normalized(sessionRedisNamespace, "opendispatch:core:admin:sessions"); }
    public boolean isCookieSecure() { return cookieSecure; }
    public void setCookieSecure(boolean cookieSecure) { this.cookieSecure = cookieSecure; }
    public String getCookieSameSite() { return cookieSameSite; }
    public void setCookieSameSite(String cookieSameSite) { this.cookieSameSite = normalized(cookieSameSite, "lax").toLowerCase(); }
    public Bootstrap getBootstrap() { return bootstrap; }
    public List<Bootstrap> getAccounts() { return accounts; }
    public void setAccounts(List<Bootstrap> accounts) { this.accounts = accounts == null ? new ArrayList<>() : new ArrayList<>(accounts); }

    public static final class Bootstrap {
        private String userId = "admin";
        private String username = "admin";
        private String displayName = "OpenDispatch Administrator";
        private String passwordHash = "";
        private String plaintextPassword = "";
        private boolean allowPlaintextPassword = false;
        private Set<AdminRole> roles = new LinkedHashSet<>(Set.of(AdminRole.ADMIN));
        private Set<String> allowedTenantIds = new LinkedHashSet<>();
        private String defaultTenantId = "";
        private boolean enabled = true;

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = normalized(userId, "admin"); }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = normalized(username, "admin"); }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = normalized(displayName, "OpenDispatch Administrator"); }
        public String getPasswordHash() { return passwordHash; }
        public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash == null ? "" : passwordHash.trim(); }
        public String getPlaintextPassword() { return plaintextPassword; }
        public void setPlaintextPassword(String plaintextPassword) { this.plaintextPassword = plaintextPassword == null ? "" : plaintextPassword; }
        public boolean isAllowPlaintextPassword() { return allowPlaintextPassword; }
        public void setAllowPlaintextPassword(boolean allowPlaintextPassword) { this.allowPlaintextPassword = allowPlaintextPassword; }
        public Set<AdminRole> getRoles() { return roles; }
        public void setRoles(Set<AdminRole> roles) { this.roles = roles == null || roles.isEmpty() ? new LinkedHashSet<>(Set.of(AdminRole.VIEWER)) : new LinkedHashSet<>(roles); }
        public Set<String> getAllowedTenantIds() { return allowedTenantIds; }
        public void setAllowedTenantIds(Set<String> allowedTenantIds) { this.allowedTenantIds = normalizeTenants(allowedTenantIds); }
        public String getDefaultTenantId() { return defaultTenantId; }
        public void setDefaultTenantId(String defaultTenantId) { this.defaultTenantId = defaultTenantId == null ? "" : defaultTenantId.trim(); }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        private static Set<String> normalizeTenants(Set<String> values) {
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            if (values != null) {
                values.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).forEach(normalized::add);
            }
            return normalized;
        }
    }

    private static String normalized(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
