package com.opensocket.aievent.core.capability;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Stage 10 live worker. REPLAY mode never enqueues current work. */
@Component
public class SemanticTriageAdaptiveRoutingWorker {
    private final JdbcTemplate jdbc; private final SemanticTriageAdaptiveRoutingService service; private final String workerId="stage10-triage-"+UUID.randomUUID();
    public SemanticTriageAdaptiveRoutingWorker(JdbcTemplate jdbc,SemanticTriageAdaptiveRoutingService service){this.jdbc=jdbc;this.service=service;}
    @Scheduled(fixedDelayString="${opendispatch.stage10.semantic-triage.worker-delay-ms:5000}")
    public void tick(){List<String> tenants=jdbc.queryForList("select tenant_id from tenants where status='ACTIVE'",String.class);for(String t:tenants){try{service.enqueueRuntimeNoMatchCandidates(t,100);for(String task:service.claimDue(t,workerId,25)){try{service.evaluateTask(t,task,"RUNTIME");service.completeWork(t,task);}catch(Exception ex){service.failWork(t,task,ex.getMessage());}}}catch(Exception ignored){}}}
}
