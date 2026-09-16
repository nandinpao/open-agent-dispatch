package com.opensocket.aievent.database.persistence.governance.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.opensocket.aievent.database.persistence.governance.po.ApiMutationReceiptPo;
import com.opensocket.aievent.database.persistence.governance.po.AuditEvidencePo;
import com.opensocket.aievent.database.persistence.governance.po.AuthorizationDecisionPo;

@Mapper
public interface AuditEvidenceDao {
    int insertDecision(@Param("value") AuthorizationDecisionPo value);
    AuthorizationDecisionPo findDecision(@Param("tenantId") String tenantId,
                                         @Param("decisionId") String decisionId);
    int insertEvidence(@Param("value") AuditEvidencePo value);
    List<AuditEvidencePo> listEvidence(@Param("tenantId") String tenantId,
                                      @Param("aggregateType") String aggregateType,
                                      @Param("aggregateId") String aggregateId,
                                      @Param("limit") int limit);
    int insertReceipt(@Param("value") ApiMutationReceiptPo value);
    int updateReceipt(@Param("value") ApiMutationReceiptPo value);
    ApiMutationReceiptPo findReceipt(@Param("tenantId") String tenantId,
                                     @Param("receiptId") String receiptId);
    ApiMutationReceiptPo findReceiptByIdempotency(@Param("tenantId") String tenantId,
                                                  @Param("requestMethod") String requestMethod,
                                                  @Param("requestPath") String requestPath,
                                                  @Param("idempotencyKey") String idempotencyKey);
}
