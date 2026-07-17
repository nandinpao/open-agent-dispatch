package com.opensocket.aievent.database.persistence.issue.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.opensocket.aievent.core.issue.TaskIssueLink;
import com.opensocket.aievent.core.issue.TaskIssueLinkRepository;
import com.opensocket.aievent.database.persistence.issue.converter.TaskIssueLinkPersistenceConverter;
import com.opensocket.aievent.database.persistence.issue.dao.TaskIssueLinkDao;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix = "task-issue-links", name = "store", havingValue = "MYBATIS")
public class MybatisTaskIssueLinkRepository implements TaskIssueLinkRepository {
    private final TaskIssueLinkDao dao;
    private final TaskIssueLinkPersistenceConverter converter;

    public MybatisTaskIssueLinkRepository(TaskIssueLinkDao dao, TaskIssueLinkPersistenceConverter converter) {
        this.dao = dao;
        this.converter = converter;
    }

    @Override
    public TaskIssueLink save(TaskIssueLink link) {
        if (link == null || link.getTaskId() == null || link.getTaskId().isBlank()) {
            throw new IllegalArgumentException("task issue link with taskId is required");
        }
        dao.upsert(converter.toPo(link));
        return link;
    }

    @Override
    public Optional<TaskIssueLink> findByTaskId(String taskId) {
        if (taskId == null || taskId.isBlank()) return Optional.empty();
        return Optional.ofNullable(dao.findByTaskId(taskId)).map(converter::toDomain);
    }

    @Override
    public List<TaskIssueLink> findByTaskIds(List<String> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) return List.of();
        List<String> safeIds = taskIds.stream().filter(id -> id != null && !id.isBlank()).distinct().limit(1000).toList();
        if (safeIds.isEmpty()) return List.of();
        return dao.findByTaskIds(safeIds).stream().map(converter::toDomain).toList();
    }

    @Override
    public List<TaskIssueLink> recent(int limit) {
        return dao.recent(Math.max(1, Math.min(limit, 1000))).stream().map(converter::toDomain).toList();
    }

    @Override
    public String mode() {
        return "MYBATIS";
    }
}
