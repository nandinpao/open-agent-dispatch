package com.opensocket.aievent.core.operational;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.opensocket.aievent.core.configuration.runtime.DynamicFixedDelayTask;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Bounded operational backlog telemetry with V41-C3R2N runtime-managed cadence and SLO thresholds. */
@Component
public class RuntimeOperationalBacklogMetrics implements InitializingBean, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(RuntimeOperationalBacklogMetrics.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Counter refreshFailures;
    private final OperationalRuntimeConfigurationView runtimeConfiguration;
    private final long remoteTrackingAgeSloSeconds;
    private final DynamicFixedDelayTask dynamicTask;
    private final BacklogMetric dispatch = new BacklogMetric();
    private final BacklogMetric reconciliation = new BacklogMetric();
    private final BacklogMetric remoteTracking = new BacklogMetric();

    public RuntimeOperationalBacklogMetrics(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            MeterRegistry registry,
            OperationalRuntimeConfigurationView runtimeConfiguration,
            @Value("${opendispatch.operational.slo.remote-tracking-oldest-age-seconds:30}") long remoteTrackingAgeSloSeconds,
            @Qualifier("maintenanceOperationalScheduler") TaskScheduler taskScheduler) {
        this.jdbc=jdbc; this.transactions=new TransactionTemplate(transactionManager); this.runtimeConfiguration=runtimeConfiguration;
        this.remoteTrackingAgeSloSeconds=Math.max(1L,remoteTrackingAgeSloSeconds);
        this.refreshFailures=Counter.builder("opendispatch.runtime.backlog.refresh.failures").description("Failures while refreshing bounded operational backlog metrics").register(registry);
        register(registry,"dispatch",dispatch); register(registry,"a2a-reconciliation",reconciliation); register(registry,"remote-tracking",remoteTracking);
        this.dynamicTask=new DynamicFixedDelayTask(taskScheduler,"operational-backlog-metrics",this::refresh,runtimeConfiguration::refreshInterval);
    }

    @Override public void afterPropertiesSet(){dynamicTask.start();}
    @Override public void destroy(){dynamicTask.stop();}

    public void refresh() {
        long dispatchCount=0L,reconciliationCount=0L,trackingCount=0L;
        long dispatchOldestMillis=0L,reconciliationOldestMillis=0L,trackingOldestMillis=0L;
        List<String> tenants=jdbc.queryForList("select tenant_id from tenants where status='ACTIVE' order by tenant_id",String.class);
        for(String tenant:tenants){try{TenantSnapshot snapshot=transactions.execute(status->readTenant(tenant));if(snapshot==null)continue;dispatchCount+=snapshot.dispatch().count();reconciliationCount+=snapshot.reconciliation().count();trackingCount+=snapshot.remoteTracking().count();dispatchOldestMillis=Math.max(dispatchOldestMillis,snapshot.dispatch().oldestMillis());reconciliationOldestMillis=Math.max(reconciliationOldestMillis,snapshot.reconciliation().oldestMillis());trackingOldestMillis=Math.max(trackingOldestMillis,snapshot.remoteTracking().oldestMillis());}catch(RuntimeException failure){refreshFailures.increment();log.warn("operational_backlog_refresh_failed tenant={} errorType={} message={}",tenant,failure.getClass().getSimpleName(),safe(failure.getMessage()));}}
        dispatch.set(dispatchCount,dispatchOldestMillis,runtimeConfiguration.dispatchSloSeconds());
        reconciliation.set(reconciliationCount,reconciliationOldestMillis,runtimeConfiguration.reconciliationSloSeconds());
        remoteTracking.set(trackingCount,trackingOldestMillis,remoteTrackingAgeSloSeconds);
    }

    private TenantSnapshot readTenant(String tenant){bind(tenant);Backlog dispatchBacklog=query("""
        select count(*)::bigint, coalesce(extract(epoch from (now()-min(coalesce(updated_at,created_at))))*1000,0)::bigint
          from dispatch_requests where status='APPROVED' or (status='RETRY_WAITING' and (next_retry_at is null or next_retry_at<=now())) or (status='DISPATCHING' and (claim_until is null or claim_until<=now()))
        """);Backlog reconciliationBacklog=query("""
        select count(*)::bigint, coalesce(extract(epoch from (now()-min(coalesce(next_attempt_at,created_at))))*1000,0)::bigint
          from a2a_reconciliation_cases where status in ('OPEN','READY','RETRY_WAITING','CLAIMED','EXECUTING') and coalesce(next_attempt_at,created_at)<=now() and (claim_until is null or claim_until<=now())
        """);Backlog remoteTrackingBacklog=query("""
        select count(*)::bigint, coalesce(extract(epoch from (now()-min(coalesce(next_poll_at,updated_at))))*1000,0)::bigint
          from a2a_remote_tracking_leases where status in ('PENDING','ACTIVE','RECONCILING','CANCELING') and (lease_until is null or lease_until<=now()) and (next_poll_at is null or next_poll_at<=now())
        """);return new TenantSnapshot(dispatchBacklog,reconciliationBacklog,remoteTrackingBacklog);}
    private Backlog query(String sql){return jdbc.queryForObject(sql,(rs,row)->new Backlog(rs.getLong(1),Math.max(0L,rs.getLong(2))));}
    private void bind(String tenant){jdbc.queryForObject("select set_config('app.current_tenant_id', ?, true)",String.class,tenant);jdbc.queryForObject("select set_config('app.current_actor_id', ?, true)",String.class,"pc-s6-operational-metrics");}
    private static void register(MeterRegistry registry,String queue,BacklogMetric metric){Gauge.builder("opendispatch.runtime.backlog.count",metric.count,AtomicLong::doubleValue).description("Number of currently due operational work items").tag("queue",queue).register(registry);Gauge.builder("opendispatch.runtime.backlog.oldest.age.seconds",metric.oldestMillis,value->value.doubleValue()/1000.0d).description("Age in seconds of the oldest currently due operational work item").tag("queue",queue).register(registry);Gauge.builder("opendispatch.runtime.slo.breach",metric.sloBreached,AtomicLong::doubleValue).description("1 when oldest due work exceeds the current configured age SLO, otherwise 0").tag("queue",queue).register(registry);}
    private static String safe(String value){if(value==null)return "";String oneLine=value.replace('\n',' ').replace('\r',' ');return oneLine.length()<=240?oneLine:oneLine.substring(0,240);}
    private record Backlog(long count,long oldestMillis){} private record TenantSnapshot(Backlog dispatch,Backlog reconciliation,Backlog remoteTracking){}
    private static final class BacklogMetric{private final AtomicLong count=new AtomicLong();private final AtomicLong oldestMillis=new AtomicLong();private final AtomicLong sloBreached=new AtomicLong();private void set(long count,long oldestMillis,long sloSeconds){this.count.set(Math.max(0L,count));this.oldestMillis.set(Math.max(0L,oldestMillis));this.sloBreached.set(oldestMillis>sloSeconds*1000L?1L:0L);}}
}
