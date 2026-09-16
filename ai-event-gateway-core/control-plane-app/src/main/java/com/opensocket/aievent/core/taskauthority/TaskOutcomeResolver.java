package com.opensocket.aievent.core.taskauthority;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Deterministic A0-R2 TaskOutcome resolver. Same persisted inputs always map to the same output. */
@Service
public class TaskOutcomeResolver {
    public static final String VERSION="A0R2_OUTCOME_RESOLVER_V1";
    private final JdbcTemplate jdbc;
    public TaskOutcomeResolver(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public Resolution resolve(String tenant,String taskId,String reason,String legacyStatus) {
        List<String> convergence=jdbc.queryForList("""
          select c.outcome from execution_plans p
          join plan_execution_runs r on r.tenant_id=p.tenant_id and r.plan_id=p.plan_id
          join plan_execution_convergence_decisions c on c.tenant_id=r.tenant_id and c.run_id=r.run_id
          where p.tenant_id=? and p.task_ref=? order by c.decided_at desc,c.convergence_id desc limit 1
          """,String.class,tenant,taskId);
        if(!convergence.isEmpty()) return new Resolution(mapConvergence(convergence.getFirst()),"PLAN_CONVERGENCE",convergence.getFirst());
        return new Resolution(mapReason(reason,legacyStatus),"TERMINALIZATION_REASON",reason==null?legacyStatus:reason);
    }

    private static String mapConvergence(String v){return switch(value(v)){case "SUCCEEDED"->"SUCCEEDED";case "PARTIAL","PROVISIONAL"->"PARTIAL_SUCCEEDED";case "CANCELLED"->"CANCELLED";default->"FAILED";};}
    private static String mapReason(String reason,String legacy){String r=value(reason);if("PLAN_COMPLETED".equals(r))return "SUCCEEDED";if("USER_CANCELLED".equals(r)||"KILL_SWITCHED".equals(r)||"MANUAL_ABORT".equals(r))return "CANCELLED";String s=value(legacy);if("SUCCEEDED".equals(s)||"COMPLETED".equals(s))return "SUCCEEDED";if("CANCELLED".equals(s)||"SUPPRESSED".equals(s))return "CANCELLED";return "FAILED";}
    private static String value(String v){return v==null?"":v.trim().toUpperCase(java.util.Locale.ROOT);}
    public record Resolution(String outcome,String source,String sourceValue){}
}
