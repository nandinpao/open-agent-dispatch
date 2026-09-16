package com.opensocket.aievent.core.iam.runtime.projection;

import com.opensocket.aievent.core.iam.api.application.port.IamMachineOwnershipAdministrationPort;
import com.opensocket.aievent.core.iam.api.response.MachineOwnershipTransferResponse;
import com.opensocket.aievent.core.iam.persistence.dao.IamApiRuntimeDao;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import java.time.Instant;
import java.util.Map;
import org.springframework.transaction.support.TransactionTemplate;

/** Executes ownership transfer under the same tenant-scoped transaction authority as IAM projections. */
public final class MybatisIamMachineOwnershipAdministrationAdapter implements IamMachineOwnershipAdministrationPort {
    private final IamApiRuntimeDao dao;
    private final TransactionTemplate transactions;

    public MybatisIamMachineOwnershipAdministrationAdapter(IamApiRuntimeDao dao, TransactionTemplate transactions) {
        this.dao = dao;
        this.transactions = transactions;
    }

    @Override
    public MachineOwnershipTransferResponse transfer(
            String tenantId, String fromUserId, String toUserId, String actorId, String reason) {
        Map<String,Object> row = IamTenantContextHolder.withContext(
                new IamTenantExecutionContext(tenantId, actorId),
                () -> transactions.execute(status -> dao.transferMachineOwnership(
                        tenantId, fromUserId, toUserId, actorId, reason)));
        if (row == null) throw new IllegalStateException("MACHINE_OWNERSHIP_TRANSFER_FAILED");
        return new MachineOwnershipTransferResponse(
                string(row.get("transferId")), tenantId, fromUserId, toUserId,
                number(row.get("serviceAccountsUpdated")),
                number(row.get("agentBusinessOwnersUpdated")),
                number(row.get("agentTechnicalStewardsUpdated")),
                actorId, reason, instant(row.get("transferredAt")));
    }

    private static String string(Object value) { return value == null ? "" : value.toString(); }
    private static long number(Object value) { return value instanceof Number n ? n.longValue() : Long.parseLong(string(value)); }
    private static Instant instant(Object value) {
        if (value instanceof Instant i) return i;
        if (value instanceof java.time.OffsetDateTime o) return o.toInstant();
        return Instant.parse(string(value));
    }
}
