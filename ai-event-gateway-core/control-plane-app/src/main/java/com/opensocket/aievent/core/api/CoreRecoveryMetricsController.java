package com.opensocket.aievent.core.api;

import java.time.Duration;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.opensocket.aievent.core.observability.RecoveryOperationMetricsService;
import com.opensocket.aievent.core.observability.RecoveryOperationMetricsSnapshot;

@RestController
@RequestMapping("/admin/recovery")
public class CoreRecoveryMetricsController {
    private final RecoveryOperationMetricsService service;

    public CoreRecoveryMetricsController(RecoveryOperationMetricsService service) {
        this.service = service;
    }

    @GetMapping("/metrics")
    public RecoveryOperationMetricsSnapshot metrics(@RequestParam(defaultValue = "15") int windowMinutes,
                                                    @RequestParam(defaultValue = "2000") int limit) {
        return service.snapshot(Duration.ofMinutes(Math.max(1, Math.min(windowMinutes, 1440))), limit);
    }
}
