package com.opensocket.aievent.database.persistence.issuepolicy.dao;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.opensocket.aievent.database.persistence.issuepolicy.po.IssuePolicyDecisionPo;

@Mapper
public interface IssuePolicyDecisionDao {
    int insert(@Param("value") IssuePolicyDecisionPo value);
    int updateCas(@Param("value") IssuePolicyDecisionPo value,@Param("expectedVersion") long expectedVersion);
    IssuePolicyDecisionPo find(@Param("tenantId") String tenantId,@Param("decisionId") String decisionId);
    IssuePolicyDecisionPo findByTaskAndPurpose(@Param("tenantId") String tenantId,@Param("taskId") String taskId,@Param("projectionPurpose") String projectionPurpose);
    List<IssuePolicyDecisionPo> listByTask(@Param("tenantId") String tenantId,@Param("taskId") String taskId,@Param("limit") int limit);
}
