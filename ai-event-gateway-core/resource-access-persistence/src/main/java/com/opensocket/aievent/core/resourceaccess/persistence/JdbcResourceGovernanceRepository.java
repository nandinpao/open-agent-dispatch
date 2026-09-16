package com.opensocket.aievent.core.resourceaccess.persistence;

import com.opensocket.aievent.core.resourceaccess.contract.OwnershipDescriptor;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceRef;
import com.opensocket.aievent.core.resourceaccess.core.*;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** P4RA-H governance read model and review evidence adapter. Authorization decisions remain authoritative elsewhere. */
@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix="resource-access",name={"enabled","governance-api-enabled"},havingValue="true")
public class JdbcResourceGovernanceRepository implements ResourceGovernanceRepository {
    private final JdbcTemplate jdbc;
    public JdbcResourceGovernanceRepository(JdbcTemplate jdbc){this.jdbc=Objects.requireNonNull(jdbc);}

    @Override public ResourceGovernanceSummary summary(String tenantId,Instant now){
        Instant soon=now.plus(30, ChronoUnit.DAYS), dayAgo=now.minus(24,ChronoUnit.HOURS);
        return new ResourceGovernanceSummary(
                count("select count(*) from resource_scope_grants where tenant_id=? and grant_state='ACTIVE' and valid_from<=? and (valid_to is null or valid_to>?)",tenantId,ts(now),ts(now)),
                count("select count(*) from resource_scope_grants where tenant_id=? and grant_state='ACTIVE' and valid_to is not null and valid_to>? and valid_to<=?",tenantId,ts(now),ts(soon)),
                count("select count(*) from resource_scope_denies where tenant_id=? and deny_state='ACTIVE' and valid_from<=? and (valid_to is null or valid_to>?)",tenantId,ts(now),ts(now)),
                count("select count(*) from resource_orphan_repairs where tenant_id=? and repair_status in ('OPEN','ASSIGNED')",tenantId),
                count("select count(*) from resource_access_review_items where tenant_id=? and review_status='OPEN' and due_at<?",tenantId,ts(now)),
                count("select count(*) from resource_access_review_campaigns where tenant_id=? and campaign_status='ACTIVE'",tenantId),
                count("select count(*) from resource_shadow_decision_comparisons where tenant_id=? and compared_at>=? and comparison_status not in ('MATCH','LEGACY_NOT_AVAILABLE')",tenantId,ts(dayAgo)),
                count("select count(*) from resource_descriptors where tenant_id=? and projection_status in ('STALE','MISSING','ERROR','ORPHANED')",tenantId),now);
    }

    @Override public List<GovernanceScopeGrantView> listGrants(String tenantId,String state,String search,GovernanceCursor cursor,int limit){
        String q=like(search);return jdbc.query("""
            select scope_grant_id,principal_type,principal_id,permission_code,resource_type,scope_type,scope_ref_id,
                   visibility_level,grant_state,grant_source,created_by,approved_by,valid_from,valid_to,version,updated_at
              from resource_scope_grants
             where tenant_id=? and (?='' or grant_state=?)
               and (?='' or lower(scope_grant_id||' '||principal_id||' '||permission_code||' '||resource_type||' '||coalesce(scope_ref_id,'')) like ?)
               and (updated_at<? or (updated_at=? and (?='' or scope_grant_id<?)))
             order by updated_at desc,scope_grant_id desc limit ?
            """,(rs,row)->grant(rs),tenantId,state,state,search,q,ts(cursor.sortTime()),ts(cursor.sortTime()),cursor.sortId(),cursor.sortId(),limit);}
    @Override public long countGrants(String tenantId,String state,String search){return count("""
            select count(*) from resource_scope_grants where tenant_id=? and (?='' or grant_state=?)
             and (?='' or lower(scope_grant_id||' '||principal_id||' '||permission_code||' '||resource_type||' '||coalesce(scope_ref_id,'')) like ?)
            """,tenantId,state,state,search,like(search));}

    @Override public List<GovernanceExplicitDenyView> listDenies(String tenantId,String state,String search,GovernanceCursor cursor,int limit){String q=like(search);return jdbc.query("""
            select scope_deny_id,principal_type,principal_id,permission_code,resource_type,scope_type,scope_ref_id,severity,
                   deny_state,deny_reason,created_by,approved_by,valid_from,valid_to,version,updated_at
              from resource_scope_denies
             where tenant_id=? and (?='' or deny_state=?)
               and (?='' or lower(scope_deny_id||' '||principal_id||' '||coalesce(permission_code,'')||' '||resource_type||' '||coalesce(scope_ref_id,'')) like ?)
               and (updated_at<? or (updated_at=? and (?='' or scope_deny_id<?)))
             order by updated_at desc,scope_deny_id desc limit ?
            """,(rs,row)->deny(rs),tenantId,state,state,search,q,ts(cursor.sortTime()),ts(cursor.sortTime()),cursor.sortId(),cursor.sortId(),limit);}
    @Override public long countDenies(String tenantId,String state,String search){return count("""
            select count(*) from resource_scope_denies where tenant_id=? and (?='' or deny_state=?)
             and (?='' or lower(scope_deny_id||' '||principal_id||' '||coalesce(permission_code,'')||' '||resource_type||' '||coalesce(scope_ref_id,'')) like ?)
            """,tenantId,state,state,search,like(search));}

    @Override public List<GovernanceOrphanView> listOrphans(String tenantId,String status,String search,GovernanceCursor cursor,int limit){String q=like(search);return jdbc.query("""
            select o.repair_id,o.resource_type,o.resource_id,d.resource_key,o.repair_status,o.reason_code,o.assigned_to,
                   o.proposed_owner_department_id,o.proposed_owner_group_id,o.proposed_steward_user_id,
                   o.resource_version,o.version,o.detected_at,o.updated_at
              from resource_orphan_repairs o join resource_descriptors d
                on d.tenant_id=o.tenant_id and d.resource_type=o.resource_type and d.resource_id=o.resource_id
             where o.tenant_id=? and (?='' or o.repair_status=?)
               and (?='' or lower(o.repair_id||' '||o.resource_type||' '||o.resource_id||' '||coalesce(d.resource_key,'')) like ?)
               and (o.updated_at<? or (o.updated_at=? and (?='' or o.repair_id<?)))
             order by o.updated_at desc,o.repair_id desc limit ?
            """,(rs,row)->orphan(rs),tenantId,status,status,search,q,ts(cursor.sortTime()),ts(cursor.sortTime()),cursor.sortId(),cursor.sortId(),limit);}
    @Override public long countOrphans(String tenantId,String status,String search){return count("""
            select count(*) from resource_orphan_repairs o join resource_descriptors d
              on d.tenant_id=o.tenant_id and d.resource_type=o.resource_type and d.resource_id=o.resource_id
             where o.tenant_id=? and (?='' or o.repair_status=?)
               and (?='' or lower(o.repair_id||' '||o.resource_type||' '||o.resource_id||' '||coalesce(d.resource_key,'')) like ?)
            """,tenantId,status,status,search,like(search));}

    @Override public Optional<ResourceGovernanceOverview> findOverview(ResourceRef ref){
        List<ResourceGovernanceOverview> values=jdbc.query("""
            select d.*,
             (select count(*) from resource_scope_grants g where g.tenant_id=d.tenant_id and g.resource_type=d.resource_type and g.grant_state='ACTIVE') active_grants,
             (select count(*) from resource_scope_denies x where x.tenant_id=d.tenant_id and x.resource_type=d.resource_type and x.deny_state='ACTIVE') active_denies,
             coalesce((select case when count(*) filter(where i.review_status='OPEN')>0 then 'OPEN' when count(*)>0 then 'REVIEWED' else 'NOT_SCHEDULED' end
               from resource_access_review_items i where i.tenant_id=d.tenant_id and i.resource_type=d.resource_type and (i.scope_ref_id=d.resource_id or i.source_id=d.resource_id)),'NOT_SCHEDULED') review_status
              from resource_descriptors d where d.tenant_id=? and d.resource_type=? and d.resource_id=?
            """,(rs,row)->overview(rs,participants(ref)),ref.tenantId(),ref.resourceType().name(),ref.resourceId());
        return values.stream().findFirst();
    }

    @Override public OrphanRepairImpactPreview previewOrphan(String tenantId,String repairId,OwnershipDescriptor proposed,Instant at){
        List<OrphanRepairImpactPreview> values=jdbc.query("""
            select o.repair_id,o.resource_type,o.resource_id,d.resource_key,o.resource_version,d.security_state,
             (select count(*) from resource_participants p where p.tenant_id=o.tenant_id and p.resource_type=o.resource_type and p.resource_id=o.resource_id and p.participant_status='ACTIVE') participant_count,
             (select count(*) from resource_scope_grants g where g.tenant_id=o.tenant_id and g.resource_type=o.resource_type and g.grant_state='ACTIVE') grant_count,
             (select count(*) from resource_scope_denies x where x.tenant_id=o.tenant_id and x.resource_type=o.resource_type and x.deny_state='ACTIVE') deny_count,
             (select count(*) from resource_descriptors c where c.tenant_id=o.tenant_id and c.parent_resource_type=o.resource_type and c.parent_resource_id=o.resource_id) child_count
              from resource_orphan_repairs o join resource_descriptors d on d.tenant_id=o.tenant_id and d.resource_type=o.resource_type and d.resource_id=o.resource_id
             where o.tenant_id=? and o.repair_id=? and o.repair_status in ('OPEN','ASSIGNED')
            """,(rs,row)->{
                long participants=rs.getLong("participant_count"),grants=rs.getLong("grant_count"),denies=rs.getLong("deny_count"),children=rs.getLong("child_count");
                boolean high="RESTRICTED".equals(rs.getString("security_state"))||"QUARANTINED".equals(rs.getString("security_state"))||denies>0||children>20;
                String warning=high?"High-risk ownership repair: review active denies, child resources, and participant visibility before applying.":"Ownership repair affects the canonical Domain owner and refreshes the Resource Access projection.";
                return new OrphanRepairImpactPreview(rs.getString("repair_id"),rs.getString("resource_type"),rs.getString("resource_id"),n(rs.getString("resource_key")),rs.getLong("resource_version"),rs.getString("security_state"),proposed.ownerDepartmentId(),proposed.ownerGroupId(),proposed.stewardUserId(),participants,grants,denies,children,high,warning,at);
            },tenantId,repairId);
        return values.stream().findFirst().orElseThrow(()->new IllegalStateException("RESOURCE_ORPHAN_REPAIR_NOT_OPEN"));
    }

    @Override @Transactional public void recordOrphanEvent(String tenantId,String eventType,OrphanRepairImpactPreview preview,String actorId,String reason,String correlationId,String idempotencyKey,Instant occurredAt){
        String impact="{\"participantCount\":"+preview.participantCount()+",\"activeGrantCount\":"+preview.activeGrantCount()+",\"activeDenyCount\":"+preview.activeDenyCount()+",\"childResourceCount\":"+preview.childResourceCount()+",\"highRisk\":"+preview.highRisk()+"}";
        jdbc.update("""
          insert into resource_orphan_repair_events(tenant_id,event_id,repair_id,resource_type,resource_id,event_type,actor_id,reason,
           proposed_owner_department_id,proposed_owner_group_id,proposed_steward_user_id,expected_resource_version,impact_snapshot,correlation_id,idempotency_key,occurred_at)
          values(?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?) on conflict(tenant_id,idempotency_key) do nothing
          """,tenantId,eventId("orphan",idempotencyKey),preview.repairId(),preview.resourceType(),preview.resourceId(),eventType,actorId,reason,
          blank(preview.proposedOwnerDepartmentId()),blank(preview.proposedOwnerGroupId()),blank(preview.proposedStewardUserId()),preview.expectedResourceVersion(),impact,correlationId,idempotencyKey,ts(occurredAt));
    }

    @Override @Transactional public AccessReviewCampaign insertCampaign(String tenantId,CreateAccessReviewCampaignCommand command){
        AccessReviewCampaign existing=findCampaignByIdempotency(tenantId,command.idempotencyKey()).orElse(null);if(existing!=null)return existing;
        jdbc.update("""
          insert into resource_access_review_campaigns(tenant_id,campaign_id,campaign_name,description,resource_type_filter,principal_type_filter,campaign_status,created_by,due_at,idempotency_key,version,created_at,updated_at)
          values(?,?,?,?,?,?,'DRAFT',?,?,?,1,?,?)
          """,tenantId,command.campaignId(),command.campaignName(),command.description(),blank(command.resourceTypeFilter()),blank(command.principalTypeFilter()),command.actorId(),ts(command.dueAt()),command.idempotencyKey(),ts(command.requestedAt()),ts(command.requestedAt()));
        auditCampaign(tenantId,command.campaignId(),null,"DRAFT",command.actorId(),"Campaign created",command.correlationId(),command.idempotencyKey(),1,command.requestedAt());
        return findCampaign(tenantId,command.campaignId()).orElseThrow();
    }
    @Override public Optional<AccessReviewCampaign> findCampaign(String tenantId,String campaignId){return jdbc.query("select * from resource_access_review_campaigns where tenant_id=? and campaign_id=?",(rs,row)->campaign(rs),tenantId,campaignId).stream().findFirst();}
    private Optional<AccessReviewCampaign> findCampaignByIdempotency(String tenantId,String key){return jdbc.query("select * from resource_access_review_campaigns where tenant_id=? and idempotency_key=?",(rs,row)->campaign(rs),tenantId,key).stream().findFirst();}
    @Override public List<AccessReviewCampaign> listCampaigns(String tenantId,String status,GovernanceCursor cursor,int limit){return jdbc.query("""
        select * from resource_access_review_campaigns where tenant_id=? and (?='' or campaign_status=?)
         and (updated_at<? or (updated_at=? and (?='' or campaign_id<?))) order by updated_at desc,campaign_id desc limit ?
        """,(rs,row)->campaign(rs),tenantId,status,status,ts(cursor.sortTime()),ts(cursor.sortTime()),cursor.sortId(),cursor.sortId(),limit);}
    @Override public long countCampaigns(String tenantId,String status){return count("select count(*) from resource_access_review_campaigns where tenant_id=? and (?='' or campaign_status=?)",tenantId,status,status);}

    @Override @Transactional public AccessReviewCampaign transitionCampaign(String tenantId,AccessReviewCampaign current,AccessReviewCampaignStatus target,AccessReviewCampaignMutationCommand command){
        String activated=target==AccessReviewCampaignStatus.ACTIVE?command.actorId():current.activatedBy();
        String completed=target==AccessReviewCampaignStatus.COMPLETED?command.actorId():current.completedBy();
        int updated=jdbc.update("""
          update resource_access_review_campaigns set campaign_status=?,activated_by=?,completed_by=?,version=version+1,updated_at=?
           where tenant_id=? and campaign_id=? and version=?
          """,target.name(),blank(activated),blank(completed),ts(command.requestedAt()),tenantId,current.campaignId(),command.expectedVersion());
        if(updated!=1)throw new IllegalStateException("ACCESS_REVIEW_CAMPAIGN_VERSION_CONFLICT");
        auditCampaign(tenantId,current.campaignId(),current.status().name(),target.name(),command.actorId(),command.reason(),command.correlationId(),command.idempotencyKey(),current.version()+1,command.requestedAt());
        return findCampaign(tenantId,current.campaignId()).orElseThrow();
    }

    @Override @Transactional public AccessReviewCampaign activateCampaignAndSnapshot(String tenantId,AccessReviewCampaign current,AccessReviewCampaignMutationCommand command){
        AccessReviewCampaign active=transitionCampaign(tenantId,current,AccessReviewCampaignStatus.ACTIVE,command);
        snapshotCampaignItems(tenantId,active,command.requestedAt());
        return findCampaign(tenantId,active.campaignId()).orElseThrow();
    }

    @Override @Transactional public void snapshotCampaignItems(String tenantId,AccessReviewCampaign campaign,Instant at){
        String resourceFilter=campaign.resourceTypeFilter(),principalFilter=campaign.principalTypeFilter();
        jdbc.update("""
          insert into resource_access_review_items(tenant_id,item_id,campaign_id,source_type,source_id,principal_type,principal_id,permission_code,resource_type,scope_type,scope_ref_id,risk_level,review_status,action_required,version,due_at,created_at,updated_at)
          select tenant_id,'ari-'||md5(?||':'||scope_grant_id),?,'SCOPE_GRANT',scope_grant_id,principal_type,principal_id,permission_code,resource_type,scope_type,scope_ref_id,
                 case when visibility_level in ('FULL','SECRET_METADATA') or grant_source='BREAK_GLASS' then 'HIGH' else 'MODERATE' end,'OPEN',false,1,?,?,?
            from resource_scope_grants where tenant_id=? and grant_state='ACTIVE' and (?='' or resource_type=?) and (?='' or principal_type=?)
          on conflict(tenant_id,campaign_id,source_type,source_id) do nothing
          """,campaign.campaignId(),campaign.campaignId(),ts(campaign.dueAt()),ts(at),ts(at),tenantId,resourceFilter,resourceFilter,principalFilter,principalFilter);
        jdbc.update("""
          insert into resource_access_review_items(tenant_id,item_id,campaign_id,source_type,source_id,principal_type,principal_id,permission_code,resource_type,scope_type,scope_ref_id,risk_level,review_status,action_required,version,due_at,created_at,updated_at)
          select tenant_id,'ari-'||md5(?||':'||scope_deny_id),?,'EXPLICIT_DENY',scope_deny_id,principal_type,principal_id,coalesce(permission_code,''),resource_type,scope_type,scope_ref_id,
                 case when severity in ('CRITICAL','HIGH') then 'HIGH' else 'MODERATE' end,'OPEN',false,1,?,?,?
            from resource_scope_denies where tenant_id=? and deny_state='ACTIVE' and (?='' or resource_type=?) and (?='' or principal_type=?)
          on conflict(tenant_id,campaign_id,source_type,source_id) do nothing
          """,campaign.campaignId(),campaign.campaignId(),ts(campaign.dueAt()),ts(at),ts(at),tenantId,resourceFilter,resourceFilter,principalFilter,principalFilter);
        jdbc.update("""
          insert into resource_access_review_items(tenant_id,item_id,campaign_id,source_type,source_id,principal_type,principal_id,permission_code,resource_type,scope_type,scope_ref_id,risk_level,review_status,action_required,version,due_at,created_at,updated_at)
          select tenant_id,'ari-'||md5(?||':'||repair_id),?,'ORPHAN_RESOURCE',repair_id,'','', 'resource.ownership.transfer',resource_type,'RESOURCE',resource_id,'HIGH','OPEN',true,1,?,?,?
            from resource_orphan_repairs where tenant_id=? and repair_status in ('OPEN','ASSIGNED') and (?='' or resource_type=?) and ?=''
          on conflict(tenant_id,campaign_id,source_type,source_id) do nothing
          """,campaign.campaignId(),campaign.campaignId(),ts(campaign.dueAt()),ts(at),ts(at),tenantId,resourceFilter,resourceFilter,principalFilter);
        refreshCampaignCounts(tenantId,campaign.campaignId(),at);
    }

    @Override public List<AccessReviewItem> listReviewItems(String tenantId,String campaignId,String status,GovernanceCursor cursor,int limit){return jdbc.query("""
        select * from resource_access_review_items where tenant_id=? and campaign_id=? and (?='' or review_status=?)
         and (updated_at<? or (updated_at=? and (?='' or item_id<?))) order by updated_at desc,item_id desc limit ?
        """,(rs,row)->item(rs),tenantId,campaignId,status,status,ts(cursor.sortTime()),ts(cursor.sortTime()),cursor.sortId(),cursor.sortId(),limit);}
    @Override public long countReviewItems(String tenantId,String campaignId,String status){return count("select count(*) from resource_access_review_items where tenant_id=? and campaign_id=? and (?='' or review_status=?)",tenantId,campaignId,status,status);}
    @Override public Optional<AccessReviewItem> findReviewItem(String tenantId,String campaignId,String itemId){return jdbc.query("select * from resource_access_review_items where tenant_id=? and campaign_id=? and item_id=?",(rs,row)->item(rs),tenantId,campaignId,itemId).stream().findFirst();}

    @Override @Transactional public AccessReviewItem decideReviewItem(String tenantId,AccessReviewItem current,AccessReviewItemDecisionCommand command){
        boolean action=command.decision()==AccessReviewItemStatus.REDUCED||command.decision()==AccessReviewItemStatus.REVOKED||command.decision()==AccessReviewItemStatus.OWNER_MISSING||command.decision()==AccessReviewItemStatus.EXPIRED||command.decision()==AccessReviewItemStatus.ESCALATED;
        int updated=jdbc.update("""
          update resource_access_review_items set review_status=?,reviewer_id=?,decision_reason=?,action_required=?,reviewed_at=?,version=version+1,updated_at=?
           where tenant_id=? and campaign_id=? and item_id=? and version=? and review_status='OPEN'
          """,command.decision().name(),command.actorId(),command.reason(),action,ts(command.requestedAt()),ts(command.requestedAt()),tenantId,current.campaignId(),current.itemId(),command.expectedVersion());
        if(updated!=1)throw new IllegalStateException("ACCESS_REVIEW_ITEM_VERSION_CONFLICT");
        jdbc.update("""
          insert into resource_access_review_events(tenant_id,event_id,campaign_id,item_id,previous_status,resulting_status,actor_id,reason,action_required,correlation_id,idempotency_key,resulting_version,occurred_at)
          values(?,?,?,?,?,?,?,?,?,?,?,?,?)
          """,tenantId,eventId("review",command.idempotencyKey()),current.campaignId(),current.itemId(),current.status().name(),command.decision().name(),command.actorId(),command.reason(),action,command.correlationId(),command.idempotencyKey(),current.version()+1,ts(command.requestedAt()));
        refreshCampaignCounts(tenantId,current.campaignId(),command.requestedAt());
        return findReviewItem(tenantId,current.campaignId(),current.itemId()).orElseThrow();
    }

    private void refreshCampaignCounts(String tenantId,String campaignId,Instant at){jdbc.update("""
      update resource_access_review_campaigns c set total_items=(select count(*) from resource_access_review_items i where i.tenant_id=c.tenant_id and i.campaign_id=c.campaign_id),
       open_items=(select count(*) from resource_access_review_items i where i.tenant_id=c.tenant_id and i.campaign_id=c.campaign_id and i.review_status='OPEN'),updated_at=?
       where tenant_id=? and campaign_id=?
      """,ts(at),tenantId,campaignId);}
    private void auditCampaign(String tenantId,String campaignId,String previous,String resulting,String actor,String reason,String correlation,String idem,long version,Instant at){jdbc.update("""
      insert into resource_access_review_events(tenant_id,event_id,campaign_id,item_id,previous_status,resulting_status,actor_id,reason,action_required,correlation_id,idempotency_key,resulting_version,occurred_at)
      values(?,?,?,'',?,?,?,?,false,?,?,?,?)
      """,tenantId,eventId("campaign",idem),campaignId,previous,resulting,actor,reason,correlation,idem,version,ts(at));}

    private List<ResourceParticipantView> participants(ResourceRef ref){return jdbc.query("""
      select participant_id,participant_type,participant_ref_id,participant_role,visibility_level,allowed_permission_codes,participant_status,valid_from,valid_to
       from resource_participants where tenant_id=? and resource_type=? and resource_id=? order by participant_role,participant_ref_id
      """,(rs,row)->new ResourceParticipantView(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),array(rs,6),rs.getString(7),instant(rs,8),instantNullable(rs,9)),ref.tenantId(),ref.resourceType().name(),ref.resourceId());}

    private ResourceGovernanceOverview overview(ResultSet rs,List<ResourceParticipantView> participants)throws SQLException{return new ResourceGovernanceOverview(rs.getString("resource_type"),rs.getString("resource_id"),n(rs.getString("resource_key")),n(rs.getString("owner_department_id")),n(rs.getString("owner_group_id")),n(rs.getString("steward_user_id")),n(rs.getString("requester_department_id")),n(rs.getString("executor_department_id")),rs.getString("sensitivity_level"),rs.getString("maximum_visibility"),n(rs.getString("visibility_policy_id")),rs.getString("security_state"),rs.getString("projection_status"),rs.getLong("resource_version"),rs.getLong("participant_version"),rs.getLong("active_grants"),rs.getLong("active_denies"),rs.getString("review_status"),participants,instantNullable(rs,"source_resolved_at"),instantNullable(rs,"last_projected_at"),instantNullable(rs,"last_reconciled_at"));}
    private GovernanceScopeGrantView grant(ResultSet rs)throws SQLException{return new GovernanceScopeGrantView(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),n(rs.getString(7)),rs.getString(8),rs.getString(9),rs.getString(10),rs.getString(11),n(rs.getString(12)),instant(rs,13),instantNullable(rs,14),rs.getLong(15),instant(rs,16));}
    private GovernanceExplicitDenyView deny(ResultSet rs)throws SQLException{return new GovernanceExplicitDenyView(rs.getString(1),rs.getString(2),rs.getString(3),n(rs.getString(4)),rs.getString(5),rs.getString(6),n(rs.getString(7)),rs.getString(8),rs.getString(9),rs.getString(10),rs.getString(11),n(rs.getString(12)),instant(rs,13),instantNullable(rs,14),rs.getLong(15),instant(rs,16));}
    private GovernanceOrphanView orphan(ResultSet rs)throws SQLException{return new GovernanceOrphanView(rs.getString(1),rs.getString(2),rs.getString(3),n(rs.getString(4)),rs.getString(5),rs.getString(6),n(rs.getString(7)),n(rs.getString(8)),n(rs.getString(9)),n(rs.getString(10)),rs.getLong(11),rs.getLong(12),instant(rs,13),instant(rs,14));}
    private AccessReviewCampaign campaign(ResultSet rs)throws SQLException{return new AccessReviewCampaign(rs.getString("campaign_id"),rs.getString("campaign_name"),n(rs.getString("description")),n(rs.getString("resource_type_filter")),n(rs.getString("principal_type_filter")),AccessReviewCampaignStatus.valueOf(rs.getString("campaign_status")),rs.getString("created_by"),n(rs.getString("activated_by")),n(rs.getString("completed_by")),instant(rs,"due_at"),rs.getLong("total_items"),rs.getLong("open_items"),rs.getLong("version"),instant(rs,"created_at"),instant(rs,"updated_at"));}
    private AccessReviewItem item(ResultSet rs)throws SQLException{return new AccessReviewItem(rs.getString("item_id"),rs.getString("campaign_id"),AccessReviewSourceType.valueOf(rs.getString("source_type")),rs.getString("source_id"),n(rs.getString("principal_type")),n(rs.getString("principal_id")),n(rs.getString("permission_code")),n(rs.getString("resource_type")),n(rs.getString("scope_type")),n(rs.getString("scope_ref_id")),rs.getString("risk_level"),AccessReviewItemStatus.valueOf(rs.getString("review_status")),n(rs.getString("reviewer_id")),n(rs.getString("decision_reason")),rs.getBoolean("action_required"),rs.getLong("version"),instant(rs,"due_at"),instantNullable(rs,"reviewed_at"),instant(rs,"created_at"),instant(rs,"updated_at"));}

    private long count(String sql,Object...args){Long value=jdbc.queryForObject(sql,Long.class,args);return value==null?0:value;}
    private static Timestamp ts(Instant value){return Timestamp.from(value);}
    private static String like(String value){return "%"+(value==null?"":value.trim().toLowerCase(Locale.ROOT))+"%";}
    private static String n(String value){return value==null?"":value;}
    private static String blank(String value){return value==null||value.isBlank()?null:value.trim();}
    private static String eventId(String prefix,String value){return prefix+"-"+UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));}
    private static Instant instant(ResultSet rs,int column)throws SQLException{return rs.getTimestamp(column).toInstant();}
    private static Instant instant(ResultSet rs,String column)throws SQLException{return rs.getTimestamp(column).toInstant();}
    private static Instant instantNullable(ResultSet rs,int column)throws SQLException{Timestamp v=rs.getTimestamp(column);return v==null?null:v.toInstant();}
    private static Instant instantNullable(ResultSet rs,String column)throws SQLException{Timestamp v=rs.getTimestamp(column);return v==null?null:v.toInstant();}
    private static List<String> array(ResultSet rs,int column)throws SQLException{Array a=rs.getArray(column);if(a==null)return List.of();Object raw=a.getArray();return raw instanceof String[] s?List.of(s):List.of();}
}
