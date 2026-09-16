package com.opensocket.aievent.core.api;

import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;
import com.opensocket.aievent.core.event.EventSeverity;
import com.opensocket.aievent.core.incident.*;
import com.opensocket.aievent.core.lifecycle.*;
import com.opensocket.aievent.core.summary.IncidentOccurrenceSummary;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedBusinessResourceAccessCoordinator;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {
    private final IncidentOperationalQuery queryService; private final IncidentLifecycleService lifecycleService;
    private final ScopedIncidentQueryService scopedQueries; private final ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess;
    public IncidentController(IncidentOperationalQuery queryService, IncidentLifecycleService lifecycleService, ScopedIncidentQueryService scopedQueries, ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess) {
        this.queryService=queryService; this.lifecycleService=lifecycleService; this.scopedQueries=scopedQueries; this.scopedAccess=scopedAccess;
    }
    @GetMapping public List<Incident> incidents(@RequestParam(required=false) String tenantId,@RequestParam(required=false) String sourceSystem,@RequestParam(required=false) String siteId,@RequestParam(required=false) String plantId,@RequestParam(required=false) String objectType,@RequestParam(required=false) String objectId,@RequestParam(required=false) String eventType,@RequestParam(required=false) String errorCode,@RequestParam(required=false) String severity,@RequestParam(required=false) String status,@RequestParam(defaultValue="100") int limit){
        IncidentQuery q=query(tenantId,sourceSystem,siteId,plantId,objectType,objectId,eventType,errorCode,severity,status,limit); var guard=scopedAccess.getIfAvailable();
        if(guard==null) return queryService.search(q);
        var plan=guard.plan("api.incident.incidents",ResourceType.INCIDENT,VisibilityLevel.STANDARD,"INCIDENT_LIST"); q.setTenantId(plan.tenantId()); return scopedQueries.search(q,plan);
    }
    @GetMapping("/{incidentId}") public Incident incident(@PathVariable String incidentId){authorize(incidentId,"api.incident.incident",ResourceAction.ActionKind.READ,false,"INCIDENT_DETAIL");return queryService.findById(incidentId).orElseThrow(()->new StandardApiException(StandardApiErrorCode.NOT_FOUND,"Incident not found: "+incidentId));}
    @GetMapping("/{incidentId}/occurrence-summary") public List<IncidentOccurrenceSummary> occurrenceSummary(@PathVariable String incidentId,@RequestParam(defaultValue="100") int limit){authorize(incidentId,"api.incident.occurrence.summary",ResourceAction.ActionKind.READ,false,"INCIDENT_OCCURRENCE_SUMMARY");return queryService.occurrenceSummary(incidentId,limit);}
    @PostMapping("/{incidentId}/resolve") public Incident resolve(@PathVariable String incidentId,@RequestBody(required=false) LifecycleRequest r){authorize(incidentId,"api.incident.resolve",ResourceAction.ActionKind.UPDATE,true,"INCIDENT_RESOLVE");return lifecycleService.resolve(incidentId,r==null?null:r.reason());}
    @PostMapping("/{incidentId}/reopen") public Incident reopen(@PathVariable String incidentId,@RequestBody(required=false) LifecycleRequest r){authorize(incidentId,"api.incident.reopen",ResourceAction.ActionKind.UPDATE,true,"INCIDENT_REOPEN");return lifecycleService.reopen(incidentId,r==null?null:r.reason());}
    @PostMapping("/{incidentId}/suppress") public Incident suppress(@PathVariable String incidentId,@RequestBody(required=false) LifecycleRequest r){authorize(incidentId,"api.incident.suppress",ResourceAction.ActionKind.UPDATE,true,"INCIDENT_SUPPRESS");return lifecycleService.suppress(incidentId,r==null?null:r.reason());}
    @PostMapping("/lifecycle/auto-resolve") public LifecycleScanResult autoResolve(){var guard=scopedAccess.getIfAvailable();if(guard!=null)guard.requireTenantWide("api.incident.auto.resolve",ResourceType.INCIDENT,VisibilityLevel.STANDARD,"INCIDENT_AUTO_RESOLVE");return lifecycleService.autoResolveStaleIncidents();}
    private void authorize(String id,String permission,ResourceAction.ActionKind kind,boolean sideEffect,String purpose){var guard=scopedAccess.getIfAvailable();if(guard!=null)guard.authorize(ResourceType.INCIDENT,id,permission,kind,sideEffect,VisibilityLevel.STANDARD,purpose);}
    private static IncidentQuery query(String tenantId,String sourceSystem,String siteId,String plantId,String objectType,String objectId,String eventType,String errorCode,String severity,String status,int limit){IncidentQuery q=new IncidentQuery();q.setTenantId(tenantId);q.setSourceSystem(sourceSystem);q.setSiteId(siteId);q.setPlantId(plantId);q.setObjectType(objectType);q.setObjectId(objectId);q.setEventType(eventType);q.setErrorCode(errorCode);if(severity!=null&&!severity.isBlank())q.setSeverity(EventSeverity.parse(severity));if(status!=null&&!status.isBlank())q.setStatus(IncidentStatus.valueOf(status.trim().toUpperCase()));q.setLimit(limit);return q;}
    public record LifecycleRequest(String reason){}
}
