package com.opensocket.aievent.core.iam.persistence.shadow;

import com.opensocket.aievent.core.iam.persistence.dao.IamRbacDao;
import com.opensocket.aievent.core.iam.rbac.application.port.out.ShadowDecisionRecorderPort;
import com.opensocket.aievent.core.iam.rbac.domain.ShadowDecisionRecord;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MybatisShadowDecisionRecorder implements ShadowDecisionRecorderPort {
    private final IamRbacDao dao;
    private final ArrayDeque<ShadowDecisionRecord> samples;
    private final int capacity;

    public MybatisShadowDecisionRecorder(IamRbacDao dao) {
        this(dao, 4096);
    }

    public MybatisShadowDecisionRecorder(IamRbacDao dao, int capacity) {
        this.dao = dao;
        this.capacity = Math.max(100, capacity);
        this.samples = new ArrayDeque<>(this.capacity);
    }

    @Override
    public void recordCritical(ShadowDecisionRecord record) {
        if (dao.insertShadowDecision(row(record)) != 1) {
            throw new IllegalStateException("SHADOW_CRITICAL_PERSISTENCE_FAILED");
        }
    }

    @Override
    public synchronized boolean enqueueSample(ShadowDecisionRecord record) {
        boolean dropped = samples.size() >= capacity;
        if (dropped) samples.removeFirst();
        samples.addLast(record);
        return !dropped;
    }

    @Override
    public void aggregate(ShadowDecisionRecord record) {
        Map<String, Object> row = row(record);
        row.put("timeBucket", record.occurredAt().truncatedTo(ChronoUnit.MINUTES));
        dao.upsertShadowAggregate(row);
    }

    public synchronized List<ShadowDecisionRecord> drain(int limit) {
        List<ShadowDecisionRecord> result = new ArrayList<>();
        while (!samples.isEmpty() && result.size() < limit) result.add(samples.removeFirst());
        return List.copyOf(result);
    }

    public synchronized int queuedSamples() {
        return samples.size();
    }

    private Map<String, Object> row(ShadowDecisionRecord record) {
        Map<String, Object> row = new HashMap<>();
        row.put("shadowId", record.shadowId());
        row.put("tenantId", record.tenantId().isBlank() ? null : record.tenantId());
        row.put("principalId", record.principalId());
        row.put("permissionCode", record.permission());
        row.put("route", record.route());
        row.put("legacyDecision", record.legacyDecision().name());
        row.put("newDecision", record.newDecision().name());
        row.put("reasonCode", record.reasonCode());
        row.put("lane", record.lane().name());
        row.put("highRisk", record.highRisk());
        row.put("metadataJson", json(record.metadata()));
        row.put("occurredAt", record.occurredAt());
        return row;
    }

    private String json(Map<String, String> source) {
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (!first) out.append(',');
            first = false;
            out.append('"').append(escape(entry.getKey())).append("\":\"")
                    .append(escape(entry.getValue())).append('"');
        }
        return out.append('}').toString();
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
