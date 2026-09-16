package com.opensocket.aievent.core.issuetracking.core;
import java.nio.charset.StandardCharsets; import java.security.MessageDigest; import java.util.HexFormat;
public final class RelaySourceMarker { private RelaySourceMarker(){}
 public static String create(String tenantId,String topologyId,String edgeId){return "opendispatch-relay:v1:"+digest(tenantId+"|"+topologyId+"|"+edgeId);}
 public static boolean isOpenDispatchMarker(String value){return value!=null&&value.startsWith("opendispatch-relay:v1:");}
 private static String digest(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))).substring(0,32);}catch(Exception ex){throw new IllegalStateException(ex);}}
}
