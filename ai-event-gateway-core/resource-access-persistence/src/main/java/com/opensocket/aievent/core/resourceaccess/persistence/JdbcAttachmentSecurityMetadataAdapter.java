package com.opensocket.aievent.core.resourceaccess.persistence;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.sql.ResultSet;import java.sql.SQLException;import java.sql.Timestamp;import java.time.Instant;import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.transaction.annotation.Transactional;

/** Security scan and internal object projection. Provider URLs and credentials are rejected by contract and schema. */
@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix="resource-access",name={"enabled","attachment-enabled"},havingValue="true")
public class JdbcAttachmentSecurityMetadataAdapter implements AttachmentSecurityMetadataPort {
 private final JdbcTemplate jdbc;public JdbcAttachmentSecurityMetadataAdapter(JdbcTemplate jdbc){this.jdbc=Objects.requireNonNull(jdbc);}
 @Override public Optional<AttachmentSecurityMetadata> find(ResourceRef ref){return jdbc.query("select * from resource_attachment_security_metadata where tenant_id=? and resource_type=? and resource_id=?",(rs,row)->map(rs),ref.tenantId(),ref.resourceType().name(),ref.resourceId()).stream().findFirst();}
 @Override @Transactional public AttachmentSecurityMetadata save(AttachmentSecurityMetadata m,long expected,String actor,String reason,String correlation){
  if(expected<0)throw new IllegalArgumentException("expectedVersion must be non-negative");Instant now=Instant.now();int changed;
  if(expected==0){changed=jdbc.update("insert into resource_attachment_security_metadata(tenant_id,resource_type,resource_id,storage_object_ref,malware_status,content_available,legal_hold,metadata_version,scanned_at,created_at,updated_at) values(?,?,?,?,?,?,?,?,?,?,?) on conflict do nothing",m.attachmentRef().tenantId(),m.attachmentRef().resourceType().name(),m.attachmentRef().resourceId(),blank(m.storageObjectRef()),m.malwareStatus().name(),m.contentAvailable(),m.legalHold(),m.version(),Timestamp.from(m.scannedAt()),Timestamp.from(now),Timestamp.from(now));}
  else{changed=jdbc.update("update resource_attachment_security_metadata set storage_object_ref=?,malware_status=?,content_available=?,legal_hold=?,metadata_version=?,scanned_at=?,updated_at=? where tenant_id=? and resource_type=? and resource_id=? and metadata_version=?",blank(m.storageObjectRef()),m.malwareStatus().name(),m.contentAvailable(),m.legalHold(),m.version(),Timestamp.from(m.scannedAt()),Timestamp.from(now),m.attachmentRef().tenantId(),m.attachmentRef().resourceType().name(),m.attachmentRef().resourceId(),expected);}
  if(changed!=1)throw new IllegalStateException("ATTACHMENT_SECURITY_METADATA_VERSION_CONFLICT");
  jdbc.update("insert into resource_attachment_security_audits(tenant_id,audit_id,resource_type,resource_id,malware_status,content_available,legal_hold,metadata_version,actor_id,reason,correlation_id,created_at) values(?,?,?,?,?,?,?,?,?,?,?,?)",m.attachmentRef().tenantId(),"asa-"+UUID.randomUUID(),m.attachmentRef().resourceType().name(),m.attachmentRef().resourceId(),m.malwareStatus().name(),m.contentAvailable(),m.legalHold(),m.version(),required(actor),required(reason),required(correlation),Timestamp.from(now));return m;
 }
 private static AttachmentSecurityMetadata map(ResultSet r)throws SQLException{return new AttachmentSecurityMetadata(new ResourceRef(r.getString("tenant_id"),ResourceType.valueOf(r.getString("resource_type")),r.getString("resource_id")),nullBlank(r.getString("storage_object_ref")),AttachmentMalwareStatus.valueOf(r.getString("malware_status")),r.getBoolean("content_available"),r.getBoolean("legal_hold"),r.getLong("metadata_version"),r.getTimestamp("scanned_at").toInstant());}
 private static String blank(String s){return s==null||s.isBlank()?null:s.trim();}private static String nullBlank(String s){return s==null?"":s;}private static String required(String s){if(s==null||s.isBlank())throw new IllegalArgumentException("audit field required");return s.trim();}
}
