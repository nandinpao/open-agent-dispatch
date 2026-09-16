package com.opensocket.aievent.core.iam.rbac.domain;
public record RoleId(String value) { public RoleId { if(value==null||value.isBlank())throw new IllegalArgumentException("roleId is required"); value=value.trim(); } @Override public String toString(){return value;} }
