package com.opensocket.aievent.core.resourceaccess.persistence;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL authority for P4RA-J materialized scope snapshots and department revision cutover. */
@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix="resource-access",name={"enabled","scope-snapshot-enabled"},havingValue="true")
public class JdbcMaterializedScopeSnapshotAdapter
        implements MaterializedScopeSnapshotPort, DepartmentRevisionCutoverPort {
    private final JdbcTemplate jdbc;

    public JdbcMaterializedScopeSnapshotAdapter(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public Optional<MaterializedScopeSnapshot> findActive(
            String tenantId, ScopeSnapshotKind kind, String principalType, String principalId,
            String permissionCode, ResourceType resourceType, PolicyVersion policyVersion,
            SecurityEpoch securityEpoch, Instant at) {
        return jdbc.query("""
                select s.* from resource_scope_materialized_snapshots s
                join resource_department_revision_cutovers c
                  on c.tenant_id=s.tenant_id and c.department_revision=s.department_revision
                 and c.cutover_status='ACTIVE'
                where s.tenant_id=? and s.snapshot_kind=? and s.principal_type=? and s.principal_id=?
                  and s.permission_code=? and s.resource_type=? and s.snapshot_status='ACTIVE'
                  and s.policy_catalog_version=? and s.policy_revision=? and s.policy_content_hash=?
                  and s.global_epoch=? and s.tenant_epoch=? and s.principal_epoch=? and s.resource_epoch=?
                  and s.epoch_policy_catalog_version=? and s.department_revision=?
                  and s.activated_at<=? and (s.retired_at is null or s.retired_at>?)
                  and not exists (
                    select 1 from resource_scope_shares rss
                     where rss.tenant_id=s.tenant_id and rss.resource_type=s.resource_type
                       and rss.valid_to is not null and rss.resource_id=any(s.explicit_resource_ids)
                  )
                order by s.activated_at desc limit 1
                """, (rs,row)->mapSnapshot(rs), tenantId,kind.name(),principalType,principalId,permissionCode,
                resourceType.name(),policyVersion.catalogVersion(),policyVersion.policyRevision(),policyVersion.contentHash(),
                securityEpoch.globalEpoch(),securityEpoch.tenantEpoch(),securityEpoch.principalEpoch(),securityEpoch.resourceEpoch(),
                securityEpoch.policyCatalogVersion(),securityEpoch.departmentTreeRevision(),ts(at),ts(at)).stream().findFirst();
    }

    @Override
    @Transactional
    public MaterializedScopeSnapshot savePrepared(MaterializedScopeSnapshot snapshot, String correlationId) {
        int inserted = jdbc.update("""
                insert into resource_scope_materialized_snapshots(
                 tenant_id,snapshot_id,snapshot_kind,principal_type,principal_id,permission_code,resource_type,strategy,
                 exact_department_ids,subtree_department_root_ids,group_ids,explicit_resource_ids,excluded_resource_ids,
                 denied_department_ids,denied_subtree_department_root_ids,denied_group_ids,maximum_visibility,
                 policy_catalog_version,policy_revision,policy_content_hash,global_epoch,tenant_epoch,principal_epoch,
                 resource_epoch,epoch_policy_catalog_version,department_revision,plan_hash,snapshot_status,prepared_at,
                 activated_at,retired_at,version,created_at,updated_at)
                values(?,?,?,?,?,?,?,?,?::varchar[],?::varchar[],?::varchar[],?::varchar[],?::varchar[],?::varchar[],?::varchar[],?::varchar[],?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                on conflict(tenant_id,snapshot_id) do nothing
                """,
                snapshot.tenantId(),snapshot.snapshotId(),snapshot.snapshotKind().name(),snapshot.principalType(),snapshot.principalId(),
                snapshot.permissionCode(),snapshot.resourceType().name(),snapshot.strategy(),pg(snapshot.exactDepartmentIds()),
                pg(snapshot.subtreeDepartmentRootIds()),pg(snapshot.groupIds()),pg(snapshot.explicitResourceIds()),
                pg(snapshot.excludedResourceIds()),pg(snapshot.deniedDepartmentIds()),pg(snapshot.deniedSubtreeDepartmentRootIds()),
                pg(snapshot.deniedGroupIds()),snapshot.maximumVisibility().name(),snapshot.policyVersion().catalogVersion(),
                snapshot.policyVersion().policyRevision(),snapshot.policyVersion().contentHash(),snapshot.securityEpoch().globalEpoch(),
                snapshot.securityEpoch().tenantEpoch(),snapshot.securityEpoch().principalEpoch(),snapshot.securityEpoch().resourceEpoch(),
                snapshot.securityEpoch().policyCatalogVersion(),snapshot.departmentRevision(),snapshot.planHash(),
                MaterializedScopeSnapshotStatus.PREPARED.name(),ts(snapshot.preparedAt()),null,null,snapshot.version(),
                ts(snapshot.preparedAt()),ts(snapshot.preparedAt()));
        if(inserted==0){
            return jdbc.query("""
                    select * from resource_scope_materialized_snapshots
                     where tenant_id=? and snapshot_kind=? and principal_type=? and principal_id=?
                       and permission_code=? and resource_type=? and policy_catalog_version=? and policy_revision=?
                       and policy_content_hash=? and global_epoch=? and tenant_epoch=? and principal_epoch=?
                       and resource_epoch=? and epoch_policy_catalog_version=? and department_revision=?
                       and snapshot_status='PREPARED' order by prepared_at desc limit 1
                    """,(rs,row)->mapSnapshot(rs),snapshot.tenantId(),snapshot.snapshotKind().name(),snapshot.principalType(),
                    snapshot.principalId(),snapshot.permissionCode(),snapshot.resourceType().name(),snapshot.policyVersion().catalogVersion(),
                    snapshot.policyVersion().policyRevision(),snapshot.policyVersion().contentHash(),snapshot.securityEpoch().globalEpoch(),
                    snapshot.securityEpoch().tenantEpoch(),snapshot.securityEpoch().principalEpoch(),snapshot.securityEpoch().resourceEpoch(),
                    snapshot.securityEpoch().policyCatalogVersion(),snapshot.departmentRevision()).stream().findFirst().orElse(snapshot);
        }
        return snapshot;
    }

    @Override
    public long countPrepared(String tenantId,long revision){
        Long count=jdbc.queryForObject("select count(*) from resource_scope_materialized_snapshots where tenant_id=? and department_revision=? and snapshot_status='PREPARED'",Long.class,tenantId,revision);
        return count==null?0:count;
    }

    @Override
    @Transactional
    public int retireBeforeDepartmentRevision(String tenantId,long revision,String reasonCode,String correlationId,Instant at) {
        return jdbc.update("""
                update resource_scope_materialized_snapshots
                   set snapshot_status='RETIRED',retired_at=?,updated_at=?,version=version+1
                 where tenant_id=? and department_revision<? and snapshot_status in ('PREPARED','ACTIVE')
                """,ts(at),ts(at),tenantId,revision);
    }

    @Override
    public Optional<DepartmentRevisionCutover> current(String tenantId) {
        return jdbc.query("select * from resource_department_revision_cutovers where tenant_id=? and cutover_status='ACTIVE' order by activated_at desc limit 1",
                (rs,row)->mapCutover(rs),tenantId).stream().findFirst();
    }

    @Override
    @Transactional
    public DepartmentRevisionCutover prepare(String tenantId,long revision,long count,String actor,String correlation,Instant at) {
        String id="drc-"+UUID.randomUUID();
        jdbc.update("""
                insert into resource_department_revision_cutovers(
                 tenant_id,cutover_id,department_revision,cutover_status,prepared_snapshot_count,prepared_by,
                 activated_by,correlation_id,prepared_at,activated_at,retired_at,failure_reason_code,version,created_at,updated_at)
                values(?,?,?,'PREPARING',?,?, '',?,?,null,null,'',0,?,?)
                """,tenantId,id,revision,count,actor,correlation,ts(at),ts(at),ts(at),ts(at));
        appendCutoverEvent(tenantId,id,revision,"PREPARED",actor,"DEPARTMENT_REVISION_CUTOVER_PREPARED",correlation,at);
        return new DepartmentRevisionCutover(tenantId,id,revision,DepartmentRevisionCutoverStatus.PREPARING,count,actor,"",correlation,at,null,0);
    }

    @Override
    @Transactional
    public DepartmentRevisionCutover activate(String tenantId,String cutoverId,long expectedVersion,String actor,String correlation,Instant at) {
        jdbc.query("select pg_advisory_xact_lock(hashtext(?))",(rs,row)->0,"p4ra-j-cutover:"+tenantId);
        DepartmentRevisionCutover target=findCutover(tenantId,cutoverId);
        if(target.version()!=expectedVersion||target.status()!=DepartmentRevisionCutoverStatus.PREPARING)
            throw new IllegalStateException("DEPARTMENT_REVISION_CUTOVER_CONFLICT");
        Long prepared=jdbc.queryForObject("select count(*) from resource_scope_materialized_snapshots where tenant_id=? and department_revision=? and snapshot_status='PREPARED'",Long.class,tenantId,target.departmentRevision());
        if(prepared==null||prepared<target.preparedSnapshotCount())throw new IllegalStateException("SCOPE_SNAPSHOT_PREPARE_INCOMPLETE");
        jdbc.update("update resource_scope_materialized_snapshots set snapshot_status='RETIRED',retired_at=?,updated_at=?,version=version+1 where tenant_id=? and snapshot_status='ACTIVE'",ts(at),ts(at),tenantId);
        jdbc.update("update resource_department_revision_cutovers set cutover_status='RETIRED',retired_at=?,updated_at=?,version=version+1 where tenant_id=? and cutover_status='ACTIVE'",ts(at),ts(at),tenantId);
        jdbc.update("update resource_scope_materialized_snapshots set snapshot_status='ACTIVE',activated_at=?,updated_at=?,version=version+1 where tenant_id=? and department_revision=? and snapshot_status='PREPARED'",ts(at),ts(at),tenantId,target.departmentRevision());
        int changed=jdbc.update("""
                update resource_department_revision_cutovers set cutover_status='ACTIVE',activated_by=?,activated_at=?,updated_at=?,version=version+1
                 where tenant_id=? and cutover_id=? and version=? and cutover_status='PREPARING'
                """,actor,ts(at),ts(at),tenantId,cutoverId,expectedVersion);
        if(changed!=1)throw new IllegalStateException("DEPARTMENT_REVISION_CUTOVER_CONFLICT");
        appendCutoverEvent(tenantId,cutoverId,target.departmentRevision(),"ACTIVATED",actor,"DEPARTMENT_REVISION_CUTOVER_ACTIVATED",correlation,at);
        return new DepartmentRevisionCutover(tenantId,cutoverId,target.departmentRevision(),DepartmentRevisionCutoverStatus.ACTIVE,
                target.preparedSnapshotCount(),target.preparedBy(),actor,correlation,target.preparedAt(),at,expectedVersion+1);
    }

    @Override
    @Transactional
    public DepartmentRevisionCutover fail(String tenantId,String cutoverId,long expectedVersion,String reason,String actor,String correlation,Instant at) {
        DepartmentRevisionCutover target=findCutover(tenantId,cutoverId);
        int changed=jdbc.update("""
                update resource_department_revision_cutovers set cutover_status='FAILED',failure_reason_code=?,updated_at=?,version=version+1
                 where tenant_id=? and cutover_id=? and version=? and cutover_status='PREPARING'
                """,reason,ts(at),tenantId,cutoverId,expectedVersion);
        if(changed!=1)throw new IllegalStateException("DEPARTMENT_REVISION_CUTOVER_CONFLICT");
        appendCutoverEvent(tenantId,cutoverId,target.departmentRevision(),"FAILED",actor,reason,correlation,at);
        return new DepartmentRevisionCutover(tenantId,cutoverId,target.departmentRevision(),DepartmentRevisionCutoverStatus.FAILED,
                target.preparedSnapshotCount(),target.preparedBy(),"",correlation,target.preparedAt(),null,expectedVersion+1);
    }

    private DepartmentRevisionCutover findCutover(String tenant,String id){
        return jdbc.query("select * from resource_department_revision_cutovers where tenant_id=? and cutover_id=?",
                (rs,row)->mapCutover(rs),tenant,id).stream().findFirst().orElseThrow(()->new IllegalArgumentException("cutover not found"));
    }
    private void appendCutoverEvent(String tenant,String id,long revision,String type,String actor,String reason,String correlation,Instant at){
        jdbc.update("insert into resource_department_revision_cutover_events(tenant_id,event_id,cutover_id,department_revision,event_type,actor_id,reason_code,correlation_id,occurred_at,created_at) values(?,?,?,?,?,?,?,?,?,?)",
                tenant,"drce-"+UUID.randomUUID(),id,revision,type,actor,reason,correlation,ts(at),ts(at));
    }
    private MaterializedScopeSnapshot mapSnapshot(ResultSet rs)throws SQLException{
        PolicyVersion policy=new PolicyVersion(rs.getLong("policy_catalog_version"),rs.getLong("policy_revision"),rs.getString("policy_content_hash"));
        SecurityEpoch epoch=new SecurityEpoch(rs.getLong("global_epoch"),rs.getLong("tenant_epoch"),rs.getLong("principal_epoch"),rs.getLong("resource_epoch"),rs.getLong("epoch_policy_catalog_version"),rs.getLong("department_revision"));
        return new MaterializedScopeSnapshot(rs.getString("snapshot_id"),rs.getString("tenant_id"),ScopeSnapshotKind.valueOf(rs.getString("snapshot_kind")),
                rs.getString("principal_type"),rs.getString("principal_id"),rs.getString("permission_code"),ResourceType.valueOf(rs.getString("resource_type")),
                rs.getString("strategy"),set(rs.getArray("exact_department_ids")),set(rs.getArray("subtree_department_root_ids")),set(rs.getArray("group_ids")),
                set(rs.getArray("explicit_resource_ids")),set(rs.getArray("excluded_resource_ids")),set(rs.getArray("denied_department_ids")),
                set(rs.getArray("denied_subtree_department_root_ids")),set(rs.getArray("denied_group_ids")),VisibilityLevel.valueOf(rs.getString("maximum_visibility")),
                policy,epoch,rs.getLong("department_revision"),rs.getString("plan_hash"),MaterializedScopeSnapshotStatus.valueOf(rs.getString("snapshot_status")),
                instant(rs,"prepared_at"),nullable(rs,"activated_at"),nullable(rs,"retired_at"),rs.getLong("version"));
    }
    private DepartmentRevisionCutover mapCutover(ResultSet rs)throws SQLException{
        return new DepartmentRevisionCutover(rs.getString("tenant_id"),rs.getString("cutover_id"),rs.getLong("department_revision"),
                DepartmentRevisionCutoverStatus.valueOf(rs.getString("cutover_status")),rs.getLong("prepared_snapshot_count"),rs.getString("prepared_by"),
                rs.getString("activated_by"),rs.getString("correlation_id"),instant(rs,"prepared_at"),nullable(rs,"activated_at"),rs.getLong("version"));
    }
    private static String pg(java.util.Set<String> values){if(values==null||values.isEmpty())return "{}";return "{"+String.join(",",values.stream().map(JdbcMaterializedScopeSnapshotAdapter::escape).toList())+"}";}
    private static String escape(String v){return "\""+v.replace("\\","\\\\").replace("\"","\\\"")+"\"";}
    private static java.util.Set<String> set(Array array)throws SQLException{if(array==null)return java.util.Set.of();Object[] raw=(Object[])array.getArray();List<String> values=new ArrayList<>();for(Object value:raw)if(value!=null)values.add(String.valueOf(value));return java.util.Set.copyOf(values);}
    private static Timestamp ts(Instant value){return value==null?null:Timestamp.from(value);}    
    private static Instant instant(ResultSet rs,String name)throws SQLException{return rs.getTimestamp(name).toInstant();}
    private static Instant nullable(ResultSet rs,String name)throws SQLException{Timestamp value=rs.getTimestamp(name);return value==null?null:value.toInstant();}
}
