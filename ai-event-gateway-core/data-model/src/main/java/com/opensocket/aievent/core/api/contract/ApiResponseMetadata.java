package com.opensocket.aievent.core.api.contract;
import java.util.List;
public record ApiResponseMetadata(Long resourceVersion,String correlationId,String authorizationDecisionId,ApiSyncStatus syncStatus,List<ApiWarning> warnings) {
 public ApiResponseMetadata { warnings=warnings==null?List.of():List.copyOf(warnings); syncStatus=syncStatus==null?ApiSyncStatus.NOT_APPLICABLE:syncStatus; }
}
