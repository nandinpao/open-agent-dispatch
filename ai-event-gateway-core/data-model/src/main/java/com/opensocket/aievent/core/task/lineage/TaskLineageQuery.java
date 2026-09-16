package com.opensocket.aievent.core.task.lineage;

/** Tenant is supplied by authenticated request context, never by caller-controlled cross-tenant input. */
public class TaskLineageQuery {
    private String tenantId; private String rootTaskId; private String taskId; private String agentId;
    private String correlationId; private String principalType; private String principalId; private String credentialId;
    private String departmentId; private String groupId; private TaskFailureDomain failureDomain; private int limit=250;
    public String getTenantId(){return tenantId;} public void setTenantId(String v){tenantId=v;}
    public String getRootTaskId(){return rootTaskId;} public void setRootTaskId(String v){rootTaskId=v;}
    public String getTaskId(){return taskId;} public void setTaskId(String v){taskId=v;}
    public String getAgentId(){return agentId;} public void setAgentId(String v){agentId=v;}
    public String getCorrelationId(){return correlationId;} public void setCorrelationId(String v){correlationId=v;}
    public String getPrincipalType(){return principalType;} public void setPrincipalType(String v){principalType=v;}
    public String getPrincipalId(){return principalId;} public void setPrincipalId(String v){principalId=v;}
    public String getCredentialId(){return credentialId;} public void setCredentialId(String v){credentialId=v;}
    public String getDepartmentId(){return departmentId;} public void setDepartmentId(String v){departmentId=v;}
    public String getGroupId(){return groupId;} public void setGroupId(String v){groupId=v;}
    public TaskFailureDomain getFailureDomain(){return failureDomain;} public void setFailureDomain(TaskFailureDomain v){failureDomain=v;}
    public int getLimit(){return Math.max(1,Math.min(limit,1000));} public void setLimit(int v){limit=v;}
}
