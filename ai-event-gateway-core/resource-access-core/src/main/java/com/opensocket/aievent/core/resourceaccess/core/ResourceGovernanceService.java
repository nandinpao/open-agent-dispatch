package com.opensocket.aievent.core.resourceaccess.core;

import com.opensocket.aievent.core.resourceaccess.contract.OwnershipDescriptor;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceRef;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/** Governance application service. Review evidence does not silently mutate policy authority. */
public final class ResourceGovernanceService {
    private final ResourceGovernanceRepository repository;
    public ResourceGovernanceService(ResourceGovernanceRepository repository){this.repository=Objects.requireNonNull(repository);}
    public ResourceGovernanceSummary summary(String tenantId,Instant now){return repository.summary(required(tenantId,"tenantId"),Objects.requireNonNull(now));}
    public GovernancePage<GovernanceScopeGrantView> grants(String tenantId,String state,String search,String cursor,int limit){int n=cap(limit);var decoded=decode(cursor);var rows=repository.listGrants(required(tenantId,"tenantId"),normalize(state),normalize(search),decoded,n+1);return page(rows,n,repository.countGrants(tenantId,normalize(state),normalize(search)),v->new GovernanceCursor(v.updatedAt(),v.grantId()));}
    public GovernancePage<GovernanceExplicitDenyView> denies(String tenantId,String state,String search,String cursor,int limit){int n=cap(limit);var rows=repository.listDenies(required(tenantId,"tenantId"),normalize(state),normalize(search),decode(cursor),n+1);return page(rows,n,repository.countDenies(tenantId,normalize(state),normalize(search)),v->new GovernanceCursor(v.updatedAt(),v.denyId()));}
    public GovernancePage<GovernanceOrphanView> orphans(String tenantId,String state,String search,String cursor,int limit){int n=cap(limit);var rows=repository.listOrphans(required(tenantId,"tenantId"),normalize(state),normalize(search),decode(cursor),n+1);return page(rows,n,repository.countOrphans(tenantId,normalize(state),normalize(search)),v->new GovernanceCursor(v.updatedAt(),v.repairId()));}
    public ResourceGovernanceOverview overview(ResourceRef ref){return repository.findOverview(Objects.requireNonNull(ref)).orElseThrow(()->new IllegalStateException("RESOURCE_GOVERNANCE_OVERVIEW_NOT_FOUND"));}
    public OrphanRepairImpactPreview previewOrphan(String tenantId,String repairId,OwnershipDescriptor proposed,Instant at,String actorId,String reason,String correlationId,String idempotencyKey){
        OrphanRepairImpactPreview preview=repository.previewOrphan(required(tenantId,"tenantId"),required(repairId,"repairId"),Objects.requireNonNull(proposed),Objects.requireNonNull(at));
        repository.recordOrphanEvent(tenantId,"IMPACT_PREVIEWED",preview,required(actorId,"actorId"),required(reason,"reason"),required(correlationId,"correlationId"),required(idempotencyKey,"idempotencyKey"),at);
        return preview;
    }
    public void recordOrphanAssigned(String tenantId,OrphanRepairImpactPreview preview,String actorId,String reason,String correlationId,String idempotencyKey,Instant at){repository.recordOrphanEvent(required(tenantId,"tenantId"),"OWNER_ASSIGNED",Objects.requireNonNull(preview),required(actorId,"actorId"),required(reason,"reason"),required(correlationId,"correlationId"),required(idempotencyKey,"idempotencyKey"),Objects.requireNonNull(at));}
    public AccessReviewCampaign createCampaign(String tenantId,CreateAccessReviewCampaignCommand command){return repository.insertCampaign(required(tenantId,"tenantId"),Objects.requireNonNull(command));}
    public AccessReviewCampaign activateCampaign(String tenantId,AccessReviewCampaignMutationCommand command){AccessReviewCampaign current=campaign(tenantId,command.campaignId());if(current.status()!=AccessReviewCampaignStatus.DRAFT)throw new IllegalStateException("ACCESS_REVIEW_CAMPAIGN_NOT_DRAFT");return repository.activateCampaignAndSnapshot(tenantId,current,command);}
    public AccessReviewCampaign completeCampaign(String tenantId,AccessReviewCampaignMutationCommand command){AccessReviewCampaign current=campaign(tenantId,command.campaignId());if(current.status()!=AccessReviewCampaignStatus.ACTIVE)throw new IllegalStateException("ACCESS_REVIEW_CAMPAIGN_NOT_ACTIVE");if(current.openItems()>0)throw new IllegalStateException("ACCESS_REVIEW_CAMPAIGN_HAS_OPEN_ITEMS");return repository.transitionCampaign(tenantId,current,AccessReviewCampaignStatus.COMPLETED,command);}
    public AccessReviewCampaign cancelCampaign(String tenantId,AccessReviewCampaignMutationCommand command){AccessReviewCampaign current=campaign(tenantId,command.campaignId());if(current.status()==AccessReviewCampaignStatus.COMPLETED||current.status()==AccessReviewCampaignStatus.CANCELLED)throw new IllegalStateException("ACCESS_REVIEW_CAMPAIGN_TERMINAL");return repository.transitionCampaign(tenantId,current,AccessReviewCampaignStatus.CANCELLED,command);}
    public GovernancePage<AccessReviewCampaign> campaigns(String tenantId,String status,String cursor,int limit){int n=cap(limit);var rows=repository.listCampaigns(required(tenantId,"tenantId"),normalize(status),decode(cursor),n+1);return page(rows,n,repository.countCampaigns(tenantId,normalize(status)),v->new GovernanceCursor(v.updatedAt(),v.campaignId()));}
    public GovernancePage<AccessReviewItem> reviewItems(String tenantId,String campaignId,String status,String cursor,int limit){int n=cap(limit);var rows=repository.listReviewItems(required(tenantId,"tenantId"),required(campaignId,"campaignId"),normalize(status),decode(cursor),n+1);return page(rows,n,repository.countReviewItems(tenantId,campaignId,normalize(status)),v->new GovernanceCursor(v.updatedAt(),v.itemId()));}
    public AccessReviewItem decide(String tenantId,AccessReviewItemDecisionCommand command){AccessReviewItem current=repository.findReviewItem(required(tenantId,"tenantId"),command.campaignId(),command.itemId()).orElseThrow(()->new IllegalStateException("ACCESS_REVIEW_ITEM_NOT_FOUND"));if(current.status()!=AccessReviewItemStatus.OPEN)throw new IllegalStateException("ACCESS_REVIEW_ITEM_ALREADY_DECIDED");if("USER".equals(current.principalType())&&command.actorId().equals(current.principalId()))throw new IllegalStateException("ACCESS_REVIEW_SELF_REVIEW_PROHIBITED");return repository.decideReviewItem(tenantId,current,command);}
    private AccessReviewCampaign campaign(String tenantId,String id){return repository.findCampaign(required(tenantId,"tenantId"),required(id,"campaignId")).orElseThrow(()->new IllegalStateException("ACCESS_REVIEW_CAMPAIGN_NOT_FOUND"));}
    private static int cap(int limit){return Math.max(1,Math.min(limit<=0?50:limit,200));}
    private static String normalize(String value){return value==null?"":value.trim();}
    private static String required(String value,String field){if(value==null||value.isBlank())throw new IllegalArgumentException(field+" is required");return value.trim();}
    private interface CursorOf<T>{GovernanceCursor cursor(T value);}
    private static <T> GovernancePage<T> page(List<T> values,int limit,long total,CursorOf<T> cursorOf){boolean more=values.size()>limit;List<T> items=more?List.copyOf(values.subList(0,limit)):List.copyOf(values);String next=more?encode(cursorOf.cursor(items.get(items.size()-1))):"";return new GovernancePage<>(items,next,total);}
    private static String encode(GovernanceCursor cursor){String raw=cursor.sortTime()+"|"+cursor.sortId();return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));}
    private static GovernanceCursor decode(String value){if(value==null||value.isBlank())return GovernanceCursor.first();try{String raw=new String(Base64.getUrlDecoder().decode(value),StandardCharsets.UTF_8);int split=raw.indexOf('|');return new GovernanceCursor(Instant.parse(raw.substring(0,split)),raw.substring(split+1));}catch(RuntimeException ex){throw new IllegalArgumentException("invalid governance cursor",ex);}}
}
