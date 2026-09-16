package com.opensocket.aievent.core.resourceaccess.api;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.resourceaccess.core.*;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** P4RA-H governance/read-review API. It never accepts tenant or actor identity from request bodies. */
@RestController
@RequestMapping(path="/api/resource-access/governance",produces=MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix="resource-access",name={"enabled","governance-api-enabled"},havingValue="true")
public class ResourceGovernanceController {
    private final ResourceGovernanceService governance;
    private final ResourceOrphanRepairService orphanRepairs;
    private final ResourceAccessApiContextPort context;
    public ResourceGovernanceController(ResourceGovernanceService governance,ResourceOrphanRepairService orphanRepairs,ResourceAccessApiContextPort context){this.governance=governance;this.orphanRepairs=orphanRepairs;this.context=context;}

    @GetMapping("/summary")
    public ResourceGovernanceSummary summary(){var c=context.current();return governance.summary(c.tenantId(),c.requestedAt());}

    @GetMapping("/grants")
    public GovernancePage<GovernanceScopeGrantView> grants(@RequestParam(defaultValue="") String state,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="") String cursor,@RequestParam(defaultValue="50") int limit){var c=context.current();return governance.grants(c.tenantId(),state,search,cursor,limit);}
    @GetMapping("/denies")
    public GovernancePage<GovernanceExplicitDenyView> denies(@RequestParam(defaultValue="") String state,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="") String cursor,@RequestParam(defaultValue="50") int limit){var c=context.current();return governance.denies(c.tenantId(),state,search,cursor,limit);}
    @GetMapping("/orphans")
    public GovernancePage<GovernanceOrphanView> orphans(@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="") String cursor,@RequestParam(defaultValue="50") int limit){var c=context.current();return governance.orphans(c.tenantId(),status,search,cursor,limit);}

    @GetMapping("/resources/{resourceType}/{resourceId}")
    public ResourceGovernanceOverview overview(@PathVariable ResourceType resourceType,@PathVariable String resourceId){var c=context.current();return governance.overview(new ResourceRef(c.tenantId(),resourceType,resourceId));}

    @PostMapping(path="/reviews",consumes=MediaType.APPLICATION_JSON_VALUE)
    public AccessReviewCampaign createReview(@RequestBody CreateReviewBody body,@RequestHeader("Idempotency-Key") String idem){var c=context.current();return governance.createCampaign(c.tenantId(),new CreateAccessReviewCampaignCommand(body.campaignId(),body.campaignName(),body.description(),body.resourceTypeFilter(),body.principalTypeFilter(),body.dueAt(),c.actorId(),c.correlationId(),idem,c.requestedAt()));}
    @GetMapping("/reviews")
    public GovernancePage<AccessReviewCampaign> reviews(@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="") String cursor,@RequestParam(defaultValue="50") int limit){var c=context.current();return governance.campaigns(c.tenantId(),status,cursor,limit);}
    @PostMapping("/reviews/{campaignId}/activate")
    public AccessReviewCampaign activate(@PathVariable String campaignId,@RequestHeader("If-Match") String version,@RequestHeader("Idempotency-Key") String idem,@RequestHeader("X-Audit-Reason") String reason){var c=context.current();return governance.activateCampaign(c.tenantId(),campaignMutation(campaignId,version,idem,reason,c));}
    @PostMapping("/reviews/{campaignId}/complete")
    public AccessReviewCampaign complete(@PathVariable String campaignId,@RequestHeader("If-Match") String version,@RequestHeader("Idempotency-Key") String idem,@RequestHeader("X-Audit-Reason") String reason){var c=context.current();return governance.completeCampaign(c.tenantId(),campaignMutation(campaignId,version,idem,reason,c));}
    @PostMapping("/reviews/{campaignId}/cancel")
    public AccessReviewCampaign cancel(@PathVariable String campaignId,@RequestHeader("If-Match") String version,@RequestHeader("Idempotency-Key") String idem,@RequestHeader("X-Audit-Reason") String reason){var c=context.current();return governance.cancelCampaign(c.tenantId(),campaignMutation(campaignId,version,idem,reason,c));}
    @GetMapping("/reviews/{campaignId}/items")
    public GovernancePage<AccessReviewItem> items(@PathVariable String campaignId,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="") String cursor,@RequestParam(defaultValue="100") int limit){var c=context.current();return governance.reviewItems(c.tenantId(),campaignId,status,cursor,limit);}
    @PostMapping(path="/reviews/{campaignId}/items/{itemId}/decision",consumes=MediaType.APPLICATION_JSON_VALUE)
    public AccessReviewItem decide(@PathVariable String campaignId,@PathVariable String itemId,@RequestBody ReviewDecisionBody body,@RequestHeader("If-Match") String version,@RequestHeader("Idempotency-Key") String idem){var c=context.current();return governance.decide(c.tenantId(),new AccessReviewItemDecisionCommand(campaignId,itemId,body.decision(),parseVersion(version),c.actorId(),body.reason(),c.correlationId(),idem,c.requestedAt()));}

    @PostMapping(path="/orphans/{repairId}/preview",consumes=MediaType.APPLICATION_JSON_VALUE)
    public OrphanRepairImpactPreview preview(@PathVariable String repairId,@RequestBody OrphanOwnerBody body,@RequestHeader("Idempotency-Key") String idem,@RequestHeader("X-Audit-Reason") String reason){var c=context.current();OwnershipDescriptor proposed=ownership(body);return governance.previewOrphan(c.tenantId(),repairId,proposed,c.requestedAt(),c.actorId(),reason,c.correlationId(),idem);}
    @PostMapping(path="/orphans/{repairId}/assign-owner",consumes=MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public OrphanRepairResult repair(@PathVariable String repairId,@RequestBody OrphanOwnerBody body,@RequestHeader("If-Match") String version,@RequestHeader("Idempotency-Key") String idem,@RequestHeader("X-Audit-Reason") String reason){
        var c=context.current();long expected=parseVersion(version);if(body.expectedResourceVersion()!=expected)throw new IllegalArgumentException("If-Match and expectedResourceVersion must match");OwnershipDescriptor proposed=ownership(body);
        OrphanRepairImpactPreview preview=governance.previewOrphan(c.tenantId(),repairId,proposed,c.requestedAt(),c.actorId(),reason,c.correlationId(),idem+":preview");
        ResourceRef ref=new ResourceRef(c.tenantId(),body.resourceType(),required(body.resourceId(),"resourceId"));
        OwnershipTransferCommand transfer=new OwnershipTransferCommand(ref,body.ownerDepartmentId(),body.ownerGroupId(),body.stewardUserId(),expected,reason,c.actorId(),c.correlationId(),idem,c.requestedAt());
        OrphanRepairResult result=orphanRepairs.repair(new OrphanRepairCommand(repairId,transfer,reason,c.requestedAt()));
        governance.recordOrphanAssigned(c.tenantId(),preview,c.actorId(),reason,c.correlationId(),idem+":assigned",c.requestedAt());
        return result;
    }

    private AccessReviewCampaignMutationCommand campaignMutation(String id,String version,String idem,String reason,ResourceAccessApiRequestContext c){return new AccessReviewCampaignMutationCommand(id,parseVersion(version),c.actorId(),reason,c.correlationId(),idem,c.requestedAt());}
    private static OwnershipDescriptor ownership(OrphanOwnerBody body){return new OwnershipDescriptor(normalize(body.ownerDepartmentId()),normalize(body.ownerGroupId()),normalize(body.stewardUserId()),"","","",body.expectedResourceVersion());}
    private static long parseVersion(String value){String normalized=value==null?"":value.trim().replace("\"","");if(normalized.startsWith("W/"))normalized=normalized.substring(2);try{return Long.parseLong(normalized);}catch(NumberFormatException ex){throw new IllegalArgumentException("If-Match must contain the numeric version");}}
    private static String required(String value,String field){if(value==null||value.isBlank())throw new IllegalArgumentException(field+" is required");return value.trim();}
    private static String normalize(String value){return value==null?"":value.trim();}

    public record CreateReviewBody(String campaignId,String campaignName,String description,String resourceTypeFilter,String principalTypeFilter,Instant dueAt){}
    public record ReviewDecisionBody(AccessReviewItemStatus decision,String reason){}
    public record OrphanOwnerBody(ResourceType resourceType,String resourceId,String ownerDepartmentId,String ownerGroupId,String stewardUserId,long expectedResourceVersion){public OrphanOwnerBody{if(resourceType==null)throw new IllegalArgumentException("resourceType is required");if(expectedResourceVersion<1)throw new IllegalArgumentException("expectedResourceVersion must be positive");}}
}
