package com.opensocket.aievent.core.enforcement.activation.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import com.opensocket.aievent.core.enforcement.activation.application.AuthorityActivationTargetRepository;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityActivationTarget;

public final class JdbcAuthorityActivationTargetRepository implements AuthorityActivationTargetRepository {
    private final JdbcTemplate jdbc;
    private final JdbcInstanceControlPlaneTransaction controlPlane;

    public JdbcAuthorityActivationTargetRepository(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.controlPlane = new JdbcInstanceControlPlaneTransaction(jdbc, transactions);
    }

    @Override
    public Optional<AuthorityActivationTarget> current() {
        return controlPlane.execute(() -> {
            List<AuthorityActivationTarget> rows = jdbc.query(
                    "select target_revision,target_checksum,generation,updated_at "
                            + "from enforcement_authority_activation_target where singleton_id='ACTIVE'",
                    (rs, row) -> new AuthorityActivationTarget(
                            rs.getLong("target_revision"),
                            rs.getString("target_checksum"),
                            rs.getLong("generation"),
                            rs.getObject("updated_at", OffsetDateTime.class).toInstant()));
            return rows.stream().findFirst();
        });
    }
}
