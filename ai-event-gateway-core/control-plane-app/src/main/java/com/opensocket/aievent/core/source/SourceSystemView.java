package com.opensocket.aievent.core.source;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SourceSystemView {
    private String tenantId;
    private String sourceSystemId;
    private String displayName;
    private String description;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
