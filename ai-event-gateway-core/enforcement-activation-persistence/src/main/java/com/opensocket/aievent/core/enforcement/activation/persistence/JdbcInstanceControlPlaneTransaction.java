package com.opensocket.aievent.core.enforcement.activation.persistence;

import java.util.Objects;
import java.util.concurrent.Callable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Establishes the transaction-local INSTANCE RLS context for JDBC control-plane access. */
final class JdbcInstanceControlPlaneTransaction {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    JdbcInstanceControlPlaneTransaction(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    <T> T execute(Callable<T> work) {
        T result = transactions.execute(status -> {
            jdbc.queryForObject(
                    "select set_config('app.current_tenant_id','INSTANCE',true)",
                    String.class);
            try {
                return work.call();
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException("INSTANCE control-plane transaction failed", exception);
            }
        });
        return Objects.requireNonNull(result, "INSTANCE control-plane transaction returned null");
    }

    void execute(Runnable work) {
        execute(() -> {
            work.run();
            return Boolean.TRUE;
        });
    }
}
