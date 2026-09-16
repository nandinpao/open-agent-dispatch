package com.opensocket.aievent.core.iam.api.pagination;
import java.time.Instant;
public record CursorPayload(String tenantId,String resource,String sortField,String sortDirection,String lastSortValue,
                            String lastUniqueId,String filterHash,Instant expiresAt) { }
