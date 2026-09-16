package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * C0-B3 authority for PeerProcessingAttestation.
 *
 * <p>A recorded assertion is PENDING and is not trust evidence. Only an explicit
 * verification transition creates the linked C0-B1 PROCESSING_ATTESTED evidence.
 * Processing/storage regions remain attested facts; residency authorization is a
 * later hard-filter authority and is intentionally not decided here.</p>
 */
@Service
public class A2APeerProcessingAttestationService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    private final A2APeerTrustEvidenceService trustEvidence;

    public A2APeerProcessingAttestationService(
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper json,
            A2APeerTrustEvidenceService trustEvidence) {
        this.jdbc = jdbc;
        this.json = json;
        this.trustEvidence = trustEvidence;
    }

    @Transactional(readOnly = true)
    public List<Map<String,Object>> attestations(String tenant, String peerId, boolean currentOnly) {
        bind(tenant,"a2a-processing-attestation-read");
        requirePeer(tenant,peerId);
        String filter = currentOnly
                ? " and a.attestation_status='VERIFIED' and a.valid_from<=now() and (a.valid_until is null or a.valid_until>now())"
                : "";
        return jdbc.queryForList("""
            select a.attestation_id,a.peer_id,a.attestation_type,a.processing_region,a.storage_region,
                   a.issuer,a.source,a.evidence_ref,a.evidence_digest,a.details_json,a.attestation_status,
                   case when a.attestation_status='REVOKED' then 'REVOKED'
                        when a.attestation_status='PENDING' then 'PENDING'
                        when a.valid_from>now() then 'NOT_YET_VALID'
                        when a.valid_until is not null and a.valid_until<=now() then 'EXPIRED'
                        else 'VERIFIED' end as effective_status,
                   a.valid_from,a.valid_until,a.verified_at,a.verified_by,a.verification_note,
                   a.trust_evidence_id,a.revoked_at,a.revocation_reason,a.created_by,a.created_at
              from a2a_peer_processing_attestations a
             where a.tenant_id=:tenant and a.peer_id=:peer
            """ + filter + " order by a.created_at desc,a.attestation_id",
                params(tenant,peerId));
    }

    @Transactional
    public Map<String,Object> record(
            String tenant,String peerId,String attestationType,String processingRegion,String storageRegion,
            String issuer,String source,String evidenceRef,String evidenceDigest,Map<String,Object> details,
            OffsetDateTime validFrom,OffsetDateTime validUntil) {
        bind(tenant,"a2a-processing-attestation-record");
        required(peerId,"peerId"); required(processingRegion,"processingRegion"); required(storageRegion,"storageRegion");
        required(issuer,"issuer"); required(source,"source"); requirePeer(tenant,peerId);
        PeerProcessingAttestationType type=PeerProcessingAttestationType.require(attestationType);
        if (blankToNull(evidenceRef)==null && blankToNull(evidenceDigest)==null) {
            throw new IllegalArgumentException("A2A_PROCESSING_ATTESTATION_EVIDENCE_ANCHOR_REQUIRED");
        }
        String id="ppa-"+UUID.randomUUID();
        String detailsJson;
        try { detailsJson=json.writeValueAsString(details==null?Map.of():details); }
        catch(Exception ex){ throw new IllegalArgumentException("A2A_PROCESSING_ATTESTATION_DETAILS_INVALID",ex); }
        MapSqlParameterSource p=new MapSqlParameterSource()
                .addValue("tenant",tenant).addValue("id",id).addValue("peer",peerId)
                .addValue("type",type.name()).addValue("processing",processingRegion.trim()).addValue("storage",storageRegion.trim())
                .addValue("issuer",issuer.trim()).addValue("source",source.trim())
                .addValue("ref",blankToNull(evidenceRef)).addValue("digest",blankToNull(evidenceDigest))
                .addValue("details",detailsJson).addValue("validFrom",validFrom).addValue("validUntil",validUntil)
                .addValue("createdBy","a2a-processing-attestation");
        jdbc.update("""
            insert into a2a_peer_processing_attestations(
              tenant_id,attestation_id,peer_id,attestation_type,processing_region,storage_region,issuer,source,
              evidence_ref,evidence_digest,details_json,attestation_status,valid_from,valid_until,created_by,created_at)
            values(:tenant,:id,:peer,:type,:processing,:storage,:issuer,:source,:ref,:digest,cast(:details as jsonb),
                   'PENDING',coalesce(:validFrom,now()),:validUntil,:createdBy,now())
            """,p);
        return one(tenant,peerId,id,false);
    }

    @Transactional
    public Map<String,Object> verify(String tenant,String peerId,String attestationId,String note) {
        bind(tenant,"a2a-processing-attestation-verify");
        Map<String,Object> row=one(tenant,peerId,attestationId,true);
        if (!PeerProcessingAttestationStatus.PENDING.name().equals(String.valueOf(row.get("attestation_status")))) {
            throw new IllegalArgumentException("A2A_PROCESSING_ATTESTATION_NOT_VERIFIABLE");
        }
        lockPeer(tenant,peerId);
        OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);
        List<Map<String,Object>> existingVerified=jdbc.queryForList("select * from a2a_peer_processing_attestations where tenant_id=:tenant and peer_id=:peer and attestation_status='VERIFIED' for update",params(tenant,peerId));
        for(Map<String,Object> existing:existingVerified){
            OffsetDateTime existingUntil=asTime(existing.get("valid_until"));
            if(existingUntil==null || existingUntil.isAfter(now)) throw new IllegalArgumentException("A2A_CURRENT_PROCESSING_ATTESTATION_ALREADY_EXISTS");
            String oldEvidence=nullableString(existing.get("trust_evidence_id"));
            if(oldEvidence!=null) trustEvidence.revokeProcessingAttestationEvidence(tenant,peerId,oldEvidence,"Processing attestation superseded after expiry");
            jdbc.update("update a2a_peer_processing_attestations set attestation_status='REVOKED',revoked_at=now(),revocation_reason='Superseded after expiry' where tenant_id=:tenant and peer_id=:peer and attestation_id=:id and attestation_status='VERIFIED'",params(tenant,peerId).addValue("id",String.valueOf(existing.get("attestation_id"))));
        }
        OffsetDateTime validUntil=asTime(row.get("valid_until"));
        if(validUntil!=null && !validUntil.isAfter(now)) throw new IllegalArgumentException("A2A_PROCESSING_ATTESTATION_EXPIRED");
        OffsetDateTime validFrom=asTime(row.get("valid_from"));
        Map<String,Object> evidenceDetails=new LinkedHashMap<>();
        evidenceDetails.put("attestationId",attestationId);
        evidenceDetails.put("attestationType",String.valueOf(row.get("attestation_type")));
        evidenceDetails.put("processingRegion",String.valueOf(row.get("processing_region")));
        evidenceDetails.put("storageRegion",String.valueOf(row.get("storage_region")));
        if(blankToNull(note)!=null)evidenceDetails.put("verificationNote",blankToNull(note));
        Map<String,Object> evidence=trustEvidence.recordProcessingAttestationEvidence(
                tenant,peerId,String.valueOf(row.get("issuer")),"PEER_PROCESSING_ATTESTATION_VERIFICATION",
                nullableString(row.get("evidence_ref")),nullableString(row.get("evidence_digest")),
                Map.copyOf(evidenceDetails),now,validFrom,validUntil);
        String evidenceId=String.valueOf(evidence.get("evidence_id"));
        int updated=jdbc.update("""
            update a2a_peer_processing_attestations
               set attestation_status='VERIFIED',verified_at=now(),verified_by=:verifiedBy,
                   verification_note=:note,trust_evidence_id=:evidence
             where tenant_id=:tenant and peer_id=:peer and attestation_id=:id and attestation_status='PENDING'
            """,params(tenant,peerId).addValue("id",attestationId).addValue("verifiedBy","a2a-processing-attestation-verifier")
                .addValue("note",blankToNull(note)).addValue("evidence",evidenceId));
        if(updated!=1)throw new IllegalStateException("A2A_PROCESSING_ATTESTATION_VERIFY_CONFLICT");
        return one(tenant,peerId,attestationId,false);
    }

    @Transactional
    public Map<String,Object> revoke(String tenant,String peerId,String attestationId,String reason) {
        bind(tenant,"a2a-processing-attestation-revoke"); required(reason,"reason");
        Map<String,Object> row=one(tenant,peerId,attestationId,true);
        String status=String.valueOf(row.get("attestation_status"));
        if(PeerProcessingAttestationStatus.REVOKED.name().equals(status))throw new IllegalArgumentException("A2A_PROCESSING_ATTESTATION_NOT_REVOCABLE");
        String evidenceId=nullableString(row.get("trust_evidence_id"));
        if(PeerProcessingAttestationStatus.VERIFIED.name().equals(status)) {
            if(evidenceId==null)throw new IllegalStateException("A2A_PROCESSING_ATTESTATION_EVIDENCE_LINK_MISSING");
            trustEvidence.revokeProcessingAttestationEvidence(tenant,peerId,evidenceId,"Processing attestation revoked: "+reason.trim());
        }
        int updated=jdbc.update("""
            update a2a_peer_processing_attestations
               set attestation_status='REVOKED',revoked_at=now(),revocation_reason=:reason
             where tenant_id=:tenant and peer_id=:peer and attestation_id=:id and attestation_status in ('PENDING','VERIFIED')
            """,params(tenant,peerId).addValue("id",attestationId).addValue("reason",reason.trim()));
        if(updated!=1)throw new IllegalArgumentException("A2A_PROCESSING_ATTESTATION_NOT_REVOCABLE");
        return one(tenant,peerId,attestationId,false);
    }

    @Transactional(readOnly = true)
    public Map<String,Object> requireCurrentVerified(String tenant,String peerId) {
        bind(tenant,"a2a-processing-attestation-current"); requirePeer(tenant,peerId);
        List<Map<String,Object>> rows=jdbc.queryForList("""
            select * from a2a_peer_processing_attestations
             where tenant_id=:tenant and peer_id=:peer and attestation_status='VERIFIED'
               and valid_from<=now() and (valid_until is null or valid_until>now())
             order by verified_at desc,attestation_id desc
            """,params(tenant,peerId));
        if(rows.isEmpty())throw new IllegalArgumentException("A2A_CURRENT_PROCESSING_ATTESTATION_REQUIRED");
        return rows.getFirst();
    }

    private Map<String,Object> one(String tenant,String peerId,String id,boolean lock) {
        List<Map<String,Object>> rows=jdbc.queryForList("select * from a2a_peer_processing_attestations where tenant_id=:tenant and peer_id=:peer and attestation_id=:id"+(lock?" for update":""),
                params(tenant,peerId).addValue("id",id));
        if(rows.isEmpty())throw new IllegalArgumentException("A2A_PROCESSING_ATTESTATION_NOT_FOUND");
        return rows.getFirst();
    }
    private void lockPeer(String tenant,String peerId){List<String> rows=jdbc.queryForList("select peer_id from a2a_peer_registrations where tenant_id=:tenant and peer_id=:peer for update",params(tenant,peerId),String.class);if(rows.size()!=1)throw new IllegalArgumentException("A2A_PEER_NOT_FOUND");}
    private void requirePeer(String tenant,String peerId){Integer n=jdbc.queryForObject("select count(*) from a2a_peer_registrations where tenant_id=:tenant and peer_id=:peer",params(tenant,peerId),Integer.class);if(n==null||n!=1)throw new IllegalArgumentException("A2A_PEER_NOT_FOUND");}
    private void bind(String tenant,String actor){required(tenant,"tenantId");jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)",String.class,tenant);jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)",String.class,actor);}
    private static MapSqlParameterSource params(String tenant,String peer){return new MapSqlParameterSource("tenant",tenant).addValue("peer",peer);}
    private static String blankToNull(String v){return v==null||v.isBlank()?null:v.trim();}
    private static String nullableString(Object v){return v==null?null:String.valueOf(v);}
    private static OffsetDateTime asTime(Object value){if(value==null)return null;if(value instanceof OffsetDateTime t)return t;if(value instanceof java.sql.Timestamp t)return t.toInstant().atOffset(ZoneOffset.UTC);return OffsetDateTime.parse(String.valueOf(value));}
    private static void required(String v,String n){if(v==null||v.isBlank())throw new IllegalArgumentException(n+" is required");}
}
