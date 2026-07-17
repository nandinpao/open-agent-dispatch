package com.opensocket.aievent.database.persistence.task.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import com.opensocket.aievent.core.assignment.AssignmentStatus;
import com.opensocket.aievent.core.assignment.TaskAssignment;
import com.opensocket.aievent.core.assignment.TaskAssignmentRepository;
import com.opensocket.aievent.database.persistence.task.dao.TaskAssignmentDao;
import com.opensocket.aievent.database.persistence.task.po.TaskAssignmentPo;
import com.opensocket.aievent.database.persistence.task.converter.TaskAssignmentPersistenceConverter;


@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix="assignment", name="store", havingValue="MYBATIS")
public class MybatisTaskAssignmentRepository implements TaskAssignmentRepository {
    private final TaskAssignmentDao dao;
    private final TaskAssignmentPersistenceConverter converter;

    public MybatisTaskAssignmentRepository(TaskAssignmentDao dao, TaskAssignmentPersistenceConverter converter) {
        this.dao = dao;
        this.converter = converter;
    }

    public TaskAssignment save(TaskAssignment assignment){dao.upsert(converter.toPo(assignment));return assignment;}

    public Optional<TaskAssignment> findById(String id){return Optional.ofNullable(dao.findById(id)).map(converter::toDomain);}

    public Optional<TaskAssignment> findOpenByTaskId(String taskId){return Optional.ofNullable(dao.findOpenByTaskId(taskId)).map(converter::toDomain);}

    public boolean releaseCapacityReservation(String id,OffsetDateTime at){return dao.releaseCapacityReservation(id,at)==1;}

    public List<TaskAssignment> findByTaskId(String taskId,int limit){return dao.findByTaskId(taskId,cap(limit)).stream().map(converter::toDomain).toList();}

    public List<TaskAssignment> recent(int limit){return dao.recent(cap(limit)).stream().map(converter::toDomain).toList();}

    public String mode(){return "MYBATIS";}

    private int cap(int n){return Math.max(1,Math.min(n,1000));}
}
