package com.opensocket.aievent.core.iam.rbac.domain.entrypoint;
import java.util.Map;
public record EntryPointBurnDownSummary(long total,Map<String,Long> byState,long unknownPermissions,long missingMappings,
 long missingResolvers,long overdue,long activeBypasses,long expiredBypasses,long cutoverBlockers) {}
