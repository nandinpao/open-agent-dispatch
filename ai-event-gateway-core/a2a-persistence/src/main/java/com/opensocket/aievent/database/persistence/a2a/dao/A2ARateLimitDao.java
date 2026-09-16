package com.opensocket.aievent.database.persistence.a2a.dao;
import java.time.OffsetDateTime; import org.apache.ibatis.annotations.Mapper; import org.apache.ibatis.annotations.Param;
@Mapper public interface A2ARateLimitDao { int increment(@Param("tenantId")String tenantId,@Param("policyId")String policyId,@Param("windowStartedAt")OffsetDateTime windowStartedAt); Integer count(@Param("tenantId")String tenantId,@Param("policyId")String policyId,@Param("windowStartedAt")OffsetDateTime windowStartedAt); }
