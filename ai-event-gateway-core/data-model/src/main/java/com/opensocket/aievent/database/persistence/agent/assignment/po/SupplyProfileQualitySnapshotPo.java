package com.opensocket.aievent.database.persistence.agent.assignment.po;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SupplyProfileQualitySnapshotPo extends AgentQualityMetricsWindowPo {
    private String snapshotId;
}
