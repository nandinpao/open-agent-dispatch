package com.opensocket.aievent.core.iam.identity.application.service;

import static org.junit.jupiter.api.Assertions.*;
import com.opensocket.aievent.core.iam.identity.application.command.CreateHumanUserCommand;
import com.opensocket.aievent.core.iam.identity.application.port.out.*;
import com.opensocket.aievent.core.iam.identity.application.query.*;
import com.opensocket.aievent.core.iam.identity.domain.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class IdentityApplicationServiceTest {
    @Test void duplicateNormalizedUsernameIsRejected() {
        InMemoryUsers users = new InMemoryUsers();
        IdentityApplicationService service = new IdentityApplicationService(users, new InMemoryRoot(), event -> {},
                Clock.fixed(Instant.parse("2026-07-23T00:00:00Z"), ZoneOffset.UTC));
        service.createHumanUser(new CreateHumanUserCommand("u-1", "David", "david@example.com", "David",
                UserCreationMode.ADMIN_CREATED, "admin", "c-1", "e-1"));
        IdentityDomainException error = assertThrows(IdentityDomainException.class, () -> service.createHumanUser(
                new CreateHumanUserCommand("u-2", "DAVID", "david2@example.com", "David 2",
                        UserCreationMode.ADMIN_CREATED, "admin", "c-2", "e-2")));
        assertEquals(IdentityReasonCode.USERNAME_CONFLICT, error.reasonCode());
    }

    private static final class InMemoryUsers implements HumanUserRepository {
        private final Map<UserId, HumanUser> values = new HashMap<>();
        public Optional<HumanUser> findById(UserId id) { return Optional.ofNullable(values.get(id)); }
        public Optional<HumanUser> findByNormalizedUsername(String value) { return values.values().stream().filter(v -> v.username().normalizedValue().equals(value)).findFirst(); }
        public Optional<HumanUser> findByNormalizedEmail(String value) { return values.values().stream().filter(v -> v.email().map(EmailAddress::normalizedValue).orElse("").equals(value)).findFirst(); }
        public boolean existsByNormalizedUsername(String value) { return values.values().stream().anyMatch(v -> v.username().normalizedValue().equals(value)); }
        public boolean existsByNormalizedEmail(String value) { return values.values().stream().flatMap(v -> v.email().stream()).anyMatch(v -> v.normalizedValue().equals(value)); }
        public HumanUser save(HumanUser user, long expectedVersion) { values.put(user.userId(), user); return user; }
        public HumanUserPage search(SearchHumanUsersQuery query) { return new HumanUserPage(List.copyOf(values.values()), Optional.empty()); }
    }
    private static final class InMemoryRoot implements RootIdentityRepository {
        private RootIdentity value; public Optional<RootIdentity> find() { return Optional.ofNullable(value); }
        public RootIdentity save(RootIdentity root, long expectedVersion) { value = root; return root; }
    }
}
