package com.opensocket.aievent.database.persistence.a2a.dao;

import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.opensocket.aievent.database.persistence.a2a.po.A2ACancellationPo;

@Mapper
public interface A2ACancellationDao {
    int upsert(@Param("value") A2ACancellationPo value);
    int updateExpectedVersion(@Param("value") A2ACancellationPo value,
                              @Param("expectedVersion") long expectedVersion);
    A2ACancellationPo findById(@Param("tenantId") String tenantId,
                               @Param("cancellationId") String cancellationId);
    A2ACancellationPo findByRequest(@Param("tenantId") String tenantId,
                                    @Param("requestId") String requestId);
    A2ACancellationPo findByIdempotencyKey(@Param("tenantId") String tenantId,
                                           @Param("idempotencyKey") String idempotencyKey);
    List<A2ACancellationPo> findDue(@Param("now") OffsetDateTime now,@Param("limit") int limit);
    List<A2ACancellationPo> findRecent(@Param("tenantId") String tenantId,@Param("limit") int limit);
}
