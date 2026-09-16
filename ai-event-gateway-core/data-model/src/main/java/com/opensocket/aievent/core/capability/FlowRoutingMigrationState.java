package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;

/** Per-Flow R6 migration state. NEW_AUTHORITATIVE is intentionally not a valid R6 value. */
public record FlowRoutingMigrationState(
        String tenantId,String flowId,String migrationState,int version,String changeReason,String changedBy,OffsetDateTime changedAt) {}
