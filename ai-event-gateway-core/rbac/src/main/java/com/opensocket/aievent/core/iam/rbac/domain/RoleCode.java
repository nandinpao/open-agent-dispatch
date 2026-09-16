package com.opensocket.aievent.core.iam.rbac.domain;

import java.util.Locale;
import java.util.regex.Pattern;
public record RoleCode(String value) {
    private static final Pattern PATTERN=Pattern.compile("[A-Z][A-Z0-9_]{1,63}");
    public RoleCode { if(value==null||value.isBlank())throw new IllegalArgumentException("roleCode is required"); value=value.trim().toUpperCase(Locale.ROOT); if(!PATTERN.matcher(value).matches())throw new IllegalArgumentException("invalid roleCode"); }
    @Override public String toString(){return value;}
}
