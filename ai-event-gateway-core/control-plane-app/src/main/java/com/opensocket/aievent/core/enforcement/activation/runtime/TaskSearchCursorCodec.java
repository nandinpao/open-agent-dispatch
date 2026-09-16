package com.opensocket.aievent.core.enforcement.activation.runtime;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import com.opensocket.aievent.core.task.TaskQuery;
import com.opensocket.aievent.core.task.TaskRecord;

/** Opaque deterministic cursor for (created_at desc, task_id desc) keyset pagination. */
public final class TaskSearchCursorCodec {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    public void apply(String cursor, TaskQuery query) {
        if (cursor == null || cursor.isBlank()) return;
        try {
            String value = new String(DECODER.decode(cursor.trim()), StandardCharsets.UTF_8);
            int separator = value.indexOf('|');
            if (separator < 1 || separator == value.length() - 1) throw new IllegalArgumentException("invalid cursor");
            OffsetDateTime createdAt = OffsetDateTime.parse(value.substring(0, separator));
            String taskId = value.substring(separator + 1).trim();
            if (taskId.isEmpty() || taskId.length() > 128) throw new IllegalArgumentException("invalid taskId");
            query.setBeforeCreatedAt(createdAt);
            query.setBeforeTaskId(taskId);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid Task search cursor", exception);
        }
    }

    public String nextCursor(java.util.List<TaskRecord> tasks, int limit) {
        if (tasks == null || tasks.size() < Math.max(1, limit)) return "";
        TaskRecord last = tasks.getLast();
        if (last.getCreatedAt() == null || last.getTaskId() == null || last.getTaskId().isBlank()) return "";
        String raw = last.getCreatedAt() + "|" + last.getTaskId();
        return ENCODER.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
