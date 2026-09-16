package com.opensocket.aievent.core.a2a.application.port.in;
import java.util.List;
import com.opensocket.aievent.core.a2a.*;
/** Read-only inbound boundary for the A2A Operations Workspace. */
public interface A2AOperationsWorkspaceUseCase {
    A2AOperationsPage searchPage(A2AOperationsSearchQuery query);
    default List<A2AOperationsListItem> search(A2AOperationsSearchQuery query){return searchPage(query).items();}
    A2AOperationsDetail detail(String tenantId,String requestId);
}
