package com.opensocket.aievent.database.persistence.configuration;

import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import com.opensocket.aievent.core.kernel.configuration.cutover.RuntimeConfigurationCutoverWaveSafetyAttestation;
import com.opensocket.aievent.core.kernel.configuration.cutover.RuntimeConfigurationCutoverWaveSafetyAttestationStore;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

@DatabaseRepositoryAdapter
public class JdbcRuntimeConfigurationCutoverWaveSafetyAttestationStore implements RuntimeConfigurationCutoverWaveSafetyAttestationStore {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcRuntimeConfigurationCutoverWaveSafetyAttestationStore(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
    @Override public RuntimeConfigurationCutoverWaveSafetyAttestation save(RuntimeConfigurationCutoverWaveSafetyAttestation a){
        jdbc.update("""
            insert into runtime_config_cutover_wave_safety_attestations
             (attestation_id,wave_id,status,evidence_json,attested_by,reason,captured_at,expires_at)
            values (:id,:wave,:status,cast(:evidence as jsonb),:actor,:reason,:captured,:expires)
            """,new MapSqlParameterSource().addValue("id",a.attestationId()).addValue("wave",a.waveId()).addValue("status",a.status())
                .addValue("evidence",a.evidenceJson()).addValue("actor",a.attestedBy()).addValue("reason",a.reason())
                .addValue("captured",a.capturedAt()).addValue("expires",a.expiresAt()));
        return a;
    }
    @Override public Optional<RuntimeConfigurationCutoverWaveSafetyAttestation> latest(String waveId){
        return jdbc.query("""
            select attestation_id,wave_id,status,evidence_json::text evidence_json,attested_by,reason,captured_at,expires_at
              from runtime_config_cutover_wave_safety_attestations where wave_id=:wave order by captured_at desc limit 1
            """,new MapSqlParameterSource("wave",required(waveId)),(rs,n)->new RuntimeConfigurationCutoverWaveSafetyAttestation(
                rs.getString("attestation_id"),rs.getString("wave_id"),rs.getString("status"),rs.getString("evidence_json"),
                rs.getString("attested_by"),rs.getString("reason"),rs.getObject("captured_at",java.time.OffsetDateTime.class),
                rs.getObject("expires_at",java.time.OffsetDateTime.class))).stream().findFirst();
    }
    private static String required(String value){if(value==null||value.isBlank())throw new IllegalArgumentException("waveId is required");return value.trim();}
}
