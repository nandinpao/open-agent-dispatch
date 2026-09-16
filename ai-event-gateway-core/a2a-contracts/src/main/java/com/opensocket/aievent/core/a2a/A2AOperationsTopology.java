package com.opensocket.aievent.core.a2a;
import java.util.List;
public record A2AOperationsTopology(List<Node> nodes,List<Edge> edges){
 public A2AOperationsTopology{nodes=nodes==null?List.of():List.copyOf(nodes);edges=edges==null?List.of():List.copyOf(edges);}
 public record Node(String id,String type,String label,String status,String authority){}
 public record Edge(String source,String target,String relation,String status){}
}
