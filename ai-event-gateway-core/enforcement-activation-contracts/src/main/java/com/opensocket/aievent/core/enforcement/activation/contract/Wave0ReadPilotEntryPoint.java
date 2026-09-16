package com.opensocket.aievent.core.enforcement.activation.contract;

public enum Wave0ReadPilotEntryPoint {
    ENFORCEMENT_RUNTIME_STATUS("ENFORCEMENT", "READ", "READ", "WAVE0_ENFORCEMENT_RUNTIME_STATUS", true),
    READINESS_EVIDENCE("IAM", "READ", "READ", "WAVE0_READINESS_EVIDENCE", true),
    PERMISSION_CATALOG("IAM", "READ", "READ", "WAVE0_PERMISSION_CATALOG", true),
    NON_SENSITIVE_ADMIN("PLATFORM", "READ", "READ", "WAVE0_NON_SENSITIVE_ADMIN", true),
    TASK_LIST_SEARCH("TASK", "READ", "READ", "WAVE0_TASK_LIST_SEARCH", false);

    private final String domain;
    private final String unit;
    private final String riskLane;
    private final String entryPoint;
    private final boolean phase6c1Executable;

    Wave0ReadPilotEntryPoint(String domain, String unit, String riskLane, String entryPoint, boolean phase6c1Executable) {
        this.domain = domain;
        this.unit = unit;
        this.riskLane = riskLane;
        this.entryPoint = entryPoint;
        this.phase6c1Executable = phase6c1Executable;
    }

    public String domain() { return domain; }
    public String unit() { return unit; }
    public String riskLane() { return riskLane; }
    public String entryPoint() { return entryPoint; }
    public boolean phase6c1Executable() { return phase6c1Executable; }

    public AuthorityRoutingContext routingContext(String tenantId, String cohortKey, String correlationId) {
        return new AuthorityRoutingContext(tenantId, domain, unit, riskLane, entryPoint, cohortKey, correlationId);
    }
}
