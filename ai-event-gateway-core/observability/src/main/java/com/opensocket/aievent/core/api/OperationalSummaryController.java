package com.opensocket.aievent.core.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.opensocket.aievent.core.observability.OperationalSummary;
import com.opensocket.aievent.core.observability.OperationalSummaryService;

@RestController
@RequestMapping("/api/ops")
public class OperationalSummaryController {
    private final OperationalSummaryService summaryService;

    public OperationalSummaryController(OperationalSummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping("/summary")
    public OperationalSummary summary() {
        return summaryService.summary();
    }
}
