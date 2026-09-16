package com.opensocket.aievent.database.persistence.integrationidentity.po;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class IntegrationPrincipalScopePo {
    private String tenantId;
    private String principalId;
    private String isolationMode;
    private String scopeReference;
    private String allowedProjectIdsJson;
    private String allowedOperationsJson;
    private String allowedIssueTypesJson;
    private boolean productionAllowed;
    private long version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
