package com.opensocket.aievent.core.a2a;
public record A2AReconciliationSearchQuery(
        String tenantId, String status, String caseType, String authorityOwner,
        String requestId, String text, int offset, int limit, String sortDirection) {
    public int cappedLimit(){ return Math.max(1,Math.min(limit<=0?50:limit,200)); }
    public int normalizedOffset(){ return Math.max(0,offset); }
    public String normalizedSortDirection(){ return "ASC".equalsIgnoreCase(sortDirection)?"ASC":"DESC"; }
}
