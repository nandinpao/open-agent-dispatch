package com.opensocket.aievent.database.persistence.integrationidentity.dao;
import java.time.OffsetDateTime; import java.util.List; import org.apache.ibatis.annotations.*;
import com.opensocket.aievent.database.persistence.integrationidentity.po.IntegrationWebhookEndpointPo;
@Mapper public interface IntegrationWebhookEndpointDao {
 int upsert(@Param("value")IntegrationWebhookEndpointPo value);
 IntegrationWebhookEndpointPo find(@Param("tenantId")String tenantId,@Param("endpointId")String endpointId);
 IntegrationWebhookEndpointPo findByTokenHash(@Param("tokenHash")String tokenHash);
 List<IntegrationWebhookEndpointPo> list(@Param("tenantId")String tenantId,@Param("connectionId")String connectionId,@Param("limit")int limit);
 int reserveNonce(@Param("tenantId")String tenantId,@Param("connectionId")String connectionId,@Param("endpointId")String endpointId,@Param("nonceHash")String nonceHash,@Param("expiresAt")OffsetDateTime expiresAt,@Param("correlationId")String correlationId);
 int deleteExpiredNonces(@Param("before")OffsetDateTime before,@Param("limit")int limit);
}
