package com.opensocket.aievent.core.api;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.opensocket.aievent.core.decision.EventDecisionRecord;
import com.opensocket.aievent.core.decision.EventDecisionQueryService;
import com.opensocket.aievent.core.decision.EventIntakeApplicationService;
import com.opensocket.aievent.core.decision.EventIntakeDecisionResponse;
import com.opensocket.aievent.core.event.EventIntakeRequest;

import jakarta.validation.Valid;

@RestController
@Validated
@RequestMapping("/api/events")
public class EventIntakeController {
    private final EventIntakeApplicationService eventIntakeApplicationService;
    private final EventDecisionQueryService decisionQueryService;

    public EventIntakeController(EventIntakeApplicationService eventIntakeApplicationService,
                                 EventDecisionQueryService decisionQueryService) {
        this.eventIntakeApplicationService = eventIntakeApplicationService;
        this.decisionQueryService = decisionQueryService;
    }

    @PostMapping("/intake")
    public EventIntakeDecisionResponse intake(@Valid @RequestBody EventIntakeRequest request) {
        return eventIntakeApplicationService.intake(request);
    }

    @GetMapping("/decisions/recent")
    public List<EventDecisionRecord> recentDecisions(@RequestParam(defaultValue = "50") int limit) {
        return decisionQueryService.recent(limit);
    }
}
