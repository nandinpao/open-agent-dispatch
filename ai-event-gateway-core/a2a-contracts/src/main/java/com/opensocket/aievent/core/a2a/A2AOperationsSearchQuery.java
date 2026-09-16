package com.opensocket.aievent.core.a2a;

/** Server-side list filters for the A2A Operations Workspace. */
public record A2AOperationsSearchQuery(
        String tenantId, String requestStatus, String blockerCode, String operationalStage,
        String sourceDomainId, String targetDomainId, String text, boolean blockerOnly,
        int offset, int limit, String sortBy, String sortDirection) {
    public A2AOperationsSearchQuery(String tenantId,String requestStatus,String blockerCode,String text,int limit){
        this(tenantId,requestStatus,blockerCode,null,null,null,text,false,0,limit,"updatedAt","DESC");
    }
    public int cappedLimit(){return Math.max(1,Math.min(limit<=0?50:limit,200));}
    public int normalizedOffset(){return Math.max(0,offset);}
    public String normalizedSortBy(){return switch(sortBy==null?"":sortBy){case "createdAt"->"createdAt";case "requestStatus"->"requestStatus";default->"updatedAt";};}
    public String normalizedSortDirection(){return "ASC".equalsIgnoreCase(sortDirection)?"ASC":"DESC";}
}
