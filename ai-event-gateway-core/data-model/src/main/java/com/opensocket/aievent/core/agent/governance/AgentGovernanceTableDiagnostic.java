package com.opensocket.aievent.core.agent.governance;

/**
 * Operational table-level diagnostics for Agent governance/runtime persistence.
 */
public class AgentGovernanceTableDiagnostic {
    private String tableName;
    private long rowCount;
    private String expectedWriter;
    private String emptyMeaning;

    public AgentGovernanceTableDiagnostic() {}

    public AgentGovernanceTableDiagnostic(String tableName, long rowCount, String expectedWriter, String emptyMeaning) {
        this.tableName = tableName;
        this.rowCount = rowCount;
        this.expectedWriter = expectedWriter;
        this.emptyMeaning = emptyMeaning;
    }

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public long getRowCount() { return rowCount; }
    public void setRowCount(long rowCount) { this.rowCount = rowCount; }
    public String getExpectedWriter() { return expectedWriter; }
    public void setExpectedWriter(String expectedWriter) { this.expectedWriter = expectedWriter; }
    public String getEmptyMeaning() { return emptyMeaning; }
    public void setEmptyMeaning(String emptyMeaning) { this.emptyMeaning = emptyMeaning; }
}
