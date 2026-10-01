package com.opensocket.aievent.database.persistence.configuration;

import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import com.opensocket.aievent.core.kernel.configuration.cutover.RuntimeConfigurationCutoverWaveCertification;
import com.opensocket.aievent.core.kernel.configuration.cutover.RuntimeConfigurationCutoverWaveCertificationStore;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

@DatabaseRepositoryAdapter
public class JdbcRuntimeConfigurationCutoverWaveCertificationStore implements RuntimeConfigurationCutoverWaveCertificationStore {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcRuntimeConfigurationCutoverWaveCertificationStore(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
    @Override public RuntimeConfigurationCutoverWaveCertification save(RuntimeConfigurationCutoverWaveCertification c){
        jdbc.update("""
            insert into runtime_config_cutover_wave_certifications
             (certification_id,wave_id,status,authority_contract_version,config_set_count,runtime_key_count,required_node_count,converged_node_count,evidence_json,certified_by,reason,certified_at)
            values (:id,:wave,:status,:contract,:sets,:keys,:required,:converged,cast(:evidence as jsonb),:actor,:reason,:at)
            """,new MapSqlParameterSource().addValue("id",c.certificationId()).addValue("wave",c.waveId()).addValue("status",c.status())
                .addValue("contract",c.authorityContractVersion()).addValue("sets",c.configSetCount()).addValue("keys",c.runtimeKeyCount())
                .addValue("required",c.requiredNodeCount()).addValue("converged",c.convergedNodeCount()).addValue("evidence",c.evidenceJson())
                .addValue("actor",c.certifiedBy()).addValue("reason",c.reason()).addValue("at",c.certifiedAt()));
        return c;
    }
    @Override public Optional<RuntimeConfigurationCutoverWaveCertification> latest(String waveId){
        return jdbc.query("""
            select certification_id,wave_id,status,authority_contract_version,config_set_count,runtime_key_count,
                   required_node_count,converged_node_count,evidence_json::text evidence_json,certified_by,reason,certified_at
              from runtime_config_cutover_wave_certifications where wave_id=:wave order by certified_at desc limit 1
            """,new MapSqlParameterSource("wave",required(waveId)),(rs,n)->new RuntimeConfigurationCutoverWaveCertification(
                rs.getString("certification_id"),rs.getString("wave_id"),rs.getString("status"),rs.getInt("authority_contract_version"),
                rs.getInt("config_set_count"),rs.getInt("runtime_key_count"),rs.getInt("required_node_count"),rs.getInt("converged_node_count"),
                rs.getString("evidence_json"),rs.getString("certified_by"),rs.getString("reason"),rs.getObject("certified_at",java.time.OffsetDateTime.class))).stream().findFirst();
    }
    private static String required(String value){if(value==null||value.isBlank())throw new IllegalArgumentException("waveId is required");return value.trim();}
}
