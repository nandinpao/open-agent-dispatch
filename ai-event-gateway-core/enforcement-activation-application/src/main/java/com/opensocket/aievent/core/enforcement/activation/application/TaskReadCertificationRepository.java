package com.opensocket.aievent.core.enforcement.activation.application;

import java.util.List;
import com.opensocket.aievent.core.enforcement.activation.contract.TaskReadCertificationEvidence;

public interface TaskReadCertificationRepository {
    TaskReadCertificationEvidence save(TaskReadCertificationEvidence evidence);
    List<TaskReadCertificationEvidence> recent(String tenantId, int limit);
}
