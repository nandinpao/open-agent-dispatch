package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;

/** A0-R7 execution ownership authority. Fencing protects OpenDispatch ownership, not remote side effects. */
public record ExecutionLeaseV206(
        String leaseId,String tenantId,String taskId,String planId,int planRevision,String stepId,String assignmentId,
        String ownerNodeId,long fencingToken,OffsetDateTime leaseUntil,String status,OffsetDateTime acquiredAt,
        OffsetDateTime releasedAt,String releaseReason,long version) {}
