package com.opensocket.aievent.core.resourceaccess.core;

import com.opensocket.aievent.core.resourceaccess.contract.OwnershipDescriptor;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceRef;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Read-model and access-review persistence boundary. It never becomes authorization authority. */
public interface ResourceGovernanceRepository {
    ResourceGovernanceSummary summary(String tenantId, Instant now);
    List<GovernanceScopeGrantView> listGrants(String tenantId, String state, String search, GovernanceCursor cursor, int limit);
    long countGrants(String tenantId, String state, String search);
    List<GovernanceExplicitDenyView> listDenies(String tenantId, String state, String search, GovernanceCursor cursor, int limit);
    long countDenies(String tenantId, String state, String search);
    List<GovernanceOrphanView> listOrphans(String tenantId, String status, String search, GovernanceCursor cursor, int limit);
    long countOrphans(String tenantId, String status, String search);
    Optional<ResourceGovernanceOverview> findOverview(ResourceRef resourceRef);
    OrphanRepairImpactPreview previewOrphan(String tenantId, String repairId, OwnershipDescriptor proposed, Instant evaluatedAt);
    void recordOrphanEvent(String tenantId, String eventType, OrphanRepairImpactPreview preview, String actorId,
                           String reason, String correlationId, String idempotencyKey, Instant occurredAt);

    AccessReviewCampaign insertCampaign(String tenantId, CreateAccessReviewCampaignCommand command);
    Optional<AccessReviewCampaign> findCampaign(String tenantId, String campaignId);
    List<AccessReviewCampaign> listCampaigns(String tenantId, String status, GovernanceCursor cursor, int limit);
    long countCampaigns(String tenantId, String status);
    AccessReviewCampaign transitionCampaign(String tenantId, AccessReviewCampaign current, AccessReviewCampaignStatus target,
                                            AccessReviewCampaignMutationCommand command);
    AccessReviewCampaign activateCampaignAndSnapshot(String tenantId, AccessReviewCampaign current, AccessReviewCampaignMutationCommand command);
    void snapshotCampaignItems(String tenantId, AccessReviewCampaign campaign, Instant at);
    List<AccessReviewItem> listReviewItems(String tenantId, String campaignId, String status, GovernanceCursor cursor, int limit);
    long countReviewItems(String tenantId, String campaignId, String status);
    Optional<AccessReviewItem> findReviewItem(String tenantId, String campaignId, String itemId);
    AccessReviewItem decideReviewItem(String tenantId, AccessReviewItem current, AccessReviewItemDecisionCommand command);
}
