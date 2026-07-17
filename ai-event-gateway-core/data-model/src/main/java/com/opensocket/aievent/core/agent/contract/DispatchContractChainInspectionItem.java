package com.opensocket.aievent.core.agent.contract;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DispatchContractChainInspectionItem {
    private String code;
    private String tableName;
    private String label;
    private String expected;
    private String actual;
    private String status;
    private boolean present;
    private boolean healthy;
    private boolean blocking;
    private String recordId;
    private String summary;
    private String nextAction;
    private Map<String, Object> details = new LinkedHashMap<>();

    public static DispatchContractChainInspectionItem of(String code, String tableName, String label) {
        DispatchContractChainInspectionItem item = new DispatchContractChainInspectionItem();
        item.setCode(code);
        item.setTableName(tableName);
        item.setLabel(label);
        return item;
    }

    public DispatchContractChainInspectionItem pass(String actual, String recordId, String summary) {
        this.present = true;
        this.healthy = true;
        this.blocking = false;
        this.status = "PASS";
        this.actual = actual;
        this.recordId = recordId;
        this.summary = summary;
        return this;
    }

    public DispatchContractChainInspectionItem warn(String actual, String recordId, String summary, String nextAction) {
        this.present = recordId != null && !recordId.isBlank();
        this.healthy = false;
        this.blocking = false;
        this.status = "WARN";
        this.actual = actual;
        this.recordId = recordId;
        this.summary = summary;
        this.nextAction = nextAction;
        return this;
    }

    public DispatchContractChainInspectionItem block(String actual, String recordId, String summary, String nextAction) {
        this.present = recordId != null && !recordId.isBlank();
        this.healthy = false;
        this.blocking = true;
        this.status = "BLOCKED";
        this.actual = actual;
        this.recordId = recordId;
        this.summary = summary;
        this.nextAction = nextAction;
        return this;
    }

    public DispatchContractChainInspectionItem withExpected(String expected) {
        this.expected = expected;
        return this;
    }

    public DispatchContractChainInspectionItem withDetail(String key, Object value) {
        if (key != null && !key.isBlank() && value != null) {
            details.put(key, value);
        }
        return this;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details == null ? new LinkedHashMap<>() : new LinkedHashMap<>(details);
    }
}
