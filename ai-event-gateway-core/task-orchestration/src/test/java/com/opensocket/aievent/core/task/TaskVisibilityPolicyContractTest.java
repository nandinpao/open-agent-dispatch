package com.opensocket.aievent.core.task;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TaskVisibilityPolicyContractTest {

    @Test
    void taskRecordAcceptsOnlyCanonicalVisibilityVocabulary() {
        TaskRecord task = new TaskRecord();
        for (String allowed : new String[]{"PRIVATE", "PARTICIPANTS", "DEPARTMENT", "GROUP", "TENANT"}) {
            task.setVisibilityPolicy(allowed.toLowerCase());
            assertEquals(allowed, task.getVisibilityPolicy());
        }
    }

    @Test
    void restrictedIsSensitivityVocabularyNotTaskVisibilityPolicy() {
        TaskRecord task = new TaskRecord();
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> task.setVisibilityPolicy("RESTRICTED"));
        assertTrue(failure.getMessage().contains("TASK_VISIBILITY_POLICY_INVALID"));
    }
}
