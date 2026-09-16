package com.opensocket.aievent.database.persistence.issueattachment;

import com.opensocket.aievent.core.issuetracking.attachment.IssueAttachmentMetadata;
import com.opensocket.aievent.core.issuetracking.attachment.IssueAttachmentMetadataRepository;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Canonical Provider-neutral Issue attachment metadata authority. Never stores Provider URLs or credentials. */
@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix="integration-identity",name="store",havingValue="MYBATIS")
public class JdbcIssueAttachmentMetadataRepository implements IssueAttachmentMetadataRepository {
    private final JdbcTemplate jdbc;
    public JdbcIssueAttachmentMetadataRepository(JdbcTemplate jdbc){this.jdbc=Objects.requireNonNull(jdbc,"jdbc");}

    @Override public Optional<IssueAttachmentMetadata> find(String tenantId,String attachmentId){
        return jdbc.query("select * from integration_issue_attachment_metadata where tenant_id=? and attachment_id=?",this::map,tenantId,attachmentId).stream().findFirst();
    }
    @Override public List<IssueAttachmentMetadata> listByLink(String tenantId,String taskIssueLinkId,int limit){
        return jdbc.query("select * from integration_issue_attachment_metadata where tenant_id=? and task_issue_link_id=? order by observed_at desc limit ?",this::map,tenantId,taskIssueLinkId,Math.max(1,Math.min(limit,500)));
    }
    @Override @Transactional public IssueAttachmentMetadata save(IssueAttachmentMetadata v,long expectedVersion){
        if(expectedVersion<0)throw new IllegalArgumentException("expectedVersion must be non-negative");
        if(expectedVersion==0){if(v.metadataVersion()!=1)throw new IllegalArgumentException("New Issue attachment metadata must start at version 1");
            jdbc.update("""
                insert into integration_issue_attachment_metadata(tenant_id,attachment_id,connection_id,task_issue_link_id,project_mapping_id,provider_attachment_id,filename,content_type,size_bytes,sha256,storage_object_ref,malware_status,content_available,legal_hold,metadata_version,observed_at,created_at,updated_at)
                values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,v.tenantId(),v.attachmentId(),v.connectionId(),blank(v.taskIssueLinkId()),blank(v.projectMappingId()),blank(v.providerAttachmentId()),v.filename(),v.contentType(),v.sizeBytes(),v.sha256(),blank(v.storageObjectRef()),v.malwareStatus(),v.contentAvailable(),v.legalHold(),v.metadataVersion(),v.observedAt(),OffsetDateTime.now(),OffsetDateTime.now());
        }else{if(v.metadataVersion()!=expectedVersion+1)throw new IllegalArgumentException("Issue attachment metadata version must increment by one");
            int n=jdbc.update("""
                update integration_issue_attachment_metadata set connection_id=?,task_issue_link_id=?,project_mapping_id=?,provider_attachment_id=?,filename=?,content_type=?,size_bytes=?,sha256=?,storage_object_ref=?,malware_status=?,content_available=?,legal_hold=?,metadata_version=?,observed_at=?,updated_at=now()
                 where tenant_id=? and attachment_id=? and metadata_version=?
                """,v.connectionId(),blank(v.taskIssueLinkId()),blank(v.projectMappingId()),blank(v.providerAttachmentId()),v.filename(),v.contentType(),v.sizeBytes(),v.sha256(),blank(v.storageObjectRef()),v.malwareStatus(),v.contentAvailable(),v.legalHold(),v.metadataVersion(),v.observedAt(),v.tenantId(),v.attachmentId(),expectedVersion);
            if(n!=1)throw new IllegalStateException("ISSUE_ATTACHMENT_VERSION_CONFLICT");
        }
        return find(v.tenantId(),v.attachmentId()).orElse(v);
    }
    private IssueAttachmentMetadata map(ResultSet r,int row)throws SQLException{return new IssueAttachmentMetadata(r.getString("tenant_id"),r.getString("attachment_id"),r.getString("connection_id"),text(r.getString("task_issue_link_id")),text(r.getString("project_mapping_id")),text(r.getString("provider_attachment_id")),r.getString("filename"),r.getString("content_type"),r.getLong("size_bytes"),r.getString("sha256"),text(r.getString("storage_object_ref")),r.getString("malware_status"),r.getBoolean("content_available"),r.getBoolean("legal_hold"),r.getLong("metadata_version"),r.getObject("observed_at",OffsetDateTime.class));}
    private static String blank(String v){return v==null||v.isBlank()?null:v.trim();}
    private static String text(String v){return v==null?"":v;}
}
