package com.opensocket.aievent.core.security.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Secret-safe fallback for failed security audit writes.
 *
 * <p>The primary security decision remains authoritative. This reporter records that its audit
 * evidence could not be persisted and emits both a durable fallback row and an operational metric.
 * If the database itself is unavailable, the structured ERROR log remains the last-resort evidence.</p>
 */
@Component
public final class PersistentSecurityAuditFailureReporter implements SecurityAuditFailureReporter {
    private static final Logger log=LoggerFactory.getLogger(PersistentSecurityAuditFailureReporter.class);
    private final JdbcTemplate jdbc;
    private final MeterRegistry meters;
    private final Clock clock;

    public PersistentSecurityAuditFailureReporter(ObjectProvider<JdbcTemplate> jdbc,
                                                  ObjectProvider<MeterRegistry> meters,
                                                  ObjectProvider<Clock> clocks) {
        this.jdbc=jdbc==null?null:jdbc.getIfAvailable();
        this.meters=meters==null?null:meters.getIfAvailable();
        Clock provided=clocks==null?null:clocks.getIfAvailable();
        this.clock=provided==null?Clock.systemUTC():provided;
    }

    @Override
    public void report(String component,String auditType,String tenantId,String principalId,
                       String correlationId,String reasonCode,RuntimeException failure) {
        String safeComponent=safe(component,160,"UNKNOWN_COMPONENT");
        String safeType=safe(auditType,96,"UNKNOWN_AUDIT");
        String correlation=safe(correlationId,160,"audit-failure-"+UUID.randomUUID());
        String reason=safe(reasonCode,128,"SECURITY_AUDIT_SINK_FAILED");
        String exceptionClass=failure==null?"java.lang.RuntimeException":safe(failure.getClass().getName(),256,"java.lang.RuntimeException");
        String message=safeMessage(failure==null?null:failure.getMessage());
        String fingerprint=sha256(exceptionClass+"|"+message);
        Instant now=clock.instant();

        log.error("security_audit_sink_failed component={} auditType={} tenantId={} principalId={} correlationId={} reasonCode={} exceptionClass={} errorFingerprint={} authoritativeDecisionUnchanged=true",
                safeComponent,safeType,safe(tenantId,128,null),safe(principalId,160,null),correlation,reason,exceptionClass,fingerprint,failure);
        if(meters!=null){
            Counter.builder("opendispatch.security.audit.write.failures")
                    .tag("component",safeComponent).tag("audit_type",safeType).tag("reason",reason)
                    .register(meters).increment();
        }
        if(jdbc==null)return;
        try{
            jdbc.update("insert into security_audit_delivery_failures(failure_id,component,audit_type,tenant_id,principal_id,correlation_id,reason_code,exception_class,error_fingerprint,safe_message,occurred_at,created_at) values(?,?,?,?,?,?,?,?,?,?,?,?)",
                    "saf-"+UUID.randomUUID(),safeComponent,safeType,safe(tenantId,128,null),safe(principalId,160,null),
                    correlation,reason,exceptionClass,fingerprint,message,Timestamp.from(now),Timestamp.from(now));
        }catch(RuntimeException fallbackFailure){
            log.error("security_audit_fallback_persist_failed component={} auditType={} correlationId={} reasonCode={} primaryErrorFingerprint={} authoritativeDecisionUnchanged=true",
                    safeComponent,safeType,correlation,reason,fingerprint,fallbackFailure);
        }
    }

    private static String safeMessage(String value){
        if(value==null||value.isBlank())return null;
        String redacted=value.replaceAll("(?i)(authorization|password|secret|token|credential|cookie)\\s*[:=]\\s*[^,;\\s]+","$1=[REDACTED]");
        return safe(redacted,1000,null);
    }
    private static String safe(String value,int max,String fallback){
        if(value==null||value.isBlank())return fallback;
        String normalized=value.trim();return normalized.length()<=max?normalized:normalized.substring(0,max);
    }
    private static String sha256(String value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){return "fingerprint-unavailable";}
    }
}
