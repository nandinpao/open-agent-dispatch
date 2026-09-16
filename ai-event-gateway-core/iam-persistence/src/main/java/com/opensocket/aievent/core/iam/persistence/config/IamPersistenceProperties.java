package com.opensocket.aievent.core.iam.persistence.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("aeg.iam.persistence")
public class IamPersistenceProperties {
    private int departmentMaxDepth = 10;
    private boolean roleValidationEnabled;
    private String expectedRuntimeRole = "opendispatch_runtime";
    public int getDepartmentMaxDepth(){return departmentMaxDepth;}
    public void setDepartmentMaxDepth(int value){if(value<1||value>10)throw new IllegalArgumentException("departmentMaxDepth must be 1..10");departmentMaxDepth=value;}
    public boolean isRoleValidationEnabled(){return roleValidationEnabled;}
    public void setRoleValidationEnabled(boolean value){roleValidationEnabled=value;}
    public String getExpectedRuntimeRole(){return expectedRuntimeRole;}
    public void setExpectedRuntimeRole(String value){expectedRuntimeRole=value;}
}
