package com.opensocket.aievent.core.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

class ConfiguredAdminIdentityRepositoryTest {
    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Test
    void remainsEmptyWhenHumanAuthenticationIsDisabled() {
        AdminIdentityProperties properties = new AdminIdentityProperties();
        ConfiguredAdminIdentityRepository repository = new ConfiguredAdminIdentityRepository(properties, passwordEncoder);

        assertThat(repository.findByUsername("admin")).isEmpty();
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void permitsBootstrapAdapterToBeDisabledForFutureDatabaseOrIdpReplacement() {
        AdminIdentityProperties properties = new AdminIdentityProperties();
        properties.setEnabled(true);
        properties.getBootstrap().setEnabled(false);
        ConfiguredAdminIdentityRepository repository = new ConfiguredAdminIdentityRepository(properties, passwordEncoder);

        assertThat(repository.findByUsername("admin")).isEmpty();
    }

    @Test
    void loadsConfiguredAccountCaseInsensitivelyAndPreservesHash() {
        AdminIdentityProperties properties = enabledProperties();
        properties.getBootstrap().setPasswordHash("{noop}secret");
        ConfiguredAdminIdentityRepository repository = new ConfiguredAdminIdentityRepository(properties, passwordEncoder);

        AdminAccount account = repository.findByUsername(" ADMIN ").orElseThrow();
        assertThat(account.passwordHash()).isEqualTo("{noop}secret");
        assertThat(account.roles()).containsExactly(AdminRole.ADMIN);
        assertThat(account.allowedTenantIds()).containsExactlyInAnyOrder("tenant-a", "tenant-b");
        assertThat(account.defaultTenantId()).isEqualTo("tenant-a");
    }

    @Test
    void loadsMultipleConfiguredAccountsWithIndependentRolesAndTenantScopes() {
        AdminIdentityProperties properties = new AdminIdentityProperties();
        properties.setEnabled(true);
        properties.getBootstrap().setEnabled(false);
        properties.setAccounts(List.of(
                account("admin-e2e", AdminRole.ADMIN, Set.of("tenant-a", "tenant-b"), "tenant-a"),
                account("operator-e2e", AdminRole.OPERATOR, Set.of("tenant-a"), "tenant-a"),
                account("viewer-e2e", AdminRole.VIEWER, Set.of("tenant-b"), "tenant-b")));

        ConfiguredAdminIdentityRepository repository = new ConfiguredAdminIdentityRepository(properties, passwordEncoder);

        assertThat(repository.findAll()).extracting(AdminAccount::username)
                .containsExactly("admin-e2e", "operator-e2e", "viewer-e2e");
        assertThat(repository.findByUsername("operator-e2e")).get().satisfies(operator -> {
            assertThat(operator.roles()).containsExactly(AdminRole.OPERATOR);
            assertThat(operator.allowedTenantIds()).containsExactly("tenant-a");
        });
        assertThat(repository.findByUsername("viewer-e2e")).get().satisfies(viewer -> {
            assertThat(viewer.roles()).containsExactly(AdminRole.VIEWER);
            assertThat(viewer.defaultTenantId()).isEqualTo("tenant-b");
        });
    }


    @Test
    void keepsBootstrapAdministratorWhenAdditionalAccountsAreEnabled() {
        AdminIdentityProperties properties = enabledProperties();
        properties.getBootstrap().setPasswordHash("{noop}bootstrap-secret");
        properties.setAccounts(List.of(
                account("admin-e2e", AdminRole.ADMIN, Set.of("tenant-a"), "tenant-a"),
                account("viewer-e2e", AdminRole.VIEWER, Set.of("tenant-b"), "tenant-b")));

        ConfiguredAdminIdentityRepository repository = new ConfiguredAdminIdentityRepository(properties, passwordEncoder);

        assertThat(repository.findAll()).extracting(AdminAccount::username)
                .containsExactly("admin", "admin-e2e", "viewer-e2e");
        assertThat(repository.findByUsername("admin")).isPresent();
    }

    @Test
    void explicitLocalPlaintextTakesPrecedenceOverStalePasswordHash() {
        AdminIdentityProperties properties = enabledProperties();
        properties.getBootstrap().setPasswordHash("{noop}stale-password-from-host-environment");
        properties.getBootstrap().setAllowPlaintextPassword(true);
        properties.getBootstrap().setPlaintextPassword("local-admin-change-me");

        ConfiguredAdminIdentityRepository repository = new ConfiguredAdminIdentityRepository(properties, passwordEncoder);
        AdminAccount account = repository.findByUsername("admin").orElseThrow();

        assertThat(passwordEncoder.matches("local-admin-change-me", account.passwordHash())).isTrue();
        assertThat(passwordEncoder.matches("stale-password-from-host-environment", account.passwordHash())).isFalse();
    }

    @Test
    void rejectsDuplicateConfiguredUsernamesCaseInsensitively() {
        AdminIdentityProperties properties = new AdminIdentityProperties();
        properties.setEnabled(true);
        properties.getBootstrap().setEnabled(false);
        properties.setAccounts(List.of(
                account("Admin-E2E", AdminRole.ADMIN, Set.of("tenant-a"), "tenant-a"),
                account(" admin-e2e ", AdminRole.VIEWER, Set.of("tenant-a"), "tenant-a")));

        assertThatThrownBy(() -> new ConfiguredAdminIdentityRepository(properties, passwordEncoder))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("usernames must be unique");
    }

    @Test
    void encodesExplicitLocalPlaintextOnlyOnceAtStartup() {
        AdminIdentityProperties properties = enabledProperties();
        properties.getBootstrap().setAllowPlaintextPassword(true);
        properties.getBootstrap().setPlaintextPassword("local-secret");
        ConfiguredAdminIdentityRepository repository = new ConfiguredAdminIdentityRepository(properties, passwordEncoder);

        AdminAccount first = repository.findByUsername("admin").orElseThrow();
        AdminAccount second = repository.findByUsername("admin").orElseThrow();
        assertThat(first.passwordHash()).isEqualTo(second.passwordHash());
        assertThat(passwordEncoder.matches("local-secret", first.passwordHash())).isTrue();
    }

    @Test
    void failsClosedWhenEnabledWithoutCredential() {
        AdminIdentityProperties properties = enabledProperties();

        assertThatThrownBy(() -> new ConfiguredAdminIdentityRepository(properties, passwordEncoder))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no password hash");
    }

    @Test
    void failsClosedWhenPasswordHashDoesNotUseDelegatingFormat() {
        AdminIdentityProperties properties = enabledProperties();
        properties.getBootstrap().setPasswordHash("raw-bcrypt-or-placeholder");

        assertThatThrownBy(() -> new ConfiguredAdminIdentityRepository(properties, passwordEncoder))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("delegating format");
    }

    @Test
    void failsClosedWhenDefaultTenantIsOutsideAllowedSet() {
        AdminIdentityProperties properties = enabledProperties();
        properties.getBootstrap().setPasswordHash("{noop}secret");
        properties.getBootstrap().setDefaultTenantId("tenant-x");

        assertThatThrownBy(() -> new ConfiguredAdminIdentityRepository(properties, passwordEncoder))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("default tenant");
    }

    private AdminIdentityProperties enabledProperties() {
        AdminIdentityProperties properties = new AdminIdentityProperties();
        properties.setEnabled(true);
        properties.getBootstrap().setUsername("admin");
        properties.getBootstrap().setAllowedTenantIds(Set.of("tenant-a", "tenant-b"));
        properties.getBootstrap().setDefaultTenantId("tenant-a");
        return properties;
    }

    private AdminIdentityProperties.Bootstrap account(String username,
                                                       AdminRole role,
                                                       Set<String> tenants,
                                                       String defaultTenant) {
        AdminIdentityProperties.Bootstrap account = new AdminIdentityProperties.Bootstrap();
        account.setUserId("user-" + username.trim().toLowerCase());
        account.setUsername(username);
        account.setDisplayName(username.trim());
        account.setPasswordHash("{noop}secret");
        account.setRoles(Set.of(role));
        account.setAllowedTenantIds(tenants);
        account.setDefaultTenantId(defaultTenant);
        return account;
    }
}
