package com.opensocket.aievent.database.persistence.a2a.dao;

import java.time.OffsetDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.opensocket.aievent.database.persistence.a2a.po.A2AIdempotencyPo;

@Mapper
public interface A2AIdempotencyDao {
    int claim(A2AIdempotencyPo value);
    A2AIdempotencyPo find(@Param("tenantId") String tenantId,
                          @Param("idempotencyKey") String idempotencyKey,
                          @Param("operationType") String operationType);
    int complete(@Param("tenantId") String tenantId,
                 @Param("idempotencyKey") String idempotencyKey,
                 @Param("operationType") String operationType,
                 @Param("resourceType") String resourceType,
                 @Param("resourceId") String resourceId,
                 @Param("completedAt") OffsetDateTime completedAt);
}
