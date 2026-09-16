package com.opensocket.aievent.core.resourceaccess.api;

import com.opensocket.aievent.core.resourceaccess.contract.*;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

/** P4RA-J operational API. Tenant/actor/correlation are always resolved from authenticated server context. */
@RestController
@RequestMapping(path="/api/resource-access/runtime",produces=MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix="resource-access",name={"enabled","runtime-scale-api-enabled","runtime-lease-enabled","scope-snapshot-enabled"},havingValue="true")
public class ResourceRuntimeScaleController {
    private final ResourceAuthorizationCachePort cache;
    private final RuntimeLateResultQuarantinePort quarantines;
    private final MaterializedScopeSnapshotPort snapshots;
    private final DepartmentRevisionCutoverPort cutovers;
    private final ResourceAccessApiContextPort context;

    public ResourceRuntimeScaleController(
            ResourceAuthorizationCachePort cache,
            RuntimeLateResultQuarantinePort quarantines,
            MaterializedScopeSnapshotPort snapshots,
            DepartmentRevisionCutoverPort cutovers,
            ResourceAccessApiContextPort context) {
        this.cache=cache; this.quarantines=quarantines; this.snapshots=snapshots; this.cutovers=cutovers; this.context=context;
    }

    @GetMapping("/cache/metrics")
    public ResourceAuthorizationCacheMetrics cacheMetrics(){return cache.metrics();}

    @GetMapping("/late-results")
    public List<RuntimeLateResultQuarantine> lateResults(@RequestParam(defaultValue="100") int limit){
        var current=context.current();return quarantines.findOpen(current.tenantId(),Math.max(1,Math.min(limit,1000)));
    }

    @PostMapping(path="/late-results/{quarantineId}/resolve",consumes=MediaType.APPLICATION_JSON_VALUE)
    public RuntimeLateResultQuarantine resolve(
            @PathVariable String quarantineId,
            @RequestHeader("If-Match") String version,
            @RequestBody ResolveLateResultBody body){
        var current=context.current();
        if(body==null||body.target()==null||body.target()==RuntimeLateResultQuarantineStatus.OPEN)
            throw new IllegalArgumentException("terminal target is required");
        return quarantines.resolve(current.tenantId(),quarantineId,parseVersion(version),body.target(),
                current.actorId(),required(body.reason(),"reason"),current.requestedAt());
    }

    @GetMapping("/scope-cutovers/current")
    public DepartmentRevisionCutover currentCutover(){
        var current=context.current();return cutovers.current(current.tenantId()).orElse(null);
    }

    @PostMapping(path="/scope-cutovers/prepare",consumes=MediaType.APPLICATION_JSON_VALUE)
    public DepartmentRevisionCutover prepare(@RequestBody PrepareCutoverBody body){
        var current=context.current();
        if(body==null||body.departmentRevision()<0)throw new IllegalArgumentException("departmentRevision is required");
        long prepared=snapshots.countPrepared(current.tenantId(),body.departmentRevision());
        if(prepared<1)throw new IllegalStateException("SCOPE_SNAPSHOT_PREPARE_INCOMPLETE");
        return cutovers.prepare(current.tenantId(),body.departmentRevision(),prepared,current.actorId(),
                current.correlationId(),current.requestedAt());
    }

    @PostMapping("/scope-cutovers/{cutoverId}/activate")
    public DepartmentRevisionCutover activate(@PathVariable String cutoverId,@RequestHeader("If-Match") String version){
        var current=context.current();return cutovers.activate(current.tenantId(),cutoverId,parseVersion(version),
                current.actorId(),current.correlationId(),current.requestedAt());
    }

    private static long parseVersion(String value){String normalized=value==null?"":value.trim().replace("\"","");if(normalized.startsWith("W/"))normalized=normalized.substring(2);try{return Long.parseLong(normalized);}catch(NumberFormatException ex){throw new IllegalArgumentException("If-Match must contain a numeric version");}}
    private static String required(String value,String field){if(value==null||value.isBlank())throw new IllegalArgumentException(field+" is required");return value.trim();}
    public record ResolveLateResultBody(RuntimeLateResultQuarantineStatus target,String reason){}
    public record PrepareCutoverBody(long departmentRevision){}
}
