package com.opensocket.aievent.database.persistence.configuration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.opensocket.aievent.core.kernel.configuration.cutover.RuntimeConfigurationCutoverWave;
import com.opensocket.aievent.core.kernel.configuration.cutover.RuntimeConfigurationCutoverWaveStore;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

/** PostgreSQL projection of release-governed Runtime Configuration cutover waves. */
@DatabaseRepositoryAdapter
public class JdbcRuntimeConfigurationCutoverWaveStore implements RuntimeConfigurationCutoverWaveStore {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcRuntimeConfigurationCutoverWaveStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<RuntimeConfigurationCutoverWave> list() {
        return load("", new MapSqlParameterSource());
    }

    @Override
    public Optional<RuntimeConfigurationCutoverWave> find(String waveId) {
        return load(" where w.wave_id=:waveId", new MapSqlParameterSource("waveId", required(waveId, "waveId")))
                .stream().findFirst();
    }

    @Override
    public Optional<RuntimeConfigurationCutoverWave> findBySetKey(String setKey) {
        return load(" where exists (select 1 from runtime_config_cutover_wave_members mx where mx.wave_id=w.wave_id and mx.set_key=:setKey)",
                new MapSqlParameterSource("setKey", required(setKey, "setKey"))).stream().findFirst();
    }

    private List<RuntimeConfigurationCutoverWave> load(String where, MapSqlParameterSource params) {
        Map<String, Builder> grouped = new LinkedHashMap<>();
        jdbc.query(SELECT + where + " order by w.sequence_no,m.member_sequence_no", params, rs -> {
            String waveId = rs.getString("wave_id");
            int waveSequenceNo = rs.getInt("wave_sequence_no");
            String displayName = rs.getString("display_name");
            String riskTier = rs.getString("risk_tier");
            int requiredAuthorityContractVersion = rs.getInt("required_authority_contract_version");
            String releaseStage = rs.getString("release_stage");

            Builder b = grouped.computeIfAbsent(waveId, ignored -> new Builder(
                    waveId,
                    waveSequenceNo,
                    displayName,
                    riskTier,
                    requiredAuthorityContractVersion,
                    releaseStage));
            b.members.add(new RuntimeConfigurationCutoverWave.Member(
                    rs.getString("set_key"),
                    rs.getInt("member_sequence_no"),
                    rs.getInt("expected_runtime_key_count")));
        });
        return grouped.values().stream().map(Builder::build).toList();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    private static final String SELECT = """
        select w.wave_id,w.sequence_no wave_sequence_no,w.display_name,w.risk_tier,
               w.required_authority_contract_version,w.release_stage,
               m.set_key,m.member_sequence_no,m.expected_runtime_key_count
          from runtime_config_cutover_waves w
          join runtime_config_cutover_wave_members m on m.wave_id=w.wave_id
        """;

    private static final class Builder {
        private final String waveId;
        private final int sequenceNo;
        private final String displayName;
        private final String riskTier;
        private final int requiredAuthorityContractVersion;
        private final String releaseStage;
        private final List<RuntimeConfigurationCutoverWave.Member> members = new ArrayList<>();

        private Builder(String waveId, int sequenceNo, String displayName, String riskTier,
                int requiredAuthorityContractVersion, String releaseStage) {
            this.waveId = waveId;
            this.sequenceNo = sequenceNo;
            this.displayName = displayName;
            this.riskTier = riskTier;
            this.requiredAuthorityContractVersion = requiredAuthorityContractVersion;
            this.releaseStage = releaseStage;
        }

        private RuntimeConfigurationCutoverWave build() {
            return new RuntimeConfigurationCutoverWave(waveId, sequenceNo, displayName, riskTier,
                    requiredAuthorityContractVersion, releaseStage, members);
        }
    }
}
