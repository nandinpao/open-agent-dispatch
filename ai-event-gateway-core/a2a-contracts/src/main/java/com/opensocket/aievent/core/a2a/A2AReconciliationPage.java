package com.opensocket.aievent.core.a2a;
import java.util.List;
public record A2AReconciliationPage(List<A2AReconciliationCase> items,long total,int offset,int limit){
 public A2AReconciliationPage{items=items==null?List.of():List.copyOf(items);}
}
