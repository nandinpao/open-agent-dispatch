package com.opensocket.aievent.core.iam.identity.application.query;

import com.opensocket.aievent.core.iam.identity.domain.HumanUser;
import java.util.List;
import java.util.Optional;

public record HumanUserPage(List<HumanUser> items, Optional<String> nextCursor) {
    public HumanUserPage {
        items = items == null ? List.of() : List.copyOf(items);
        nextCursor = nextCursor == null ? Optional.empty() : nextCursor;
    }
}
