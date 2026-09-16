package com.opensocket.aievent.core.api;

import com.opensocket.aievent.core.eventquery.*;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedBusinessResourceAccessCoordinator;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

/** Canonical enterprise Business Event API. Legacy /events remains a SELF/developer runtime diagnostic surface. */
@RestController
@RequestMapping("/admin/business-events")
public final class BusinessEventController {
    private final BusinessEventQueryService queries;
    private final ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess;
    public BusinessEventController(BusinessEventQueryService queries,ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess){this.queries=queries;this.scopedAccess=scopedAccess;}

    @GetMapping
    public List<BusinessEventView> list(@RequestParam(required=false) String sourceSystem,@RequestParam(required=false) String eventType,@RequestParam(defaultValue="100") int limit){
        var guard=requiredGuard();
        var plan=guard.plan("admin.business.event.list",ResourceType.EVENT,VisibilityLevel.STANDARD,"BUSINESS_EVENT_LIST");
        return queries.list(plan,sourceSystem,eventType,limit);
    }

    @GetMapping("/{eventId}")
    public BusinessEventView detail(@PathVariable String eventId){
        var guard=requiredGuard();
        guard.authorize(ResourceType.EVENT,eventId,"admin.business.event.detail",ResourceAction.ActionKind.READ,false,VisibilityLevel.STANDARD,"BUSINESS_EVENT_DETAIL");
        return queries.find(guard.activeTenantId(),eventId).orElseThrow(()->new StandardApiException(StandardApiErrorCode.NOT_FOUND,"Business Event not found: "+eventId));
    }

    @GetMapping("/{eventId}/payload")
    public BusinessEventPayloadView payload(@PathVariable String eventId){
        var guard=requiredGuard();
        guard.authorize(ResourceType.EVENT,eventId,"admin.business.event.payload.read",ResourceAction.ActionKind.READ,false,VisibilityLevel.SENSITIVE,"BUSINESS_EVENT_PAYLOAD");
        return queries.payload(guard.activeTenantId(),eventId).orElseThrow(()->new StandardApiException(StandardApiErrorCode.NOT_FOUND,"Business Event not found: "+eventId));
    }

    private ScopedBusinessResourceAccessCoordinator requiredGuard(){
        var guard=scopedAccess.getIfAvailable();
        if(guard==null) throw new StandardApiException(StandardApiErrorCode.FORBIDDEN,"Business Event access requires Resource Access enforcement.");
        return guard;
    }
}
