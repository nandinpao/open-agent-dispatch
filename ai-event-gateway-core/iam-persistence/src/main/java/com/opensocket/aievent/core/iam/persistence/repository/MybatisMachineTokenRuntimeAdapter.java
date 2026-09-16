package com.opensocket.aievent.core.iam.persistence.repository;

import static com.opensocket.aievent.core.iam.persistence.repository.RowValues.*;
import com.opensocket.aievent.core.iam.persistence.dao.IamTokenDao;
import com.opensocket.aievent.core.iam.token.application.port.out.MachineCredentialDirectoryPort;
import com.opensocket.aievent.core.iam.token.application.port.out.MachineOAuthRateLimitPort;
import com.opensocket.aievent.core.iam.token.application.port.out.MachineSigningKeyRepository;
import com.opensocket.aievent.core.iam.token.application.port.out.MachineTokenAuditPort;
import com.opensocket.aievent.core.iam.token.application.port.out.MachineResourceAccessAuditPort;
import com.opensocket.aievent.core.iam.token.domain.MachineSigningKey;
import com.opensocket.aievent.core.iam.token.domain.MachineSigningKeyStatus;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** MyBatis adapter for the instance-level OAuth/JWT runtime and secret-free client directory. */
@DatabaseRepositoryAdapter
public class MybatisMachineTokenRuntimeAdapter implements
        MachineCredentialDirectoryPort, MachineSigningKeyRepository, MachineOAuthRateLimitPort, MachineTokenAuditPort, MachineResourceAccessAuditPort {
    private final IamTokenDao dao;

    public MybatisMachineTokenRuntimeAdapter(IamTokenDao dao) { this.dao = dao; }

    @Override
    public Optional<Entry> resolve(String clientId) {
        if (clientId == null || clientId.isBlank()) return Optional.empty();
        return Optional.ofNullable(dao.findMachineCredentialDirectory(clientId.trim())).map(r -> new Entry(
                string(r,"clientId"), string(r,"tenantId"), string(r,"credentialId"),
                string(r,"serviceAccountId"), string(r,"credentialType"), string(r,"status"), instant(r,"expiresAt")));
    }

    @Override
    public Optional<MachineSigningKey> active() {
        return Optional.ofNullable(dao.findActiveMachineSigningKey()).map(this::key);
    }

    @Override
    public List<MachineSigningKey> publishable(Instant at) {
        return dao.findPublishableMachineSigningKeys(at).stream().map(this::key).toList();
    }

    @Override
    public MachineSigningKey activateIfRequired(MachineSigningKey candidate, Instant at, Instant previousVerifyUntil) {
        Map<String,Object> row = keyRow(candidate);
        Map<String,Object> result = dao.activateMachineSigningKey(row, at, previousVerifyUntil);
        if (result == null) throw new IllegalStateException("MACHINE_SIGNING_KEY_ACTIVATION_FAILED");
        return key(result);
    }

    @Override
    public boolean tryAcquire(String rateKey, int limitPerMinute, Instant at) {
        Instant windowStart = at.truncatedTo(ChronoUnit.MINUTES);
        Instant cleanupBefore = windowStart.minus(10, ChronoUnit.MINUTES);
        return dao.tryAcquireMachineOauthRateLimit(rateKey, windowStart, cleanupBefore, limitPerMinute);
    }

    @Override
    public void append(MachineTokenAuditPort.Event event) {
        Map<String,Object> row = new HashMap<>();
        row.put("eventId", event.eventId());
        row.put("outcome", event.outcome());
        row.put("reasonCode", event.reasonCode());
        row.put("tenantId", blankToNull(event.tenantId()));
        row.put("serviceAccountId", blankToNull(event.serviceAccountId()));
        row.put("credentialId", blankToNull(event.credentialId()));
        row.put("clientId", blankToNull(event.clientId()));
        row.put("jwtId", blankToNull(event.jwtId()));
        row.put("scopesCsv", event.scopes()==null?"":String.join(Character.toString(31), event.scopes()));
        row.put("audience", blankToNull(event.audience()));
        row.put("sourceIp", blankToNull(event.sourceIp()));
        row.put("correlationId", event.correlationId());
        row.put("occurredAt", event.occurredAt());
        if (dao.insertMachineTokenAudit(row) != 1) throw new IllegalStateException("MACHINE_TOKEN_AUDIT_WRITE_FAILED");
    }

    @Override
    public void append(MachineResourceAccessAuditPort.Event event) {
        Map<String,Object> row = new HashMap<>();
        row.put("eventId", event.eventId()); row.put("resource", event.resource()); row.put("httpMethod", event.httpMethod());
        row.put("cutoverMode", event.cutoverMode()); row.put("authMethod", event.authMethod()); row.put("outcome", event.outcome());
        row.put("reasonCode", event.reasonCode()); row.put("tenantId", blankToNull(event.tenantId())); row.put("serviceAccountId", blankToNull(event.serviceAccountId()));
        row.put("credentialId", blankToNull(event.credentialId())); row.put("jwtId", blankToNull(event.jwtId())); row.put("sourceSystem", blankToNull(event.sourceSystem()));
        row.put("sourceIp", blankToNull(event.sourceIp())); row.put("correlationId", event.correlationId()); row.put("occurredAt", event.occurredAt());
        if (dao.insertMachineResourceAccessAudit(row) != 1) throw new IllegalStateException("MACHINE_RESOURCE_ACCESS_AUDIT_WRITE_FAILED");
    }

    private MachineSigningKey key(Map<String,Object> r) {
        return new MachineSigningKey(
                string(r,"keyId"), string(r,"algorithm"), string(r,"publicKeyDerBase64"),
                string(r,"protectedPrivateKey"), string(r,"protectionKeyId"),
                MachineSigningKeyStatus.valueOf(string(r,"status")), instant(r,"activatedAt"),
                instant(r,"rotateAfter"), instant(r,"verifyUntil"), instant(r,"createdAt"),
                instant(r,"updatedAt"), longValue(r,"version"));
    }

    private Map<String,Object> keyRow(MachineSigningKey k) {
        Map<String,Object> r = new HashMap<>();
        r.put("keyId", k.keyId()); r.put("algorithm", k.algorithm());
        r.put("publicKeyDerBase64", k.publicKeyDerBase64()); r.put("protectedPrivateKey", k.protectedPrivateKey());
        r.put("protectionKeyId", k.protectionKeyId()); r.put("status", k.status().name());
        r.put("activatedAt", k.activatedAt()); r.put("rotateAfter", k.rotateAfter());
        r.put("verifyUntil", k.verifyUntil()); r.put("createdAt", k.createdAt());
        r.put("updatedAt", k.updatedAt()); r.put("version", k.version());
        return r;
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
