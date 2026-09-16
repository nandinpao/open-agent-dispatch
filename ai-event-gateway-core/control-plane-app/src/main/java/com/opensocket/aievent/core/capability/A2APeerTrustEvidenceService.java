package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * C0-B1 trust-evidence authority. Evidence is a durable fact, never an
 * assurance grant and never a routing/authorization decision by itself.
 */
@Service
public class A2APeerTrustEvidenceService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public A2APeerTrustEvidenceService(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> evidence(String tenant, String peerId, boolean activeOnly) {
        bind(tenant, "a2a-peer-trust-evidence-read");
        requirePeer(tenant, peerId);
        String active = activeOnly
                ? " and e.evidence_status='ACTIVE' and (e.valid_until is null or e.valid_until>now())"
                : "";
        return jdbc.queryForList("""
            select e.evidence_id,e.peer_id,e.interface_id,e.subject_type,e.evidence_type,
                   case when e.evidence_status='REVOKED' then 'REVOKED'
                        when e.valid_until is not null and e.valid_until<=now() then 'EXPIRED'
                        else 'ACTIVE' end as effective_status,
                   e.evidence_status,e.issuer,e.source,e.evidence_ref,e.evidence_digest,
                   e.details_json,e.observed_at,e.valid_from,e.valid_until,e.revoked_at,
                   e.revocation_reason,e.created_by,e.created_at
              from a2a_peer_trust_evidence e
             where e.tenant_id=:tenant and e.peer_id=:peer
            """ + active + " order by e.observed_at desc,e.evidence_id",
                new MapSqlParameterSource("tenant", tenant).addValue("peer", peerId));
    }

    @Transactional
    public Map<String, Object> record(
            String tenant,
            String peerId,
            String subjectType,
            String interfaceId,
            String evidenceType,
            String issuer,
            String source,
            String evidenceRef,
            String evidenceDigest,
            Map<String, Object> details,
            OffsetDateTime observedAt,
            OffsetDateTime validFrom,
            OffsetDateTime validUntil) {
        bind(tenant, "a2a-peer-trust-evidence-record");
        required(peerId, "peerId");
        required(source, "source");
        requirePeer(tenant, peerId);
        PeerTrustEvidenceSubjectType subject = PeerTrustEvidenceSubjectType.require(subjectType);
        PeerTrustEvidenceType type = PeerTrustEvidenceType.require(evidenceType);
        if (type == PeerTrustEvidenceType.PROCESSING_ATTESTED) {
            throw new IllegalArgumentException("A2A_PROCESSING_ATTESTATION_EVIDENCE_REQUIRES_C0_B3_VERIFICATION");
        }
        return recordInternal(tenant,peerId,subject,interfaceId,type,issuer,source,evidenceRef,evidenceDigest,details,
                observedAt,validFrom,validUntil,"a2a-peer-trust-evidence");
    }

    private Map<String,Object> recordInternal(
            String tenant,String peerId,PeerTrustEvidenceSubjectType subject,String interfaceId,PeerTrustEvidenceType type,
            String issuer,String source,String evidenceRef,String evidenceDigest,Map<String,Object> details,
            OffsetDateTime observedAt,OffsetDateTime validFrom,OffsetDateTime validUntil,String createdBy) {
        if (subject == PeerTrustEvidenceSubjectType.INTERFACE) {
            required(interfaceId, "interfaceId");
            requireInterface(tenant, peerId, interfaceId);
        } else if (interfaceId != null && !interfaceId.isBlank()) {
            throw new IllegalArgumentException("A2A_PEER_TRUST_EVIDENCE_PEER_SUBJECT_INTERFACE_FORBIDDEN");
        }
        String evidenceId = "pte-" + UUID.randomUUID();
        String detailsJson;
        try {
            detailsJson = json.writeValueAsString(details == null ? Map.of() : details);
        } catch (Exception ex) {
            throw new IllegalArgumentException("A2A_PEER_TRUST_EVIDENCE_DETAILS_INVALID", ex);
        }
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("tenant", tenant).addValue("id", evidenceId).addValue("peer", peerId)
                .addValue("interface", subject == PeerTrustEvidenceSubjectType.INTERFACE ? interfaceId : null)
                .addValue("subject", subject.name()).addValue("type", type.name())
                .addValue("issuer", blankToNull(issuer)).addValue("source", source.trim())
                .addValue("ref", blankToNull(evidenceRef)).addValue("digest", blankToNull(evidenceDigest))
                .addValue("details", detailsJson).addValue("observed", observedAt)
                .addValue("validFrom", validFrom).addValue("validUntil", validUntil)
                .addValue("createdBy", createdBy);
        jdbc.update("""
            insert into a2a_peer_trust_evidence(
              tenant_id,evidence_id,peer_id,interface_id,subject_type,evidence_type,evidence_status,
              issuer,source,evidence_ref,evidence_digest,details_json,observed_at,valid_from,valid_until,
              created_by,created_at)
            values(:tenant,:id,:peer,:interface,:subject,:type,'ACTIVE',:issuer,:source,:ref,:digest,
                   cast(:details as jsonb),coalesce(:observed,now()),coalesce(:validFrom,:observed,now()),
                   :validUntil,:createdBy,now())
            """, p);
        return one(tenant, peerId, evidenceId);
    }

    @Transactional
    public Map<String, Object> revoke(String tenant, String peerId, String evidenceId, String reason) {
        bind(tenant, "a2a-peer-trust-evidence-revoke");
        Map<String,Object> existing = one(tenant, peerId, evidenceId);
        if (PeerTrustEvidenceType.PROCESSING_ATTESTED.name().equals(String.valueOf(existing.get("evidence_type")))) {
            throw new IllegalArgumentException("A2A_PROCESSING_ATTESTATION_EVIDENCE_REVOKE_REQUIRES_C0_B3");
        }
        return revokeInternal(tenant, peerId, evidenceId, reason);
    }

    Map<String,Object> recordProcessingAttestationEvidence(
            String tenant, String peerId, String issuer, String source, String evidenceRef, String evidenceDigest,
            Map<String,Object> details, OffsetDateTime observedAt, OffsetDateTime validFrom, OffsetDateTime validUntil) {
        bind(tenant, "a2a-processing-attestation-evidence-record");
        required(peerId, "peerId"); required(source, "source"); requirePeer(tenant, peerId);
        return recordInternal(tenant, peerId, PeerTrustEvidenceSubjectType.PEER, null,
                PeerTrustEvidenceType.PROCESSING_ATTESTED, issuer, source, evidenceRef, evidenceDigest, details,
                observedAt, validFrom, validUntil, "a2a-processing-attestation");
    }

    Map<String,Object> revokeProcessingAttestationEvidence(String tenant, String peerId, String evidenceId, String reason) {
        bind(tenant, "a2a-processing-attestation-evidence-revoke");
        Map<String,Object> existing = one(tenant, peerId, evidenceId);
        if (!PeerTrustEvidenceType.PROCESSING_ATTESTED.name().equals(String.valueOf(existing.get("evidence_type")))) {
            throw new IllegalArgumentException("A2A_PROCESSING_ATTESTATION_EVIDENCE_TYPE_MISMATCH");
        }
        return revokeInternal(tenant, peerId, evidenceId, reason);
    }

    private Map<String,Object> revokeInternal(String tenant, String peerId, String evidenceId, String reason) {
        required(reason, "reason");
        int updated = jdbc.update("""
            update a2a_peer_trust_evidence
               set evidence_status='REVOKED',revoked_at=now(),revocation_reason=:reason
             where tenant_id=:tenant and peer_id=:peer and evidence_id=:id and evidence_status='ACTIVE'
            """, new MapSqlParameterSource("tenant", tenant).addValue("peer", peerId)
                .addValue("id", evidenceId).addValue("reason", reason.trim()));
        if (updated != 1) throw new IllegalArgumentException("A2A_PEER_TRUST_EVIDENCE_NOT_REVOCABLE");
        return one(tenant, peerId, evidenceId);
    }

    private Map<String, Object> one(String tenant, String peerId, String evidenceId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            select * from a2a_peer_trust_evidence
             where tenant_id=:tenant and peer_id=:peer and evidence_id=:id
            """, new MapSqlParameterSource("tenant", tenant).addValue("peer", peerId).addValue("id", evidenceId));
        if (rows.isEmpty()) throw new IllegalArgumentException("A2A_PEER_TRUST_EVIDENCE_NOT_FOUND");
        return rows.get(0);
    }

    private void requirePeer(String tenant, String peerId) {
        Integer count = jdbc.queryForObject("select count(*) from a2a_peer_registrations where tenant_id=:tenant and peer_id=:peer",
                new MapSqlParameterSource("tenant", tenant).addValue("peer", peerId), Integer.class);
        if (count == null || count != 1) throw new IllegalArgumentException("A2A_PEER_NOT_FOUND");
    }

    private void requireInterface(String tenant, String peerId, String interfaceId) {
        Integer count = jdbc.queryForObject("select count(*) from a2a_peer_interfaces where tenant_id=:tenant and peer_id=:peer and interface_id=:interface",
                new MapSqlParameterSource("tenant", tenant).addValue("peer", peerId).addValue("interface", interfaceId), Integer.class);
        if (count == null || count != 1) throw new IllegalArgumentException("A2A_PEER_INTERFACE_NOT_FOUND");
    }

    private void bind(String tenant, String actor) {
        required(tenant, "tenantId");
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)", String.class, tenant);
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)", String.class, actor);
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static void required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
