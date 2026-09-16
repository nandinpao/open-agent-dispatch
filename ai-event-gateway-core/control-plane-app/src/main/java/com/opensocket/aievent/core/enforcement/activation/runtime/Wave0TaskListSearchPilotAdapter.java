package com.opensocket.aievent.core.enforcement.activation.runtime;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.opensocket.aievent.core.enforcement.activation.application.Wave0ReadPilotService;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0CanonicalPayload;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotEntryPoint;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotMismatchCategory;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotResponse;
import com.opensocket.aievent.core.resourceaccess.contract.TaskScopeQueryPlan;
import com.opensocket.aievent.core.resourceaccess.contract.TaskScopeQueryPlanPort;
import com.opensocket.aievent.core.resourceaccess.contract.VisibilityLevel;
import com.opensocket.aievent.core.task.TaskOperationalQuery;
import com.opensocket.aievent.core.task.TaskQuery;
import com.opensocket.aievent.core.task.TaskRecord;

/** Phase 6C-2 certified Task List/Search Shadow and low-percentage Canary adapter. */
@Component
@ConditionalOnProperty(prefix = "aeg.enforcement-activation", name = "task-list-search-pilot-enabled", havingValue = "true")
public final class Wave0TaskListSearchPilotAdapter {
    private final Wave0ReadPilotService pilot;
    private final TaskOperationalQuery tasks;
    private final TaskScopeQueryPlanPort scopes;
    private final TaskRecordVisibilityProjector projector;

    public Wave0TaskListSearchPilotAdapter(
            Wave0ReadPilotService pilot,
            TaskOperationalQuery tasks,
            TaskScopeQueryPlanPort scopes,
            TaskRecordVisibilityProjector projector) {
        this.pilot = Objects.requireNonNull(pilot, "pilot");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.scopes = Objects.requireNonNull(scopes, "scopes");
        this.projector = Objects.requireNonNull(projector, "projector");
    }

    public Wave0ReadPilotResponse<TaskListPayload> search(TaskQuery query, Wave0ReadPilotService.ActorContext actor) {
        TaskQuery safeQuery = query == null ? new TaskQuery() : query;
        TaskScopeQueryPlan plan = scopes.build("task.read", VisibilityLevel.SUMMARY, "WAVE0_TASK_LIST_SEARCH");
        return pilot.execute(
                Wave0ReadPilotEntryPoint.TASK_LIST_SEARCH,
                actor,
                () -> payload(tasks.searchTasks(safeQuery, plan), plan.maximumVisibility()),
                () -> payload(tasks.searchTasksTarget(safeQuery, plan), plan.maximumVisibility()));
    }

    private TaskListPayload payload(List<TaskRecord> values, VisibilityLevel visibility) {
        return new TaskListPayload(projector.project(values, visibility));
    }

    public record TaskListPayload(List<TaskRecord> tasks) implements Wave0CanonicalPayload {
        public TaskListPayload { tasks = tasks == null ? List.of() : List.copyOf(tasks); }

        @Override public String canonicalValue() {
            return tasks.stream().map(TaskListPayload::canonicalTask).reduce("", (left, right) -> left + right);
        }

        @Override public Wave0ReadPilotMismatchCategory compareTarget(Wave0CanonicalPayload target) {
            if (!(target instanceof TaskListPayload other)) return Wave0ReadPilotMismatchCategory.PAYLOAD_MISMATCH;
            List<String> legacyIds = tasks.stream().map(TaskRecord::getTaskId).toList();
            List<String> targetIds = other.tasks.stream().map(TaskRecord::getTaskId).toList();
            if (!legacyIds.equals(targetIds)) {
                if (legacyIds.size() == targetIds.size() && legacyIds.containsAll(targetIds)) {
                    return Wave0ReadPilotMismatchCategory.ORDER_MISMATCH;
                }
                if (!legacyIds.containsAll(targetIds)) return Wave0ReadPilotMismatchCategory.TARGET_SCOPE_EXPANSION;
                return Wave0ReadPilotMismatchCategory.PAYLOAD_MISMATCH;
            }
            Map<String, String> legacy = canonicalById(tasks);
            Map<String, String> candidate = canonicalById(other.tasks);
            return legacy.equals(candidate) ? Wave0ReadPilotMismatchCategory.MATCH
                    : Wave0ReadPilotMismatchCategory.PAYLOAD_MISMATCH;
        }

        private static Map<String, String> canonicalById(List<TaskRecord> tasks) {
            Map<String, String> values = new HashMap<>();
            tasks.forEach(task -> values.put(task.getTaskId(), canonicalTask(task)));
            return values;
        }

        private static String canonicalTask(TaskRecord task) {
            return "[" + text(task.getTaskId()) + "|" + text(task.getTitle()) + "|"
                    + text(task.getStatus()) + "|" + text(task.getPriority()) + "|"
                    + text(task.getSeverity()) + "|" + text(task.getTenantId()) + "|"
                    + text(task.getSensitivityLevel()) + "|" + time(task.getCreatedAt()) + "|"
                    + time(task.getUpdatedAt()) + "]";
        }

        private static String text(Object value) { return value == null ? "" : value.toString().replace("|", "\\|"); }
        private static String time(OffsetDateTime value) { return value == null ? "" : value.toInstant().toString(); }
    }
}
