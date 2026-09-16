package com.opensocket.aievent.core.iam.api.controller;

import com.opensocket.aievent.core.iam.api.application.service.IamPermissionCatalogAdministrationService;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContextFactory;
import com.opensocket.aievent.core.iam.api.request.CreatePermissionCatalogDraftRequest;
import com.opensocket.aievent.core.iam.api.request.PublishPermissionCatalogRequest;
import com.opensocket.aievent.core.iam.api.request.UpsertPermissionAliasRequest;
import com.opensocket.aievent.core.iam.api.request.UpsertPermissionDefinitionRequest;
import com.opensocket.aievent.core.iam.api.response.PermissionCatalogAliasResponse;
import com.opensocket.aievent.core.iam.api.response.PermissionCatalogDiffResponse;
import com.opensocket.aievent.core.iam.api.response.PermissionCatalogPublicationResponse;
import com.opensocket.aievent.core.iam.api.response.PermissionCatalogPublicationEvidenceResponse;
import com.opensocket.aievent.core.iam.api.response.PermissionCatalogRevisionResponse;
import com.opensocket.aievent.core.iam.api.response.PermissionCatalogValidationResponse;
import com.opensocket.aievent.core.iam.api.response.PermissionDefinitionResponse;
import com.opensocket.aievent.core.iam.api.security.IamPermissionGuard;
import com.opensocket.aievent.core.iam.api.security.IamPermissions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/access/platform/permission-catalog")
@ConditionalOnBean({IamPermissionCatalogAdministrationService.class,IamPermissionGuard.class})
@ConditionalOnProperty(prefix="aeg.iam.api",name="enabled",havingValue="true")
public class IamPermissionCatalogController {
    private final IamPermissionCatalogAdministrationService service;
    private final IamPermissionGuard guard;
    private final IamApiRequestContextFactory contexts;

    public IamPermissionCatalogController(IamPermissionCatalogAdministrationService service,
            IamPermissionGuard guard,IamApiRequestContextFactory contexts) {
        this.service=service;this.guard=guard;this.contexts=contexts;
    }

    @GetMapping("/revisions")
    public List<PermissionCatalogRevisionResponse> revisions(HttpServletRequest request){var c=contexts.from(request);guard.requireInstance(c,IamPermissions.PERMISSION_CATALOG_READ,"PERMISSION_CATALOG","");return service.revisions();}
    @GetMapping("/active")
    public PermissionCatalogRevisionResponse active(HttpServletRequest request){var c=contexts.from(request);guard.requireInstance(c,IamPermissions.PERMISSION_CATALOG_READ,"PERMISSION_CATALOG","ACTIVE");return service.active();}
    @GetMapping("/revisions/{revisionId}")
    public PermissionCatalogRevisionResponse revision(@PathVariable UUID revisionId,HttpServletRequest request){var c=contexts.from(request);guard.requireInstance(c,IamPermissions.PERMISSION_CATALOG_READ,"PERMISSION_CATALOG",revisionId.toString());return service.revision(revisionId);}
    @GetMapping("/revisions/{revisionId}/entries")
    public List<PermissionDefinitionResponse> entries(@PathVariable UUID revisionId,HttpServletRequest request){var c=contexts.from(request);guard.requireInstance(c,IamPermissions.PERMISSION_CATALOG_READ,"PERMISSION_CATALOG",revisionId.toString());return service.definitions(revisionId);}
    @GetMapping("/revisions/{revisionId}/aliases")
    public List<PermissionCatalogAliasResponse> aliases(@PathVariable UUID revisionId,HttpServletRequest request){var c=contexts.from(request);guard.requireInstance(c,IamPermissions.PERMISSION_CATALOG_READ,"PERMISSION_CATALOG",revisionId.toString());return service.aliases(revisionId);}
    @GetMapping("/revisions/{revisionId}/diff")
    public PermissionCatalogDiffResponse diff(@PathVariable UUID revisionId,HttpServletRequest request){var c=contexts.from(request);guard.requireInstance(c,IamPermissions.PERMISSION_CATALOG_READ,"PERMISSION_CATALOG",revisionId.toString());return service.diff(revisionId);}
    @GetMapping("/revisions/{revisionId}/validation")
    public PermissionCatalogValidationResponse validation(@PathVariable UUID revisionId,HttpServletRequest request){var c=contexts.from(request);guard.requireInstance(c,IamPermissions.PERMISSION_CATALOG_READ,"PERMISSION_CATALOG",revisionId.toString());return service.validate(revisionId);}
    @GetMapping("/publications")
    public List<PermissionCatalogPublicationEvidenceResponse> publications(@RequestParam(defaultValue="50") int limit,HttpServletRequest request){var c=contexts.from(request);guard.requireInstance(c,IamPermissions.PERMISSION_CATALOG_READ,"PERMISSION_CATALOG","PUBLICATIONS");return service.publications(limit);}

    @PostMapping("/revisions")
    public ResponseEntity<PermissionCatalogRevisionResponse> createDraft(@Valid @RequestBody CreatePermissionCatalogDraftRequest body,HttpServletRequest request){var c=contexts.from(request);c.requireAuditReason();guard.requireInstance(c,IamPermissions.PERMISSION_CATALOG_MANAGE,"PERMISSION_CATALOG",body.revisionCode());return ResponseEntity.status(201).body(service.createDraft(body,c));}
    @PostMapping("/revisions/{revisionId}/entries")
    public ResponseEntity<PermissionDefinitionResponse> createEntry(@PathVariable UUID revisionId,@Valid @RequestBody UpsertPermissionDefinitionRequest body,HttpServletRequest request){var c=contexts.from(request);c.requireAuditReason();guard.requireInstance(c,IamPermissions.PERMISSION_CATALOG_MANAGE,"PERMISSION_DEFINITION",body.permissionCode());return ResponseEntity.status(201).body(service.createDefinition(revisionId,body,c));}
    @PutMapping("/revisions/{revisionId}/entries/{permissionCode}")
    public PermissionDefinitionResponse updateEntry(@PathVariable UUID revisionId,@PathVariable String permissionCode,@RequestHeader("If-Match") String ifMatch,@Valid @RequestBody UpsertPermissionDefinitionRequest body,HttpServletRequest request){var c=contexts.from(request);c.requireAuditReason();guard.requireInstance(c,IamPermissions.PERMISSION_CATALOG_MANAGE,"PERMISSION_DEFINITION",permissionCode);return service.updateDefinition(revisionId,permissionCode,body,c.requireExpectedVersion(ifMatch),c);}
    @DeleteMapping("/revisions/{revisionId}/entries/{permissionCode}")
    public ResponseEntity<Void> deleteEntry(@PathVariable UUID revisionId,@PathVariable String permissionCode,@RequestHeader("If-Match") String ifMatch,HttpServletRequest request){var c=contexts.from(request);c.requireAuditReason();guard.requireInstance(c,IamPermissions.PERMISSION_CATALOG_MANAGE,"PERMISSION_DEFINITION",permissionCode);service.deleteDefinition(revisionId,permissionCode,c.requireExpectedVersion(ifMatch),c);return ResponseEntity.noContent().build();}
    @PostMapping("/revisions/{revisionId}/aliases")
    public ResponseEntity<PermissionCatalogAliasResponse> createAlias(@PathVariable UUID revisionId,@Valid @RequestBody UpsertPermissionAliasRequest body,HttpServletRequest request){var c=contexts.from(request);c.requireAuditReason();guard.requireInstance(c,IamPermissions.PERMISSION_CATALOG_MANAGE,"PERMISSION_ALIAS",body.aliasCode());return ResponseEntity.status(201).body(service.createAlias(revisionId,body,c));}
    @PutMapping("/revisions/{revisionId}/aliases/{aliasCode}")
    public PermissionCatalogAliasResponse updateAlias(@PathVariable UUID revisionId,@PathVariable String aliasCode,@RequestHeader("If-Match") String ifMatch,@Valid @RequestBody UpsertPermissionAliasRequest body,HttpServletRequest request){var c=contexts.from(request);c.requireAuditReason();guard.requireInstance(c,IamPermissions.PERMISSION_CATALOG_MANAGE,"PERMISSION_ALIAS",aliasCode);return service.updateAlias(revisionId,aliasCode,body,c.requireExpectedVersion(ifMatch),c);}
    @DeleteMapping("/revisions/{revisionId}/aliases/{aliasCode}")
    public ResponseEntity<Void> deleteAlias(@PathVariable UUID revisionId,@PathVariable String aliasCode,@RequestHeader("If-Match") String ifMatch,HttpServletRequest request){var c=contexts.from(request);c.requireAuditReason();guard.requireInstance(c,IamPermissions.PERMISSION_CATALOG_MANAGE,"PERMISSION_ALIAS",aliasCode);service.deleteAlias(revisionId,aliasCode,c.requireExpectedVersion(ifMatch),c);return ResponseEntity.noContent().build();}
    @PostMapping("/revisions/{revisionId}/publish")
    public PermissionCatalogPublicationResponse publish(@PathVariable UUID revisionId,@RequestHeader("If-Match") String ifMatch,@Valid @RequestBody PublishPermissionCatalogRequest body,HttpServletRequest request){var c=contexts.from(request);c.requireAuditReason();guard.requireInstance(c,IamPermissions.PERMISSION_CATALOG_PUBLISH,"PERMISSION_CATALOG",revisionId.toString());return service.publish(revisionId,c.requireExpectedVersion(ifMatch),body,c);}
}
