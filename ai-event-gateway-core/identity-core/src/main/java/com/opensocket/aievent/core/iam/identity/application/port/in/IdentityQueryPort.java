package com.opensocket.aievent.core.iam.identity.application.port.in;

import com.opensocket.aievent.core.iam.identity.application.query.FindHumanUserByEmailQuery;
import com.opensocket.aievent.core.iam.identity.application.query.FindHumanUserByUsernameQuery;
import com.opensocket.aievent.core.iam.identity.application.query.FindHumanUserQuery;
import com.opensocket.aievent.core.iam.identity.application.query.HumanUserPage;
import com.opensocket.aievent.core.iam.identity.application.query.SearchHumanUsersQuery;
import com.opensocket.aievent.core.iam.identity.domain.HumanUser;
import com.opensocket.aievent.core.iam.identity.domain.RootIdentity;
import java.util.Optional;

public interface IdentityQueryPort {
    Optional<HumanUser> findHumanUser(FindHumanUserQuery query);
    Optional<HumanUser> findHumanUserByUsername(FindHumanUserByUsernameQuery query);
    Optional<HumanUser> findHumanUserByEmail(FindHumanUserByEmailQuery query);
    HumanUserPage searchHumanUsers(SearchHumanUsersQuery query);
    Optional<RootIdentity> findRootIdentity();
}
