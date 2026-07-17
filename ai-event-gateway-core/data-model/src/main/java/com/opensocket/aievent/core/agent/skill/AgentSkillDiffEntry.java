package com.opensocket.aievent.core.agent.skill;

import java.util.ArrayList;
import java.util.List;

/** Single field-level diff entry between two Skill Registry definitions. */
public class AgentSkillDiffEntry {
    private String field;
    private String changeType;
    private List<String> beforeValues = new ArrayList<>();
    private List<String> afterValues = new ArrayList<>();
    private List<String> addedValues = new ArrayList<>();
    private List<String> removedValues = new ArrayList<>();
    private boolean breakingChange;
    private String note;

    public String getField() { return field; }
    public void setField(String field) { this.field = field; }
    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }
    public List<String> getBeforeValues() { return beforeValues; }
    public void setBeforeValues(List<String> beforeValues) { this.beforeValues = beforeValues == null ? new ArrayList<>() : new ArrayList<>(beforeValues); }
    public List<String> getAfterValues() { return afterValues; }
    public void setAfterValues(List<String> afterValues) { this.afterValues = afterValues == null ? new ArrayList<>() : new ArrayList<>(afterValues); }
    public List<String> getAddedValues() { return addedValues; }
    public void setAddedValues(List<String> addedValues) { this.addedValues = addedValues == null ? new ArrayList<>() : new ArrayList<>(addedValues); }
    public List<String> getRemovedValues() { return removedValues; }
    public void setRemovedValues(List<String> removedValues) { this.removedValues = removedValues == null ? new ArrayList<>() : new ArrayList<>(removedValues); }
    public boolean isBreakingChange() { return breakingChange; }
    public void setBreakingChange(boolean breakingChange) { this.breakingChange = breakingChange; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
