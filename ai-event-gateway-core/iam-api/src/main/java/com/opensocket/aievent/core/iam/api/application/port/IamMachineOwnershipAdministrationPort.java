package com.opensocket.aievent.core.iam.api.application.port;

import com.opensocket.aievent.core.iam.api.response.MachineOwnershipTransferResponse;

/** Canonical command boundary for accountable machine/Agent ownership lifecycle operations. */
public interface IamMachineOwnershipAdministrationPort {
    MachineOwnershipTransferResponse transfer(
            String tenantId, String fromUserId, String toUserId, String actorId, String reason);
}
