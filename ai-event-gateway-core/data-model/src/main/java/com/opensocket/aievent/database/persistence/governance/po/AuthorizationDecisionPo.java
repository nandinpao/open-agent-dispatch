package com.opensocket.aievent.database.persistence.governance.po; import java.time.OffsetDateTime; import lombok.*;
@Getter @Setter @NoArgsConstructor public class AuthorizationDecisionPo {private String tenantId,decisionId,permissionPoint,resourceType,resourceId,actorType,actorId,decision,reasonCode,evaluatedScopesJson,correlationId;private OffsetDateTime decidedAt;}
