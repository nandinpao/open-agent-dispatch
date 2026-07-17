package com.opensocket.aievent.database.persistence.execution.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.opensocket.aievent.database.persistence.execution.po.RecoveryApprovalRequestPo;

@Mapper
public interface RecoveryApprovalRequestDao {
    int upsert(@Param("record") RecoveryApprovalRequestPo record);
    RecoveryApprovalRequestPo findById(@Param("approvalId") String approvalId);
    List<RecoveryApprovalRequestPo> findByStatus(@Param("status") String status, @Param("limit") int limit);
    List<RecoveryApprovalRequestPo> recent(@Param("limit") int limit);
}
