package com.opensocket.aievent.database.persistence.adapter.dao;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.opensocket.aievent.database.persistence.adapter.po.AdapterExecutorAuditPo;

@Mapper
public interface AdapterExecutorAuditDao {
    int insertIgnore(@Param("audit") AdapterExecutorAuditPo audit);
    List<AdapterExecutorAuditPo> recent(@Param("limit") int limit);
    List<AdapterExecutorAuditPo> findByActionId(@Param("actionId") String actionId, @Param("limit") int limit);
}
