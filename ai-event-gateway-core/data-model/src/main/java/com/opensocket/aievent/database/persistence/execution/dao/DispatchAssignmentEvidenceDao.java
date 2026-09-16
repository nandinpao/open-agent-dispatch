package com.opensocket.aievent.database.persistence.execution.dao;
import java.util.List;
import org.apache.ibatis.annotations.Mapper; import org.apache.ibatis.annotations.Param;
import com.opensocket.aievent.database.persistence.execution.po.DispatchAssignmentEvidencePo;
@Mapper public interface DispatchAssignmentEvidenceDao {
 int insert(@Param("evidence") DispatchAssignmentEvidencePo evidence);
 List<DispatchAssignmentEvidencePo> findByDispatchRequest(@Param("dispatchRequestId") String dispatchRequestId,@Param("limit") int limit);
 DispatchAssignmentEvidencePo findEvent(@Param("dispatchRequestId") String dispatchRequestId,@Param("attemptNo") int attemptNo,@Param("eventType") String eventType);
 int countEvent(@Param("dispatchRequestId") String dispatchRequestId,@Param("attemptNo") int attemptNo,@Param("eventType") String eventType);
}
