package com.opensocket.aievent.core.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;

/** Bootstrap adapter used until a database or enterprise IdP adapter is enabled. */
@Repository
public class ConfiguredAdminIdentityRepository implements AdminIdentityRepository {
    private static final Logger log = LoggerFactory.getLogger(ConfiguredAdminIdentityRepository.class);
    private final AdminIdentityProperties properties;
    private final List<AdminAccount> configuredAccounts;

    public ConfiguredAdminIdentityRepository(AdminIdentityProperties properties, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.configuredAccounts = buildConfiguredAccounts(passwordEncoder);
    }

    @Override
    public Optional<AdminAccount> findByUsername(String username) {
        if (!properties.isEnabled() || username == null || username.isBlank()) return Optional.empty();
        String normalized = normalizedUsername(username);
        return configuredAccounts.stream().filter(account -> normalizedUsername(account.username()).equals(normalized)).findFirst();
    }

    @Override
    public List<AdminAccount> findAll() { return properties.isEnabled() ? configuredAccounts : List.of(); }

    private List<AdminAccount> buildConfiguredAccounts(PasswordEncoder passwordEncoder) {
        if (!properties.isEnabled()) return List.of();

        ArrayList<AdminIdentityProperties.Bootstrap> definitions = new ArrayList<>();
        if (properties.getBootstrap().isEnabled()) {
            definitions.add(properties.getBootstrap());
        }
        properties.getAccounts().stream()
                .filter(AdminIdentityProperties.Bootstrap::isEnabled)
                .forEach(definitions::add);

        ArrayList<AdminAccount> accounts = new ArrayList<>();
        for (AdminIdentityProperties.Bootstrap definition : definitions) {
            validate(definition);
            String passwordHash = resolvePasswordHash(definition, passwordEncoder);
            AdminAccount account = new AdminAccount(definition.getUserId(), definition.getUsername(), definition.getDisplayName(),
                    passwordHash, definition.getRoles(), definition.getAllowedTenantIds(), selectedDefaultTenant(definition), true);
            accounts.add(account);
            log.info("Core Admin account configured username={} credentialSource={} roles={} tenants={} defaultTenant={}",
                    account.username(), credentialSource(definition), account.roles(), account.allowedTenantIds(), account.defaultTenantId());
        }
        long uniqueUsers = accounts.stream().map(account -> normalizedUsername(account.username())).distinct().count();
        if (uniqueUsers != accounts.size()) throw new IllegalStateException("Core Admin usernames must be unique.");
        return List.copyOf(accounts);
    }

    private String resolvePasswordHash(AdminIdentityProperties.Bootstrap account, PasswordEncoder passwordEncoder) {
        if (usesPlaintextPassword(account)) {
            if (!account.getPasswordHash().isBlank()) {
                log.warn("Core Admin password hash is ignored because explicit local/CI plaintext mode is enabled for username={}.", account.getUsername());
            }
            return passwordEncoder.encode(account.getPlaintextPassword());
        }
        return account.getPasswordHash();
    }

    private String credentialSource(AdminIdentityProperties.Bootstrap account) {
        return usesPlaintextPassword(account) ? "LOCAL_PLAINTEXT_ENCODED_AT_STARTUP" : "DELEGATING_PASSWORD_HASH";
    }

    private boolean usesPlaintextPassword(AdminIdentityProperties.Bootstrap account) {
        return account.isAllowPlaintextPassword() && !account.getPlaintextPassword().isBlank();
    }

    private void validate(AdminIdentityProperties.Bootstrap account) {
        if (account.getAllowedTenantIds().isEmpty()) throw new IllegalStateException("Core Admin account requires at least one allowed tenant: " + account.getUsername());
        if (!account.getAllowedTenantIds().contains(selectedDefaultTenant(account))) throw new IllegalStateException("Core Admin default tenant must be included in allowed tenants: " + account.getUsername());
        if (!usesPlaintextPassword(account) && account.getPasswordHash().isBlank())
            throw new IllegalStateException("Core Admin account has no password hash: " + account.getUsername());
        if (!usesPlaintextPassword(account) && !account.getPasswordHash().isBlank() && !isDelegatingPasswordHash(account.getPasswordHash()))
            throw new IllegalStateException("Core Admin password hash must use delegating format: " + account.getUsername());
        if (usesPlaintextPassword(account))
            log.warn("Core Admin bootstrap plaintext password mode is enabled for username={}. Use only in local/CI.", account.getUsername());
    }

    private boolean isDelegatingPasswordHash(String value) {
        int closingBrace = value.indexOf('}'); return value.startsWith("{") && closingBrace > 1 && closingBrace < value.length() - 1;
    }
    private String selectedDefaultTenant(AdminIdentityProperties.Bootstrap account) {
        if (account.getDefaultTenantId() != null && !account.getDefaultTenantId().isBlank()) return account.getDefaultTenantId().trim();
        return account.getAllowedTenantIds().stream().findFirst().orElse("");
    }
    static String normalizedUsername(String username) { return username == null ? "" : username.trim().toLowerCase(Locale.ROOT); }
}
