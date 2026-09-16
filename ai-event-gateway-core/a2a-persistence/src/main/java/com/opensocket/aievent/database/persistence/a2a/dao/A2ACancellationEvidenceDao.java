package com.opensocket.aievent.database.persistence.a2a.dao;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.opensocket.aievent.database.persistence.a2a.po.A2ACancellationEvidencePo;

@Mapper
public interface A2ACancellationEvidenceDao {
    int insert(@Param("value") A2ACancellationEvidencePo value);
    List<A2ACancellationEvidencePo> findByCancellation(@Param("tenantId") String tenantId,
                                                        @Param("cancellationId") String cancellationId,
                                                        @Param("limit") int limit);
    A2ACancellationEvidencePo findByEventKey(@Param("tenantId") String tenantId,
                                              @Param("eventKey") String eventKey);
}
