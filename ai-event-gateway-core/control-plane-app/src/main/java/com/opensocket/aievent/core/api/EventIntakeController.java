package com.opensocket.aievent.core.api;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.ObjectProvider;
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
import com.opensocket.aievent.core.iam.runtime.eventintake.EventIntakeAuthorityEnforcer;
import com.opensocket.aievent.core.iam.runtime.eventintake.EventIntakeAuthorizationEvidence;
import com.opensocket.aievent.core.workload.EventIntakeWorkloadContextFactory;

import jakarta.validation.Valid;

@RestController
@Validated
@RequestMapping("/api/events")
public class EventIntakeController {
    private final EventIntakeApplicationService eventIntakeApplicationService;
    private final EventDecisionQueryService decisionQueryService;
    private final ObjectProvider<EventIntakeAuthorityEnforcer> eventIntakeAuthorityEnforcer;
    private final ObjectProvider<EventIntakeWorkloadContextFactory> workloadContextFactory;

    @org.springframework.beans.factory.annotation.Autowired
    public EventIntakeController(EventIntakeApplicationService eventIntakeApplicationService,
                                 EventDecisionQueryService decisionQueryService,
                                 ObjectProvider<EventIntakeAuthorityEnforcer> eventIntakeAuthorityEnforcer,
                                 ObjectProvider<EventIntakeWorkloadContextFactory> workloadContextFactory) {
        this.eventIntakeApplicationService = eventIntakeApplicationService;
        this.decisionQueryService = decisionQueryService;
        this.eventIntakeAuthorityEnforcer = eventIntakeAuthorityEnforcer;
        this.workloadContextFactory = workloadContextFactory;
    }


    @PostMapping("/intake")
    public EventIntakeDecisionResponse intake(@Valid @RequestBody EventIntakeRequest request, HttpServletRequest servletRequest) {
        EventIntakeAuthorityEnforcer enforcer = eventIntakeAuthorityEnforcer.getIfAvailable();
        EventIntakeAuthorizationEvidence evidence = enforcer == null
                ? EventIntakeAuthorizationEvidence.unenforced()
                : enforcer.authorize(request, servletRequest);
        EventIntakeWorkloadContextFactory factory = workloadContextFactory.getIfAvailable();
        if (factory != null) request.attachServerWorkloadContext(factory.capture(request, servletRequest, evidence));
        return eventIntakeApplicationService.intake(request);
    }

    @GetMapping("/decisions/recent")
    public List<EventDecisionRecord> recentDecisions(@RequestParam(defaultValue = "50") int limit) {
        return decisionQueryService.recent(limit);
    }
}
