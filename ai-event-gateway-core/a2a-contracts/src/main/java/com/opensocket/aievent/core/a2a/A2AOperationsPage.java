package com.opensocket.aievent.core.a2a;
import java.util.List;
public record A2AOperationsPage(List<A2AOperationsListItem> items,long total,int offset,int limit,String nextCursor){
 public A2AOperationsPage{items=items==null?List.of():List.copyOf(items);}
}
