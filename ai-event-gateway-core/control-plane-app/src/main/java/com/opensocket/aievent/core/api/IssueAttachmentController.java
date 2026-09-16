package com.opensocket.aievent.core.api;

import com.opensocket.aievent.core.http.context.OpenDispatchRequestContext;
import com.opensocket.aievent.core.http.context.OpenDispatchRequestContextHolder;
import com.opensocket.aievent.core.issuetracking.attachment.IssueAttachmentMetadata;
import com.opensocket.aievent.core.issuetracking.attachment.IssueAttachmentMetadataRepository;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedBusinessResourceAccessCoordinator;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** RS5 governed Issue attachment surface. Metadata and content release are distinct authorities. */
@RestController
@RequestMapping("/api/issues/{linkId}/attachments")
public class IssueAttachmentController {
    private final IssueAttachmentMetadataRepository attachments;
    @Autowired(required=false) private ScopedBusinessResourceAccessCoordinator scopedAccess;
    @Autowired(required=false) private AttachmentAccessPort attachmentAccess;

    public IssueAttachmentController(IssueAttachmentMetadataRepository attachments){this.attachments=attachments;}

    @GetMapping
    public List<AttachmentView> list(@PathVariable String linkId,@RequestParam(defaultValue="200") int limit){
        authorizeIssue(linkId);
        return attachments.listByLink(tenant(),linkId,Math.max(1,Math.min(limit,500))).stream().filter(this::canReadAttachment).map(AttachmentView::from).toList();
    }

    @GetMapping("/{attachmentId}")
    public AttachmentView detail(@PathVariable String linkId,@PathVariable String attachmentId){
        authorizeAttachment(attachmentId,"integration.issue.attachment.read",ResourceAction.ActionKind.READ,false,"RS5_ISSUE_ATTACHMENT_READ");
        IssueAttachmentMetadata value=find(attachmentId);
        if(!linkId.equals(value.taskIssueLinkId()))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Issue attachment not found for this Issue.");
        return AttachmentView.from(value);
    }

    @PostMapping("/{attachmentId}/download")
    public AttachmentDownloadGrant download(@PathVariable String linkId,@PathVariable String attachmentId){
        IssueAttachmentMetadata value=find(attachmentId);
        if(!linkId.equals(value.taskIssueLinkId()))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Issue attachment not found for this Issue.");
        if(attachmentAccess==null)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Governed attachment download is unavailable.");
        return attachmentAccess.authorizeDownload(new AttachmentDownloadCommand(
                new ResourceRef(tenant(),ResourceType.ISSUE_ATTACHMENT,attachmentId),
                "RS5_ISSUE_ATTACHMENT_DOWNLOAD",correlation(),Map.of("rs5","ISSUE_ATTACHMENT","parentLinkId",linkId)));
    }

    private boolean canReadAttachment(IssueAttachmentMetadata value){try{authorizeAttachment(value.attachmentId(),"integration.issue.attachment.read",ResourceAction.ActionKind.READ,false,"RS5_ISSUE_ATTACHMENT_LIST_ITEM");return true;}catch(RuntimeException denied){return false;}}
    private IssueAttachmentMetadata find(String id){return attachments.find(tenant(),required(id,"attachmentId")).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Issue attachment not found."));}
    private void authorizeIssue(String linkId){if(scopedAccess!=null)scopedAccess.authorize(ResourceType.TASK_ISSUE_LINK,required(linkId,"linkId"),"integration.issue.link.read",ResourceAction.ActionKind.READ,false,VisibilityLevel.SENSITIVE,"RS5_ISSUE_ATTACHMENT_LIST");}
    private void authorizeAttachment(String id,String permission,ResourceAction.ActionKind kind,boolean sideEffect,String purpose){if(scopedAccess!=null)scopedAccess.authorize(ResourceType.ISSUE_ATTACHMENT,required(id,"attachmentId"),permission,kind,sideEffect,VisibilityLevel.SENSITIVE,purpose);}
    private String tenant(){return required(context().tenantId(),"tenantId");}
    private String correlation(){String c=context().correlationId();return c==null||c.isBlank()?java.util.UUID.randomUUID().toString():c.trim();}
    private OpenDispatchRequestContext context(){return OpenDispatchRequestContextHolder.current().orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Request context is required."));}
    private static String required(String v,String f){if(v==null||v.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,f+" is required.");return v.trim();}

    /** Redacted metadata: storageObjectRef and Provider attachment identity never leave this surface. */
    public record AttachmentView(String attachmentId,String filename,String contentType,long sizeBytes,String sha256,String malwareStatus,boolean contentAvailable,boolean legalHold,long metadataVersion,OffsetDateTime observedAt){
        static AttachmentView from(IssueAttachmentMetadata v){return new AttachmentView(v.attachmentId(),v.filename(),v.contentType(),v.sizeBytes(),v.sha256(),v.malwareStatus(),v.contentAvailable(),v.legalHold(),v.metadataVersion(),v.observedAt());}
    }
}
