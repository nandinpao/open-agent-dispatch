package com.opensocket.aievent.database.persistence.agent.assignment.po;

import java.time.LocalDate;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentQualityMetricsDailyPo extends AgentQualityMetricsWindowPo {
    private LocalDate metricDate;
}
