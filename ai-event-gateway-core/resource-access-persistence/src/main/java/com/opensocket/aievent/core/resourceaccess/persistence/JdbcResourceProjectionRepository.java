package com.opensocket.aievent.core.resourceaccess.persistence;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.resourceaccess.core.*;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL projection adapter. Domain tables remain the canonical ownership and participant authorities. */
@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix="resource-access",name="enabled",havingValue="true")
public class JdbcResourceProjectionRepository implements ResourceProjectionRepository {
    private final JdbcTemplate jdbc;
    public JdbcResourceProjectionRepository(JdbcTemplate jdbc){this.jdbc=Objects.requireNonNull(jdbc);}

    @Override
    public Optional<ResourceDescriptor> findDescriptor(ResourceRef ref){
        List<ResourceDescriptor> values=jdbc.query("""
            select * from resource_descriptors
             where tenant_id=? and resource_type=? and resource_id=?
            """,(rs,row)->descriptor(rs),ref.tenantId(),ref.resourceType().name(),ref.resourceId());
        return values.stream().findFirst();
    }

    @Override
    @Transactional
    public ResourceProjectionSyncResult synchronize(ResourceProjectionBatch batch){
        ResourceDescriptor value=batch.descriptor(); ResourceRef ref=value.resourceRef(); Optional<ResourceDescriptor> previous=findDescriptor(ref);
        previous.ifPresent(current->{
            if(current.resourceVersion()>value.resourceVersion())throw new IllegalStateException("RESOURCE_DESCRIPTOR_VERSION_REGRESSION");
            if(current.ownership().ownershipVersion()>value.ownership().ownershipVersion())throw new IllegalStateException("RESOURCE_OWNERSHIP_VERSION_REGRESSION");
        });
        boolean changed=previous.isEmpty()||!previous.get().descriptorHash().equals(value.descriptorHash())||previous.get().participantVersion()!=value.participantVersion();
        upsertDescriptor(value,batch.projectedAt());
        if(previous.isEmpty()||!previous.get().ownership().equals(value.ownership()))insertOwnershipSnapshot(value,batch.sourceEventId(),batch.projectedAt());
        replaceParticipants(batch.participants(),batch.projectedAt());
        ensureSecurityEpoch(value,batch.projectedAt());
        if(changed)insertOutbox(batch.event());
        if(value.securityState()==ResourceSecurityState.ORPHANED)openOrphan(value,batch.projectedAt()); else resolveProjectedOrphan(value,batch.projectedAt());
        return new ResourceProjectionSyncResult(ref,changed,value.securityState()==ResourceSecurityState.ORPHANED,
                previous.map(ResourceDescriptor::descriptorHash).orElse(""),value.descriptorHash(),value.resourceVersion(),value.participantVersion(),batch.projectedAt());
    }

    @Override
    public List<ResourceRef> findStaleDescriptors(Instant projectedBefore,int limit){
        int capped=Math.max(1,Math.min(limit,1000));
        return jdbc.query("""
            select tenant_id,resource_type,resource_id from resource_descriptors
             where coalesce(last_projected_at,created_at) < ? or projection_status in ('STALE','ERROR')
             order by coalesce(last_projected_at,created_at) asc limit ?
            """,(rs,row)->new ResourceRef(rs.getString(1),ResourceType.valueOf(rs.getString(2)),rs.getString(3)),Timestamp.from(projectedBefore),capped);
    }

    @Override
    @Transactional
    public void recordReconciliation(ResourceReconciliationRecord value){
        ResourceRef ref=value.resourceRef();
        jdbc.update("""
            insert into resource_descriptor_reconciliations(
              tenant_id,reconciliation_id,resource_type,resource_id,reconciliation_status,projected_hash,authority_hash,reason_code,auto_repaired,reconciled_at)
            values (?,?,?,?,?,?,?,?,?,?)
            on conflict(tenant_id,reconciliation_id) do nothing
            """,ref.tenantId(),value.reconciliationId(),ref.resourceType().name(),ref.resourceId(),value.status().name(),blankToNull(value.projectedHash()),blankToNull(value.authorityHash()),blankToNull(value.reasonCode()),value.autoRepaired(),Timestamp.from(value.reconciledAt()));
        jdbc.update("""
            update resource_descriptors set last_reconciled_at=?,projection_status=?,updated_at=?
             where tenant_id=? and resource_type=? and resource_id=?
            """,Timestamp.from(value.reconciledAt()),projectionStatus(value.status()),Timestamp.from(value.reconciledAt()),ref.tenantId(),ref.resourceType().name(),ref.resourceId());
    }

    @Override
    public Optional<OwnershipTransferResult> findOwnershipTransfer(ResourceRef ref,String idempotencyKey){
        List<OwnershipTransferResult> values=jdbc.query("""
            select * from resource_ownership_transfer_audits
             where tenant_id=? and idempotency_key=? and resource_type=? and resource_id=? limit 1
            """,(rs,row)->{
                OwnershipDescriptor previous=new OwnershipDescriptor(nullToBlank(rs.getString("previous_owner_department_id")),nullToBlank(rs.getString("previous_owner_group_id")),nullToBlank(rs.getString("previous_steward_user_id")),"","","",rs.getLong("expected_resource_version"));
                OwnershipDescriptor current=new OwnershipDescriptor(nullToBlank(rs.getString("new_owner_department_id")),nullToBlank(rs.getString("new_owner_group_id")),nullToBlank(rs.getString("new_steward_user_id")),"","","",rs.getLong("resulting_resource_version"));
                return new OwnershipTransferResult(ref,previous,current,rs.getLong("resulting_resource_version"),rs.getString("source_event_id"),rs.getString("actor_id"),rs.getString("reason"),instant(rs,"transferred_at",Instant.EPOCH));
            },ref.tenantId(),idempotencyKey,ref.resourceType().name(),ref.resourceId());
        return values.stream().findFirst();
    }

    @Override
    @Transactional
    public void recordOwnershipTransfer(OwnershipTransferCommand command,OwnershipTransferResult result){
        ResourceRef ref=command.resourceRef(); String id="rat-"+UUID.nameUUIDFromBytes((ref+":"+command.idempotencyKey()).getBytes(StandardCharsets.UTF_8));
        jdbc.update("""
            insert into resource_ownership_transfer_audits(
              tenant_id,transfer_id,resource_type,resource_id,expected_resource_version,resulting_resource_version,
              previous_owner_department_id,previous_owner_group_id,previous_steward_user_id,
              new_owner_department_id,new_owner_group_id,new_steward_user_id,actor_id,reason,correlation_id,idempotency_key,source_event_id,transferred_at)
            values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) on conflict(tenant_id,idempotency_key) do nothing
            """,ref.tenantId(),id,ref.resourceType().name(),ref.resourceId(),command.expectedResourceVersion(),result.resourceVersion(),
                blankToNull(result.previousOwnership().ownerDepartmentId()),blankToNull(result.previousOwnership().ownerGroupId()),blankToNull(result.previousOwnership().stewardUserId()),
                blankToNull(result.currentOwnership().ownerDepartmentId()),blankToNull(result.currentOwnership().ownerGroupId()),blankToNull(result.currentOwnership().stewardUserId()),
                command.actorId(),command.reason(),command.correlationId(),command.idempotencyKey(),result.sourceEventId(),Timestamp.from(result.transferredAt()));
    }

    @Override
    public Optional<ResourceOrphanRepairCase> findOpenOrphan(ResourceRef ref){
        List<ResourceOrphanRepairCase> values=jdbc.query("""
            select * from resource_orphan_repairs where tenant_id=? and resource_type=? and resource_id=?
              and repair_status in ('OPEN','ASSIGNED') order by detected_at asc limit 1
            """,(rs,row)->orphan(rs),ref.tenantId(),ref.resourceType().name(),ref.resourceId());
        return values.stream().findFirst();
    }

    @Override
    @Transactional
    public void markOrphanResolved(String repairId,ResourceRef ref,OwnershipDescriptor ownership,long resourceVersion,String actorId,Instant resolvedAt){
        int rows=jdbc.update("""
            update resource_orphan_repairs set repair_status='RESOLVED',proposed_owner_department_id=?,proposed_owner_group_id=?,
              proposed_steward_user_id=?,resolved_at=?,resolved_by=?,resource_version=?,version=version+1,updated_at=?
             where tenant_id=? and repair_id=? and resource_type=? and resource_id=? and repair_status in ('OPEN','ASSIGNED','RESOLVED')
            """,blankToNull(ownership.ownerDepartmentId()),blankToNull(ownership.ownerGroupId()),blankToNull(ownership.stewardUserId()),Timestamp.from(resolvedAt),actorId,resourceVersion,Timestamp.from(resolvedAt),ref.tenantId(),repairId,ref.resourceType().name(),ref.resourceId());
        if(rows==0)throw new IllegalStateException("RESOURCE_ORPHAN_REPAIR_VERSION_CONFLICT");
    }

    private void upsertDescriptor(ResourceDescriptor d,Instant projectedAt){
        ResourceRef r=d.resourceRef(); OwnershipDescriptor o=d.ownership(); ResourceRef parent=d.parentResource(); ResourceRef root=d.rootResource(); VisibilityDescriptor v=d.visibility();
        jdbc.update("""
            insert into resource_descriptors(
              tenant_id,resource_type,resource_id,resource_key,owner_department_id,owner_group_id,steward_user_id,custodian_service_id,
              requester_department_id,executor_department_id,parent_resource_type,parent_resource_id,root_resource_type,root_resource_id,
              sensitivity_level,maximum_visibility,visibility_policy_id,security_state,ownership_version,participant_version,resource_version,
              descriptor_authority,descriptor_hash,source_updated_at,visibility_policy_version,source_resolved_at,last_projected_at,projection_status,updated_at)
            values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            on conflict(tenant_id,resource_type,resource_id) do update set
              resource_key=excluded.resource_key,owner_department_id=excluded.owner_department_id,owner_group_id=excluded.owner_group_id,
              steward_user_id=excluded.steward_user_id,custodian_service_id=excluded.custodian_service_id,requester_department_id=excluded.requester_department_id,
              executor_department_id=excluded.executor_department_id,parent_resource_type=excluded.parent_resource_type,parent_resource_id=excluded.parent_resource_id,
              root_resource_type=excluded.root_resource_type,root_resource_id=excluded.root_resource_id,sensitivity_level=excluded.sensitivity_level,
              maximum_visibility=excluded.maximum_visibility,visibility_policy_id=excluded.visibility_policy_id,security_state=excluded.security_state,
              ownership_version=excluded.ownership_version,participant_version=excluded.participant_version,resource_version=excluded.resource_version,
              descriptor_authority=excluded.descriptor_authority,descriptor_hash=excluded.descriptor_hash,source_updated_at=excluded.source_updated_at,
              visibility_policy_version=excluded.visibility_policy_version,source_resolved_at=excluded.source_resolved_at,last_projected_at=excluded.last_projected_at,
              projection_status=excluded.projection_status,updated_at=excluded.updated_at
            """,r.tenantId(),r.resourceType().name(),r.resourceId(),blankToNull(d.resourceKey()),blankToNull(o.ownerDepartmentId()),blankToNull(o.ownerGroupId()),blankToNull(o.stewardUserId()),blankToNull(o.custodianServiceId()),
                blankToNull(o.requesterDepartmentId()),blankToNull(o.executorDepartmentId()),parent==null?null:parent.resourceType().name(),parent==null?null:parent.resourceId(),root==null?null:root.resourceType().name(),root==null?null:root.resourceId(),
                v.sensitivityLevel().name(),v.maximumVisibility().name(),blankToNull(v.visibilityPolicyId()),d.securityState().name(),o.ownershipVersion(),d.participantVersion(),d.resourceVersion(),d.descriptorAuthority().name(),d.descriptorHash(),Timestamp.from(d.resolvedAt()),v.policyVersion().policyRevision(),Timestamp.from(d.resolvedAt()),Timestamp.from(projectedAt),d.securityState()==ResourceSecurityState.ORPHANED?"ORPHANED":"CURRENT",Timestamp.from(projectedAt));
    }

    private void insertOwnershipSnapshot(ResourceDescriptor d,String sourceEventId,Instant at){
        ResourceRef r=d.resourceRef(); OwnershipDescriptor o=d.ownership(); String content=DescriptorFingerprint.sha256(o.toString());
        String id="ros-"+UUID.nameUUIDFromBytes((r+":"+o.ownershipVersion()+":"+content).getBytes(StandardCharsets.UTF_8));
        jdbc.update("""
            insert into resource_ownership_snapshots(tenant_id,snapshot_id,resource_type,resource_id,owner_department_id,owner_group_id,steward_user_id,custodian_service_id,requester_department_id,executor_department_id,ownership_version,source_authority,source_event_id,valid_from,content_hash)
            values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) on conflict(tenant_id,snapshot_id) do nothing
            """,r.tenantId(),id,r.resourceType().name(),r.resourceId(),blankToNull(o.ownerDepartmentId()),blankToNull(o.ownerGroupId()),blankToNull(o.stewardUserId()),blankToNull(o.custodianServiceId()),blankToNull(o.requesterDepartmentId()),blankToNull(o.executorDepartmentId()),o.ownershipVersion(),d.descriptorAuthority().name(),blankToNull(sourceEventId),Timestamp.from(at),content);
    }

    private void replaceParticipants(ParticipantProjectionSnapshot snapshot,Instant at){
        ResourceRef r=snapshot.resourceRef();
        jdbc.update("delete from resource_participants where tenant_id=? and resource_type=? and resource_id=?",r.tenantId(),r.resourceType().name(),r.resourceId());
        for(ResourceParticipantProjection p:snapshot.participants()){
            jdbc.update("""
                insert into resource_participants(tenant_id,participant_id,resource_type,resource_id,participant_type,participant_ref_id,participant_role,visibility_level,allowed_permission_codes,valid_from,valid_to,source_authority,source_version,participant_status,projected_at)
                values (?,?,?,?,?,?,?,?,string_to_array(?,','),?,?,?,?,?,?)
                """,r.tenantId(),p.participantId(),r.resourceType().name(),r.resourceId(),p.participantType().name(),p.participantRefId(),p.participantRole().name(),p.visibilityLevel().name(),String.join(",",p.allowedPermissionCodes()),Timestamp.from(p.validFrom()),p.validTo()==null?null:Timestamp.from(p.validTo()),p.sourceAuthority().name(),p.sourceVersion(),p.status().name(),Timestamp.from(at));
        }
    }

    private void insertOutbox(ResourceProjectionEvent e){ResourceRef r=e.resourceRef();jdbc.update("""
        insert into resource_projection_outbox_events(tenant_id,event_id,resource_type,resource_id,event_type,source_event_id,payload_hash,event_status,available_at)
        values (?,?,?,?,?,?,?,'PENDING',?) on conflict(tenant_id,event_id) do nothing
        """,r.tenantId(),e.eventId(),r.resourceType().name(),r.resourceId(),e.eventType(),blankToNull(e.sourceEventId()),e.payloadHash(),Timestamp.from(e.occurredAt()));}
    private void ensureSecurityEpoch(ResourceDescriptor d,Instant at){ResourceRef r=d.resourceRef();jdbc.update("""
        insert into resource_security_epochs(tenant_id,resource_type,resource_id,resource_security_epoch,updated_at,updated_by)
        values (?,?,?,0,?,'resource-projection') on conflict(tenant_id,resource_type,resource_id) do nothing
        """,r.tenantId(),r.resourceType().name(),r.resourceId(),Timestamp.from(at));}
    private void openOrphan(ResourceDescriptor d,Instant at){ResourceRef r=d.resourceRef();String id="ror-"+UUID.nameUUIDFromBytes((r+":OWNER_MISSING").getBytes(StandardCharsets.UTF_8));jdbc.update("""
        insert into resource_orphan_repairs(tenant_id,repair_id,resource_type,resource_id,repair_status,reason_code,detected_at,resource_version)
        values (?,?,?,?,'OPEN','RESOURCE_OWNER_MISSING',?,?)
        on conflict(tenant_id,resource_type,resource_id) where repair_status in ('OPEN','ASSIGNED') do update set updated_at=excluded.detected_at,resource_version=excluded.resource_version
        """,r.tenantId(),id,r.resourceType().name(),r.resourceId(),Timestamp.from(at),d.resourceVersion());}
    private void resolveProjectedOrphan(ResourceDescriptor d,Instant at){ResourceRef r=d.resourceRef();jdbc.update("""
        update resource_orphan_repairs set repair_status='RESOLVED',resolved_at=?,resolved_by='projection-reconciliation',resource_version=?,version=version+1,updated_at=?
        where tenant_id=? and resource_type=? and resource_id=? and repair_status in ('OPEN','ASSIGNED')
        """,Timestamp.from(at),d.resourceVersion(),Timestamp.from(at),r.tenantId(),r.resourceType().name(),r.resourceId());}

    private ResourceDescriptor descriptor(ResultSet rs)throws SQLException{
        ResourceRef ref=new ResourceRef(rs.getString("tenant_id"),ResourceType.valueOf(rs.getString("resource_type")),rs.getString("resource_id"));
        OwnershipDescriptor ownership=new OwnershipDescriptor(nullToBlank(rs.getString("owner_department_id")),nullToBlank(rs.getString("owner_group_id")),nullToBlank(rs.getString("steward_user_id")),nullToBlank(rs.getString("custodian_service_id")),nullToBlank(rs.getString("requester_department_id")),nullToBlank(rs.getString("executor_department_id")),rs.getLong("ownership_version"));
        ResourceRef parent=ref(rs,"parent_resource_type","parent_resource_id",ref.tenantId()); ResourceRef root=ref(rs,"root_resource_type","root_resource_id",ref.tenantId());
        long revision=hasColumn(rs,"visibility_policy_version")?rs.getLong("visibility_policy_version"):0;
        VisibilityDescriptor visibility=new VisibilityDescriptor(SensitivityLevel.valueOf(rs.getString("sensitivity_level")),VisibilityLevel.valueOf(rs.getString("maximum_visibility")),nullToBlank(rs.getString("visibility_policy_id")),new PolicyVersion(1,revision,DescriptorFingerprint.sha256(nullToBlank(rs.getString("visibility_policy_id"))+":"+revision)));
        return new ResourceDescriptor(ref,nullToBlank(rs.getString("resource_key")),ownership,parent,root,visibility,ResourceSecurityState.valueOf(rs.getString("security_state")),rs.getLong("participant_version"),rs.getLong("resource_version"),DescriptorAuthority.valueOf(rs.getString("descriptor_authority")),rs.getString("descriptor_hash"),instant(rs,"source_resolved_at",instant(rs,"source_updated_at",Instant.EPOCH)));
    }
    private ResourceOrphanRepairCase orphan(ResultSet rs)throws SQLException{ResourceRef ref=new ResourceRef(rs.getString("tenant_id"),ResourceType.valueOf(rs.getString("resource_type")),rs.getString("resource_id"));OwnershipDescriptor repaired=new OwnershipDescriptor(nullToBlank(rs.getString("proposed_owner_department_id")),nullToBlank(rs.getString("proposed_owner_group_id")),nullToBlank(rs.getString("proposed_steward_user_id")),"","","",rs.getLong("resource_version"));return new ResourceOrphanRepairCase(rs.getString("repair_id"),ref,ResourceOrphanRepairStatus.valueOf(rs.getString("repair_status")),rs.getString("reason_code"),repaired,nullToBlank(rs.getString("assigned_to")),instant(rs,"detected_at",Instant.EPOCH),instant(rs,"updated_at",Instant.EPOCH),rs.getLong("version"));}
    private String projectionStatus(ResourceReconciliationStatus status){return switch(status){case MATCHED,REPAIRED->"CURRENT";case MISSING_PROJECTION->"MISSING";case ORPHANED->"ORPHANED";case STALE_PROJECTION,AUTHORITY_MISMATCH->"STALE";case FAILED->"ERROR";};}
    private static ResourceRef ref(ResultSet rs,String typeColumn,String idColumn,String tenant)throws SQLException{String type=rs.getString(typeColumn),id=rs.getString(idColumn);return type==null||id==null?null:new ResourceRef(tenant,ResourceType.valueOf(type),id);}
    private static Instant instant(ResultSet rs,String column,Instant fallback)throws SQLException{Timestamp value=rs.getTimestamp(column);return value==null?fallback:value.toInstant();}
    private static boolean hasColumn(ResultSet rs,String name){try{rs.findColumn(name);return true;}catch(SQLException ignored){return false;}}
    private static String blankToNull(String value){return value==null||value.isBlank()?null:value.trim();}
    private static String nullToBlank(String value){return value==null?"":value;}
}
