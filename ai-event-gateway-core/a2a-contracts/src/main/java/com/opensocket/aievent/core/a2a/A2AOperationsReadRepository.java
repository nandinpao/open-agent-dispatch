package com.opensocket.aievent.core.a2a;
import java.util.List;
/** Optimized persistence projection used by the operations workspace. */
public interface A2AOperationsReadRepository {
    List<A2AOperationsListItem> search(A2AOperationsSearchQuery query);
    long count(A2AOperationsSearchQuery query);
    default A2AOperationsPage searchPage(A2AOperationsSearchQuery query){
        return new A2AOperationsPage(search(query),count(query),query.normalizedOffset(),query.cappedLimit(),null);
    }
    String mode();
}
