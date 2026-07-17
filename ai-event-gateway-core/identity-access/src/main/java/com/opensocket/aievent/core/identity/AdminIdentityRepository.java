package com.opensocket.aievent.core.identity;

import java.util.List;
import java.util.Optional;

/** Port for human administrator identities. A database/IdP adapter can replace the bootstrap adapter. */
public interface AdminIdentityRepository {
    Optional<AdminAccount> findByUsername(String username);
    default List<AdminAccount> findAll() { return List.of(); }
}
