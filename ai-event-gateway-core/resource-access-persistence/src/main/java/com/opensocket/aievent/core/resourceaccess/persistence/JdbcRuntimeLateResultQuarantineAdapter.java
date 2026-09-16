package com.opensocket.aievent.core.resourceaccess.persistence;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Secret-free immutable evidence adapter for stale or late runtime result submissions. */
@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix="resource-access",name={"enabled","runtime-lease-enabled"},havingValue="true")
public class JdbcRuntimeLateResultQuarantineAdapter implements RuntimeLateResultQuarantinePort {
    private final JdbcTemplate jdbc;
    public JdbcRuntimeLateResultQuarantineAdapter(JdbcTemplate jdbc){this.jdbc=Objects.requireNonNull(jdbc,"jdbc");}

    @Override
    @Transactional
    public RuntimeLateResultQuarantine append(RuntimeLateResultQuarantine record){
        RuntimeResultSubmission submission=record.submission();
        int inserted=jdbc.update("""
                insert into resource_runtime_late_result_quarantines(
                 tenant_id,quarantine_id,submission_id,lease_id,presented_fencing_version,resource_type,resource_id,
                 assignment_id,attempt_no,payload_hash,lease_status,reason_code,quarantine_status,correlation_id,
                 observed_at,quarantined_at,resolved_by,resolution_reason,resolved_at,version,created_at,updated_at)
                values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) on conflict(tenant_id,submission_id) do nothing
                """,record.tenantId(),record.quarantineId(),submission.submissionId(),submission.leaseId(),submission.presentedFencingVersion(),
                submission.resourceRef().resourceType().name(),submission.resourceRef().resourceId(),submission.assignmentId(),submission.attemptNo(),
                submission.payloadHash(),record.leaseStatus().name(),record.reasonCode(),record.status().name(),submission.correlationId(),
                ts(submission.observedAt()),ts(record.quarantinedAt()),record.resolvedBy(),record.resolutionReason(),ts(record.resolvedAt()),record.version(),
                ts(record.quarantinedAt()),ts(record.quarantinedAt()));
        RuntimeLateResultQuarantine persisted=inserted==1?record:findBySubmission(record.tenantId(),submission.submissionId()).orElseThrow();
        if(inserted==1)event(persisted,"QUARANTINED","runtime-result-fence",record.reasonCode(),submission.correlationId(),record.quarantinedAt());
        return persisted;
    }
    @Override public Optional<RuntimeLateResultQuarantine> findBySubmission(String tenant,String submission){return jdbc.query("select * from resource_runtime_late_result_quarantines where tenant_id=? and submission_id=?",(rs,row)->map(rs),tenant,submission).stream().findFirst();}
    @Override public List<RuntimeLateResultQuarantine> findOpen(String tenant,int limit){return jdbc.query("select * from resource_runtime_late_result_quarantines where tenant_id=? and quarantine_status='OPEN' order by quarantined_at limit ?",(rs,row)->map(rs),tenant,Math.max(1,Math.min(limit,1000)));}
    @Override
    @Transactional
    public RuntimeLateResultQuarantine resolve(String tenant,String quarantineId,long expectedVersion,RuntimeLateResultQuarantineStatus target,String actor,String reason,Instant at){
        if(target==RuntimeLateResultQuarantineStatus.OPEN)throw new IllegalArgumentException("target must be terminal");
        int changed=jdbc.update("""
                update resource_runtime_late_result_quarantines
                   set quarantine_status=?,resolved_by=?,resolution_reason=?,resolved_at=?,updated_at=?,version=version+1
                 where tenant_id=? and quarantine_id=? and version=? and quarantine_status='OPEN'
                """,target.name(),actor,reason,ts(at),ts(at),tenant,quarantineId,expectedVersion);
        if(changed!=1)throw new IllegalStateException("LATE_RESULT_QUARANTINE_VERSION_CONFLICT");
        RuntimeLateResultQuarantine result=jdbc.query("select * from resource_runtime_late_result_quarantines where tenant_id=? and quarantine_id=?",(rs,row)->map(rs),tenant,quarantineId).stream().findFirst().orElseThrow();
        event(result,target.name(),actor,"RUNTIME_LATE_RESULT_"+target.name(),result.submission().correlationId(),at);return result;
    }
    private void event(RuntimeLateResultQuarantine record,String type,String actor,String reason,String correlation,Instant at){jdbc.update("insert into resource_runtime_late_result_quarantine_events(tenant_id,event_id,quarantine_id,event_type,actor_id,reason_code,correlation_id,occurred_at,created_at) values(?,?,?,?,?,?,?,?,?)",record.tenantId(),"rlrqe-"+UUID.randomUUID(),record.quarantineId(),type,actor,reason,correlation,ts(at),ts(at));}
    private RuntimeLateResultQuarantine map(ResultSet rs)throws SQLException{
        String tenant=rs.getString("tenant_id");RuntimeResultSubmission submission=new RuntimeResultSubmission(tenant,rs.getString("submission_id"),rs.getString("lease_id"),rs.getLong("presented_fencing_version"),new ResourceRef(tenant,ResourceType.valueOf(rs.getString("resource_type")),rs.getString("resource_id")),blank(rs.getString("assignment_id")),(Integer)rs.getObject("attempt_no"),rs.getString("payload_hash"),rs.getString("correlation_id"),instant(rs,"observed_at"));
        return new RuntimeLateResultQuarantine(tenant,rs.getString("quarantine_id"),submission,RuntimeLeaseStatus.valueOf(rs.getString("lease_status")),rs.getString("reason_code"),RuntimeLateResultQuarantineStatus.valueOf(rs.getString("quarantine_status")),blank(rs.getString("resolved_by")),blank(rs.getString("resolution_reason")),instant(rs,"quarantined_at"),nullable(rs,"resolved_at"),rs.getLong("version"));
    }
    private static String blank(String v){return v==null?"":v;}
    private static Timestamp ts(Instant v){return v==null?null:Timestamp.from(v);}private static Instant instant(ResultSet rs,String c)throws SQLException{return rs.getTimestamp(c).toInstant();}private static Instant nullable(ResultSet rs,String c)throws SQLException{Timestamp t=rs.getTimestamp(c);return t==null?null:t.toInstant();}
}
