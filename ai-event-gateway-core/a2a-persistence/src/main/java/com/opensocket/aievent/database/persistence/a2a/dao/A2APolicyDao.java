package com.opensocket.aievent.database.persistence.a2a.dao;

import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.opensocket.aievent.core.a2a.A2APolicyVisibilityScope;
import com.opensocket.aievent.database.persistence.a2a.po.A2APolicyPo;

@Mapper
public interface A2APolicyDao {
    int upsert(@Param("policy") A2APolicyPo policy);
    A2APolicyPo findById(@Param("tenantId") String tenantId, @Param("policyId") String policyId);
    List<A2APolicyPo> search(@Param("tenantId") String tenantId,
                             @Param("sourceDomainId") String sourceDomainId,
                             @Param("targetDomainId") String targetDomainId,
                             @Param("limit") int limit);
    List<A2APolicyPo> searchScoped(@Param("tenantId") String tenantId,
                                   @Param("sourceDomainId") String sourceDomainId,
                                   @Param("targetDomainId") String targetDomainId,
                                   @Param("limit") int limit,
                                   @Param("scope") A2APolicyVisibilityScope scope);
    List<A2APolicyPo> findDirectional(@Param("tenantId") String tenantId,
                                      @Param("sourceDomainId") String sourceDomainId,
                                      @Param("targetDomainId") String targetDomainId,
                                      @Param("at") OffsetDateTime at);
}
