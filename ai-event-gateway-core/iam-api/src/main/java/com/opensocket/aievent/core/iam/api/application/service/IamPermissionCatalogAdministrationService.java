package com.opensocket.aievent.core.iam.api.application.service;

import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.error.IamApiException;
import com.opensocket.aievent.core.iam.api.idempotency.IamIdempotencyExecutor;
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
import com.opensocket.aievent.core.iam.rbac.application.command.catalog.CreatePermissionCatalogDraftCommand;
import com.opensocket.aievent.core.iam.rbac.application.command.catalog.DeletePermissionAliasCommand;
import com.opensocket.aievent.core.iam.rbac.application.command.catalog.DeletePermissionDefinitionCommand;
import com.opensocket.aievent.core.iam.rbac.application.command.catalog.PublishPermissionCatalogCommand;
import com.opensocket.aievent.core.iam.rbac.application.command.catalog.UpsertPermissionAliasCommand;
import com.opensocket.aievent.core.iam.rbac.application.command.catalog.UpsertPermissionDefinitionCommand;
import com.opensocket.aievent.core.iam.rbac.application.port.in.PermissionCatalogAdministrationPort;
import com.opensocket.aievent.core.iam.rbac.domain.PermissionCode;
import com.opensocket.aievent.core.iam.rbac.domain.ScopeType;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionAliasType;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionLifecycle;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionRiskLane;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public final class IamPermissionCatalogAdministrationService {
    private static final String INSTANCE_SCOPE = "INSTANCE";
    private final PermissionCatalogAdministrationPort port;
    private final IamIdempotencyExecutor idempotency;

    public IamPermissionCatalogAdministrationService(
            PermissionCatalogAdministrationPort port,
            IamIdempotencyExecutor idempotency) {
        this.port = port;
        this.idempotency = idempotency;
    }

    public List<PermissionCatalogRevisionResponse> revisions() {
        return port.revisions().stream().map(PermissionCatalogRevisionResponse::from).toList();
    }
    public PermissionCatalogRevisionResponse active() {
        return port.activeRevision().map(PermissionCatalogRevisionResponse::from)
                .orElseThrow(() -> IamApiException.notFound("PERMISSION_CATALOG_REVISION_NOT_FOUND", "Active revision not found"));
    }
    public PermissionCatalogRevisionResponse revision(UUID revisionId) {
        return PermissionCatalogRevisionResponse.from(port.revision(revisionId));
    }
    public List<PermissionDefinitionResponse> definitions(UUID revisionId) {
        return port.definitions(revisionId).stream().map(PermissionDefinitionResponse::from).toList();
    }
    public List<PermissionCatalogAliasResponse> aliases(UUID revisionId) {
        return port.aliases(revisionId).stream().map(PermissionCatalogAliasResponse::from).toList();
    }
    public PermissionCatalogDiffResponse diff(UUID revisionId) {
        return PermissionCatalogDiffResponse.from(port.diff(revisionId));
    }
    public PermissionCatalogValidationResponse validate(UUID revisionId) {
        return PermissionCatalogValidationResponse.from(port.validate(revisionId));
    }
    public List<PermissionCatalogPublicationEvidenceResponse> publications(int limit) {
        if (limit < 1 || limit > 200) {
            throw IamApiException.badRequest("PERMISSION_CATALOG_PUBLICATION_LIMIT_INVALID",
                    "Publication evidence limit must be between 1 and 200");
        }
        return port.publications(limit).stream().map(PermissionCatalogPublicationEvidenceResponse::from).toList();
    }

    public PermissionCatalogRevisionResponse createDraft(
            CreatePermissionCatalogDraftRequest request,
            IamApiRequestContext context) {
        String key=context.requireIdempotencyKey();
        return idempotency.execute(INSTANCE_SCOPE,context.actorId(),"permission.catalog.revision.create",key,request,201,
                PermissionCatalogRevisionResponse.class,()->PermissionCatalogRevisionResponse.from(port.createDraft(
                new CreatePermissionCatalogDraftCommand(UUID.randomUUID(),request.revisionCode(),request.description(),
                        context.actorId(),context.correlationId(),context.requestedAt()))));
    }

    public PermissionDefinitionResponse createDefinition(UUID revisionId,UpsertPermissionDefinitionRequest request,IamApiRequestContext context) {
        return mutateDefinition(revisionId,request,0,context,"permission.catalog.definition.create",201);
    }
    public PermissionDefinitionResponse updateDefinition(UUID revisionId,String permissionCode,UpsertPermissionDefinitionRequest request,long expectedVersion,IamApiRequestContext context) {
        if(!permissionCode.equals(request.permissionCode()))throw IamApiException.badRequest("PERMISSION_CATALOG_PERMISSION_CODE_MISMATCH","Path and body Permission codes must match");
        return mutateDefinition(revisionId,request,expectedVersion,context,"permission.catalog.definition.update",200);
    }
    private PermissionDefinitionResponse mutateDefinition(UUID revisionId,UpsertPermissionDefinitionRequest request,long expectedVersion,
            IamApiRequestContext context,String operation,int status) {
        String key=context.requireIdempotencyKey();
        return idempotency.execute(INSTANCE_SCOPE,context.actorId(),operation,key,request,status,PermissionDefinitionResponse.class,()->
                PermissionDefinitionResponse.from(port.upsertDefinition(new UpsertPermissionDefinitionCommand(
                        revisionId,new PermissionCode(request.permissionCode()),request.ownerModule(),request.resourceType(),request.actionCode(),
                        request.description(),request.riskLevel().toUpperCase(Locale.ROOT),PermissionRiskLane.valueOf(request.riskLane().toUpperCase(Locale.ROOT)),
                        PermissionLifecycle.valueOf(request.lifecycle().toUpperCase(Locale.ROOT)),request.allowedScopes().stream()
                        .map(value->ScopeType.valueOf(value.toUpperCase(Locale.ROOT))).collect(Collectors.toUnmodifiableSet()),request.systemManaged(),
                        optionalCode(request.replacementPermissionCode()),Optional.ofNullable(request.deprecatedAt()),Optional.ofNullable(request.retiredAt()),
                        expectedVersion,context.actorId(),context.correlationId(),context.requestedAt()))));
    }
    public void deleteDefinition(UUID revisionId,String permissionCode,long expectedVersion,IamApiRequestContext context) {
        String key=context.requireIdempotencyKey();
        idempotency.execute(INSTANCE_SCOPE,context.actorId(),"permission.catalog.definition.delete",key,
                java.util.Map.of("revisionId",revisionId.toString(),"permissionCode",permissionCode,"version",expectedVersion),204,String.class,()->{
                    port.deleteDefinition(new DeletePermissionDefinitionCommand(revisionId,new PermissionCode(permissionCode),expectedVersion,
                            context.actorId(),context.correlationId(),context.requestedAt()));return "OK";});
    }

    public PermissionCatalogAliasResponse createAlias(UUID revisionId,UpsertPermissionAliasRequest request,IamApiRequestContext context) {
        return mutateAlias(revisionId,request,0,context,"permission.catalog.alias.create",201);
    }
    public PermissionCatalogAliasResponse updateAlias(UUID revisionId,String aliasCode,UpsertPermissionAliasRequest request,long expectedVersion,IamApiRequestContext context) {
        if(!aliasCode.equals(request.aliasCode()))throw IamApiException.badRequest("PERMISSION_CATALOG_ALIAS_CODE_MISMATCH","Path and body alias codes must match");
        return mutateAlias(revisionId,request,expectedVersion,context,"permission.catalog.alias.update",200);
    }
    private PermissionCatalogAliasResponse mutateAlias(UUID revisionId,UpsertPermissionAliasRequest request,long expectedVersion,
            IamApiRequestContext context,String operation,int status) {
        String key=context.requireIdempotencyKey();Instant validFrom=request.validFrom()==null?context.requestedAt():request.validFrom();
        return idempotency.execute(INSTANCE_SCOPE,context.actorId(),operation,key,request,status,PermissionCatalogAliasResponse.class,()->
                PermissionCatalogAliasResponse.from(port.upsertAlias(new UpsertPermissionAliasCommand(revisionId,
                        new PermissionCode(request.aliasCode()),new PermissionCode(request.canonicalPermissionCode()),
                        PermissionAliasType.valueOf(request.aliasType().toUpperCase(Locale.ROOT)),validFrom,Optional.ofNullable(request.validUntil()),
                        request.reason(),expectedVersion,context.actorId(),context.correlationId(),context.requestedAt()))));
    }
    public void deleteAlias(UUID revisionId,String aliasCode,long expectedVersion,IamApiRequestContext context) {
        String key=context.requireIdempotencyKey();
        idempotency.execute(INSTANCE_SCOPE,context.actorId(),"permission.catalog.alias.delete",key,
                java.util.Map.of("revisionId",revisionId.toString(),"aliasCode",aliasCode,"version",expectedVersion),204,String.class,()->{
                    port.deleteAlias(new DeletePermissionAliasCommand(revisionId,new PermissionCode(aliasCode),expectedVersion,
                            context.actorId(),context.correlationId(),context.requestedAt()));return "OK";});
    }
    public PermissionCatalogPublicationResponse publish(UUID revisionId,long expectedVersion,PublishPermissionCatalogRequest request,IamApiRequestContext context) {
        if(!request.confirm())throw IamApiException.badRequest("PERMISSION_CATALOG_PUBLISH_CONFIRMATION_REQUIRED","Publish confirmation is required");
        String key=context.requireIdempotencyKey();String reason=context.requireAuditReason();
        return idempotency.execute(INSTANCE_SCOPE,context.actorId(),"permission.catalog.publish",key,request,200,
                PermissionCatalogPublicationResponse.class,()->PermissionCatalogPublicationResponse.from(port.publish(
                        new PublishPermissionCatalogCommand(revisionId,expectedVersion,reason,context.actorId(),context.correlationId(),context.requestedAt()))));
    }
    private Optional<PermissionCode> optionalCode(String value){return value==null||value.isBlank()?Optional.empty():Optional.of(new PermissionCode(value));}
}
