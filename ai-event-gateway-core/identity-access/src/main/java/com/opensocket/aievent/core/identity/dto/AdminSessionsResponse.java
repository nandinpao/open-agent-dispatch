package com.opensocket.aievent.core.identity.dto;

import java.util.List;

public record AdminSessionsResponse(String currentSessionReference, List<AdminSessionDescriptor> sessions) {
    public AdminSessionsResponse {
        currentSessionReference = currentSessionReference == null ? "" : currentSessionReference;
        sessions = sessions == null ? List.of() : List.copyOf(sessions);
    }
}
