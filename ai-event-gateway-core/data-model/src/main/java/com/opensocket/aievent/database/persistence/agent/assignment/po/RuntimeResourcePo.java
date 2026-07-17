package com.opensocket.aievent.database.persistence.agent.assignment.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RuntimeResourcePo {
    private String tenantId;
    private String runtimeId;
    private String runtimeCode;
    private String runtimeName;
    private String runtimeType;
    private String connectorType;
    private String executionHost;
    private String environment;
    private String region;
    private String zone;
    private String trustStatus;
    private String status;
    private int capacityLimit;
    private String metadataJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
