package com.opensocket.aievent.core.iam.identity.application.query;

import com.opensocket.aievent.core.iam.identity.domain.AccountStatus;
import java.util.Optional;

public record SearchHumanUsersQuery(String text, Optional<AccountStatus> status, int limit, Optional<String> cursor) {
    public SearchHumanUsersQuery {
        text = text == null ? "" : text.trim();
        status = status == null ? Optional.empty() : status;
        cursor = cursor == null ? Optional.empty() : cursor;
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
    }
}
