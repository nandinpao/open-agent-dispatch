package com.opensocket.aievent.core.iam.identity.application.service;

import com.opensocket.aievent.core.iam.identity.application.port.in.IdentityQueryPort;
import com.opensocket.aievent.core.iam.identity.application.port.out.HumanUserRepository;
import com.opensocket.aievent.core.iam.identity.application.port.out.RootIdentityRepository;
import com.opensocket.aievent.core.iam.identity.application.query.FindHumanUserByEmailQuery;
import com.opensocket.aievent.core.iam.identity.application.query.FindHumanUserByUsernameQuery;
import com.opensocket.aievent.core.iam.identity.application.query.FindHumanUserQuery;
import com.opensocket.aievent.core.iam.identity.application.query.HumanUserPage;
import com.opensocket.aievent.core.iam.identity.application.query.SearchHumanUsersQuery;
import com.opensocket.aievent.core.iam.identity.domain.EmailAddress;
import com.opensocket.aievent.core.iam.identity.domain.HumanUser;
import com.opensocket.aievent.core.iam.identity.domain.Username;
import com.opensocket.aievent.core.iam.identity.domain.RootIdentity;
import com.opensocket.aievent.core.iam.identity.domain.UserId;
import java.util.Objects;
import java.util.Optional;

public final class IdentityQueryService implements IdentityQueryPort {
    private final HumanUserRepository users;
    private final RootIdentityRepository roots;

    public IdentityQueryService(HumanUserRepository users, RootIdentityRepository roots) {
        this.users = Objects.requireNonNull(users, "users");
        this.roots = Objects.requireNonNull(roots, "roots");
    }

    public Optional<HumanUser> findHumanUser(FindHumanUserQuery query) {
        return users.findById(new UserId(query.userId()));
    }

    public Optional<HumanUser> findHumanUserByUsername(FindHumanUserByUsernameQuery query) {
        return users.findByNormalizedUsername(new Username(query.username()).normalizedValue());
    }

    public Optional<HumanUser> findHumanUserByEmail(FindHumanUserByEmailQuery query) {
        if (query.email() == null || query.email().isBlank()) return Optional.empty();
        return users.findByNormalizedEmail(new EmailAddress(query.email()).normalizedValue());
    }

    public HumanUserPage searchHumanUsers(SearchHumanUsersQuery query) {
        return users.search(query);
    }

    public Optional<RootIdentity> findRootIdentity() {
        return roots.find();
    }
}
