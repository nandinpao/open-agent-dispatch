package com.opensocket.aievent.core.capability;

import java.util.LinkedHashMap;
import java.util.Map;

/** Small tolerant reader for A2A v1 HTTP+JSON response and StreamResponse shapes. */
final class A2AProtocolObjects {
    private A2AProtocolObjects(){}
    @SuppressWarnings("unchecked") static Map<String,Object> task(Map<String,Object> body){
        if(body==null)return Map.of(); Object t=body.get("task"); if(t instanceof Map<?,?> m)return (Map<String,Object>)(Map<?,?>)m;
        if(body.containsKey("id")&&body.containsKey("status"))return body;
        Object statusUpdate=body.get("statusUpdate"); if(statusUpdate instanceof Map<?,?> sm){Map<String,Object> out=new LinkedHashMap<>();out.put("id",sm.get("taskId"));out.put("contextId",sm.get("contextId"));out.put("status",sm.get("status"));return out;}
        return Map.of();
    }
    @SuppressWarnings("unchecked") static String state(Map<String,Object> task){Object status=task.get("status");if(status instanceof Map<?,?> m){Object s=m.get("state");return s==null?null:String.valueOf(s);}return null;}
    static String id(Map<String,Object> task){Object v=task.get("id");return v==null?null:String.valueOf(v);}
    static String contextId(Map<String,Object> task){Object v=task.get("contextId");return v==null?null:String.valueOf(v);}
    static boolean terminal(String state){return "TASK_STATE_COMPLETED".equals(state)||"TASK_STATE_FAILED".equals(state)||"TASK_STATE_CANCELED".equals(state)||"TASK_STATE_REJECTED".equals(state);}
    static boolean success(String state){return "TASK_STATE_COMPLETED".equals(state);}
    static boolean interrupted(String state){return "TASK_STATE_INPUT_REQUIRED".equals(state)||"TASK_STATE_AUTH_REQUIRED".equals(state);}
}
