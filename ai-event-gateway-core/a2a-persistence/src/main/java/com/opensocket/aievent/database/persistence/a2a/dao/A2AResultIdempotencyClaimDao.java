package com.opensocket.aievent.database.persistence.a2a.dao;
import org.apache.ibatis.annotations.*; import com.opensocket.aievent.database.persistence.a2a.po.A2AResultIdempotencyClaimPo;
@Mapper public interface A2AResultIdempotencyClaimDao {
 int insert(@Param("claim") A2AResultIdempotencyClaimPo claim);
 A2AResultIdempotencyClaimPo findByIdempotencyKey(@Param("tenantId")String tenantId,@Param("idempotencyKey")String idempotencyKey);
}
