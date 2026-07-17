package com.opensocket.aievent.database.persistence.task.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskType;
import com.opensocket.aievent.database.persistence.task.po.TaskPo;

class TaskPersistenceConverterTest {

    private final TaskPersistenceConverter converter =
            new TaskPersistenceConverter(JsonMapper.builder().build());

    @Test
    void convertsHistoricalBusinessTaskTypeWithoutEnumFailure() {
        TaskPo po = new TaskPo();
        po.setTaskType("CMS_CONTENT_REVIEW");

        TaskRecord task = converter.toTask(po);

        assertEquals(TaskType.INCIDENT_RESPONSE, task.getTaskType());
        assertEquals("CMS_CONTENT_REVIEW", task.getTaskTypeCode());
    }

    @Test
    void preservesExplicitBusinessTaskTypeCode() {
        TaskPo po = new TaskPo();
        po.setTaskType("INCIDENT_ESCALATION");
        po.setTaskTypeCode("CUSTOM_ESCALATION_REVIEW");

        TaskRecord task = converter.toTask(po);

        assertEquals(TaskType.INCIDENT_ESCALATION, task.getTaskType());
        assertEquals("CUSTOM_ESCALATION_REVIEW", task.getTaskTypeCode());
    }

    @Test
    void writesPlatformLifecycleTypeWhenOnlyBusinessCodeIsPresent() {
        TaskRecord task = new TaskRecord();
        task.setTaskTypeCode("CUSTOM_ANALYSIS");

        TaskPo po = converter.toPo(task);

        assertEquals("INCIDENT_RESPONSE", po.getTaskType());
        assertEquals("CUSTOM_ANALYSIS", po.getTaskTypeCode());
    }

    @Test
    void keepsNullBusinessCodeForKnownPlatformType() {
        TaskPo po = new TaskPo();
        po.setTaskType("INCIDENT_RESPONSE");

        TaskRecord task = converter.toTask(po);

        assertEquals(TaskType.INCIDENT_RESPONSE, task.getTaskType());
        assertNull(task.getTaskTypeCode());
    }
}
