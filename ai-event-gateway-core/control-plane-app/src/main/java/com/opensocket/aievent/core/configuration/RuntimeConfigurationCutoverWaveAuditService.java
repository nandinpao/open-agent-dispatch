package com.opensocket.aievent.core.configuration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

/** Durable append-only audit trail for high-risk Runtime Configuration cutover wave mutations. */
@Service
public class RuntimeConfigurationCutoverWaveAuditService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public RuntimeConfigurationCutoverWaveAuditService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc; this.json = json;
    }

    public void record(String action, String permissionCode, RuntimeConfigurationCutoverWaveService.WaveStatus before,
            RuntimeConfigurationCutoverWaveService.WaveStatus after, String actor, String reason,
            String correlationId, Map<String,Object> additional) {
        Map<String,Object> detail = new LinkedHashMap<>();
        detail.put("before", summary(before));
        detail.put("after", summary(after));
        if (additional != null && !additional.isEmpty()) detail.put("evidence", additional);
        jdbc.update("insert into runtime_config_cutover_wave_audit_events("
                        + "event_id,wave_id,action_code,permission_code,actor_id,audit_reason,correlation_id,before_phase,after_phase,detail_json,created_at) "
                        + "values(?,?,?,?,?,?,?,?,?,cast(? as jsonb),now())",
                UUID.randomUUID().toString(), after != null ? after.waveId() : before.waveId(), required(action,"action"),
                required(permissionCode,"permissionCode"), required(actor,"actor"), required(reason,"reason"),
                text(correlationId), before == null ? null : before.phase(), after == null ? null : after.phase(), serialize(detail));
    }

    private Map<String,Object> summary(RuntimeConfigurationCutoverWaveService.WaveStatus value) {
        if (value == null) return Map.of();
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("waveId", value.waveId()); out.put("phase", value.phase()); out.put("riskTier", value.riskTier());
        out.put("configSetCount", value.configSetCount()); out.put("runtimeKeyCount", value.runtimeKeyCount());
        out.put("requiredNodeCount", value.requiredNodeCount()); out.put("convergedNodeCount", value.convergedNodeCount());
        out.put("safetyAttestationStatus", value.safetyAttestationStatus()); out.put("safetyAttestationId", value.safetyAttestationId());
        out.put("blockers", value.blockers());
        return out;
    }

    private String serialize(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalStateException("CONFIGURATION_CUTOVER_AUDIT_SERIALIZATION_FAILED", ex); }
    }
    private static String required(String value,String field){if(value==null||value.isBlank())throw new IllegalArgumentException(field+" is required");return value.trim();}
    private static String text(String value){return value==null?"":value.trim();}
}
