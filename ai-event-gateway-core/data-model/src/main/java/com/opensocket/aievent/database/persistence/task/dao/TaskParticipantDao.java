package com.opensocket.aievent.database.persistence.task.dao;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.opensocket.aievent.database.persistence.task.po.TaskParticipantPo;

@Mapper
public interface TaskParticipantDao {
    int insert(@Param("participant") TaskParticipantPo participant);
    TaskParticipantPo findById(@Param("tenantId") String tenantId, @Param("participantId") String participantId);
    TaskParticipantPo findNatural(@Param("tenantId") String tenantId, @Param("taskId") String taskId, @Param("participantType") String participantType, @Param("participantRefId") String participantRefId, @Param("participantRole") String participantRole);
    List<TaskParticipantPo> findByTask(@Param("tenantId") String tenantId, @Param("taskId") String taskId, @Param("limit") int limit);
    int delete(@Param("tenantId") String tenantId, @Param("participantId") String participantId);
}
