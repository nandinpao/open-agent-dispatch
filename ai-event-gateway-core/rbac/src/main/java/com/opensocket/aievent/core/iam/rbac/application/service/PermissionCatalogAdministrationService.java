package com.opensocket.aievent.core.iam.rbac.application.service;

import com.opensocket.aievent.core.iam.rbac.application.command.catalog.CreatePermissionCatalogDraftCommand;
import com.opensocket.aievent.core.iam.rbac.application.command.catalog.DeletePermissionAliasCommand;
import com.opensocket.aievent.core.iam.rbac.application.command.catalog.DeletePermissionDefinitionCommand;
import com.opensocket.aievent.core.iam.rbac.application.command.catalog.PublishPermissionCatalogCommand;
import com.opensocket.aievent.core.iam.rbac.application.command.catalog.UpsertPermissionAliasCommand;
import com.opensocket.aievent.core.iam.rbac.application.command.catalog.UpsertPermissionDefinitionCommand;
import com.opensocket.aievent.core.iam.rbac.application.port.in.PermissionCatalogAdministrationPort;
import com.opensocket.aievent.core.iam.rbac.application.port.out.PermissionCatalogAdministrationRepository;
import com.opensocket.aievent.core.iam.rbac.domain.PermissionCode;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionCatalogAlias;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionCatalogDiff;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionCatalogDiffItem;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionCatalogDiffType;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionCatalogPublication;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionCatalogRevision;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionCatalogRevisionStatus;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionCatalogValidationIssue;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionCatalogValidationReport;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionCatalogValidationSeverity;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionDefinition;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionLifecycle;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Phase 5B application authority for Draft, Diff, Validation and Publication. */
public final class PermissionCatalogAdministrationService implements PermissionCatalogAdministrationPort {
    private final PermissionCatalogAdministrationRepository repository;

    public PermissionCatalogAdministrationService(PermissionCatalogAdministrationRepository repository) {
        this.repository = repository;
    }

    @Override
    public PermissionCatalogRevision createDraft(CreatePermissionCatalogDraftCommand command) {
        if (repository.draftExists()) throw new IllegalStateException("PERMISSION_CATALOG_ACTIVE_DRAFT_EXISTS");
        PermissionCatalogRevision active = repository.findActiveRevision()
                .orElseThrow(() -> new IllegalStateException("PERMISSION_CATALOG_REVISION_NOT_FOUND"));
        long number = repository.nextRevisionNumber();
        PermissionCatalogRevision draft = new PermissionCatalogRevision(
                command.revisionId(), command.revisionCode(), number,
                PermissionCatalogRevisionStatus.DRAFT, "DRAFT:UNPUBLISHED",
                command.description(), Optional.of(active.revisionId()), command.requestedAt(),
                command.actorId(), Optional.empty(), Optional.empty(), 1);
        repository.insertRevision(draft);
        repository.cloneEntries(active.revisionId(), draft.revisionId(), command.actorId(), command.requestedAt());
        repository.cloneAliases(active.revisionId(), draft.revisionId(), command.actorId(), command.requestedAt());
        repository.appendChangeEvent(UUID.randomUUID(), draft.revisionId(), null, "REVISION_CREATED",
                "Draft cloned from " + active.revisionCode(), active.contentHash(), null,
                command.actorId(), command.correlationId(), command.requestedAt());
        return draft;
    }

    @Override public List<PermissionCatalogRevision> revisions() { return repository.findRevisions(); }
    @Override public Optional<PermissionCatalogRevision> activeRevision() { return repository.findActiveRevision(); }
    @Override public PermissionCatalogRevision revision(UUID revisionId) { return requireRevision(revisionId); }
    @Override public List<PermissionDefinition> definitions(UUID revisionId) { requireRevision(revisionId); return repository.findDefinitions(revisionId); }
    @Override public Optional<PermissionDefinition> definition(UUID revisionId, PermissionCode code) { requireRevision(revisionId); return repository.findDefinition(revisionId, code); }

    @Override
    public PermissionDefinition upsertDefinition(UpsertPermissionDefinitionCommand command) {
        requireDraft(command.revisionId());
        Optional<PermissionDefinition> existing = repository.findDefinition(command.revisionId(), command.permissionCode());
        if (command.create() != existing.isEmpty()) throw new IllegalStateException("PERMISSION_CATALOG_VERSION_CONFLICT");
        existing.ifPresent(before -> validateLifecycle(before.lifecycle(), command.lifecycle()));
        Instant introducedAt = existing.map(PermissionDefinition::introducedAt).orElse(command.requestedAt());
        Optional<Instant> deprecatedAt = lifecycleTime(command.lifecycle(), PermissionLifecycle.DEPRECATED,
                command.deprecatedAt(), existing.flatMap(PermissionDefinition::deprecatedAt), command.requestedAt());
        Optional<Instant> retiredAt = lifecycleTime(command.lifecycle(), PermissionLifecycle.RETIRED,
                command.retiredAt(), existing.flatMap(PermissionDefinition::retiredAt), command.requestedAt());
        long nextVersion = existing.map(PermissionDefinition::version).orElse(0L) + 1;
        PermissionDefinition updated = new PermissionDefinition(
                command.permissionCode(), command.ownerModule(), command.resourceType(), command.actionCode(),
                command.description(), command.riskLevel(), command.riskLane(), command.lifecycle(),
                command.allowedScopes(), command.systemManaged(), command.revisionId(),
                command.replacementPermissionCode(), introducedAt, deprecatedAt, retiredAt,
                command.requestedAt(), command.actorId(), nextVersion);
        String beforeHash = existing.map(this::hashDefinition).orElse(null);
        if (existing.isEmpty()) repository.insertDefinition(updated);
        else if (!repository.updateDefinition(updated, command.expectedVersion())) {
            throw new IllegalStateException("PERMISSION_CATALOG_VERSION_CONFLICT");
        }
        repository.appendChangeEvent(UUID.randomUUID(), command.revisionId(), command.permissionCode().value(),
                existing.isEmpty() ? "PERMISSION_CREATED" : "PERMISSION_UPDATED",
                existing.isEmpty() ? "Permission added to Draft" : "Permission changed in Draft",
                beforeHash, hashDefinition(updated), command.actorId(), command.correlationId(), command.requestedAt());
        return updated;
    }

    @Override
    public void deleteDefinition(DeletePermissionDefinitionCommand command) {
        requireDraft(command.revisionId());
        PermissionDefinition existing = repository.findDefinition(command.revisionId(), command.permissionCode())
                .orElseThrow(() -> new IllegalStateException("PERMISSION_CATALOG_PERMISSION_NOT_FOUND"));
        PermissionCatalogRevision active = repository.findActiveRevision()
                .orElseThrow(() -> new IllegalStateException("PERMISSION_CATALOG_REVISION_NOT_FOUND"));
        if (repository.findDefinition(active.revisionId(), command.permissionCode()).isPresent()) {
            throw new IllegalStateException("PERMISSION_CATALOG_EXISTING_PERMISSION_MUST_BE_RETIRED");
        }
        if (!repository.deleteDefinition(command.revisionId(), command.permissionCode(), command.expectedVersion())) {
            throw new IllegalStateException("PERMISSION_CATALOG_VERSION_CONFLICT");
        }
        repository.appendChangeEvent(UUID.randomUUID(), command.revisionId(), command.permissionCode().value(),
                "PERMISSION_DELETED", "Draft-only Permission removed", hashDefinition(existing), null,
                command.actorId(), command.correlationId(), command.requestedAt());
    }

    @Override public List<PermissionCatalogAlias> aliases(UUID revisionId) { requireRevision(revisionId); return repository.findAliases(revisionId); }

    @Override
    public PermissionCatalogAlias upsertAlias(UpsertPermissionAliasCommand command) {
        requireDraft(command.revisionId());
        if (repository.findDefinition(command.revisionId(), command.canonicalPermissionCode()).isEmpty()) {
            throw new IllegalStateException("PERMISSION_CATALOG_PERMISSION_NOT_FOUND");
        }
        if (repository.findDefinition(command.revisionId(), command.aliasCode()).isPresent()) {
            throw new IllegalStateException("PERMISSION_CATALOG_ALIAS_CONFLICT");
        }
        Optional<PermissionCatalogAlias> existing = repository.findAlias(command.revisionId(), command.aliasCode());
        if (command.create() != existing.isEmpty()) throw new IllegalStateException("PERMISSION_CATALOG_VERSION_CONFLICT");
        PermissionCatalogAlias alias = new PermissionCatalogAlias(
                command.revisionId(), command.aliasCode(), command.canonicalPermissionCode(), command.aliasType(),
                command.validFrom(), command.validUntil(), command.reason(),
                existing.map(PermissionCatalogAlias::createdAt).orElse(command.requestedAt()),
                existing.map(PermissionCatalogAlias::createdBy).orElse(command.actorId()),
                existing.map(PermissionCatalogAlias::version).orElse(0L) + 1);
        if (existing.isEmpty()) repository.insertAlias(alias);
        else if (!repository.updateAlias(alias, command.expectedVersion())) {
            throw new IllegalStateException("PERMISSION_CATALOG_VERSION_CONFLICT");
        }
        repository.appendChangeEvent(UUID.randomUUID(), command.revisionId(), command.aliasCode().value(),
                existing.isEmpty() ? "ALIAS_CREATED" : "ALIAS_UPDATED", command.reason(), null,
                hashAlias(alias), command.actorId(), command.correlationId(), command.requestedAt());
        return alias;
    }

    @Override
    public void deleteAlias(DeletePermissionAliasCommand command) {
        requireDraft(command.revisionId());
        PermissionCatalogAlias existing = repository.findAlias(command.revisionId(), command.aliasCode())
                .orElseThrow(() -> new IllegalStateException("PERMISSION_CATALOG_ALIAS_NOT_FOUND"));
        if (!repository.deleteAlias(command.revisionId(), command.aliasCode(), command.expectedVersion())) {
            throw new IllegalStateException("PERMISSION_CATALOG_VERSION_CONFLICT");
        }
        repository.appendChangeEvent(UUID.randomUUID(), command.revisionId(), command.aliasCode().value(),
                "ALIAS_DELETED", "Permission alias removed from Draft", hashAlias(existing), null,
                command.actorId(), command.correlationId(), command.requestedAt());
    }

    @Override
    public PermissionCatalogDiff diff(UUID revisionId) {
        PermissionCatalogRevision target = requireRevision(revisionId);
        UUID baseId = target.supersedesRevisionId().orElseGet(() -> repository.findActiveRevision()
                .orElseThrow(() -> new IllegalStateException("PERMISSION_CATALOG_REVISION_NOT_FOUND")).revisionId());
        Map<String, PermissionDefinition> before = byCode(repository.findDefinitions(baseId));
        Map<String, PermissionDefinition> after = byCode(repository.findDefinitions(revisionId));
        Set<String> codes = new LinkedHashSet<>(); codes.addAll(before.keySet()); codes.addAll(after.keySet());
        List<PermissionCatalogDiffItem> items = new ArrayList<>();
        for (String code : codes.stream().sorted().toList()) {
            PermissionDefinition left = before.get(code), right = after.get(code);
            if (left == null) items.add(new PermissionCatalogDiffItem(new PermissionCode(code), PermissionCatalogDiffType.ADDED, Optional.empty(), Optional.of(right), List.of()));
            else if (right == null) items.add(new PermissionCatalogDiffItem(new PermissionCode(code), PermissionCatalogDiffType.REMOVED, Optional.of(left), Optional.empty(), List.of()));
            else {
                List<String> changed = changedFields(left, right);
                if (!changed.isEmpty()) items.add(new PermissionCatalogDiffItem(new PermissionCode(code), PermissionCatalogDiffType.CHANGED, Optional.of(left), Optional.of(right), changed));
            }
        }
        return new PermissionCatalogDiff(baseId, revisionId, items);
    }

    @Override
    public PermissionCatalogValidationReport validate(UUID revisionId) {
        PermissionCatalogRevision revision = requireDraft(revisionId);
        List<PermissionDefinition> definitions = repository.findDefinitions(revisionId);
        List<PermissionCatalogAlias> aliases = repository.findAliases(revisionId);
        List<PermissionCatalogValidationIssue> issues = new ArrayList<>();
        if (definitions.isEmpty()) error(issues,"CATALOG_EMPTY","","A published Catalog must contain at least one Permission.");
        Map<String,PermissionDefinition> target = byCode(definitions);
        for (PermissionDefinition definition : definitions) {
            String code = definition.code().value();
            if (definition.lifecycle()==PermissionLifecycle.DRAFT) error(issues,"DRAFT_PERMISSION_UNPUBLISHABLE",code,"Draft Permission lifecycle must become ACTIVE before publication.");
            definition.replacementPermissionCode().ifPresent(replacement -> {
                PermissionDefinition replacementDefinition=target.get(replacement.value());
                if(replacementDefinition==null) error(issues,"REPLACEMENT_NOT_FOUND",code,"Replacement Permission is not present in this revision.");
                else if(replacementDefinition.lifecycle()==PermissionLifecycle.RETIRED) error(issues,"REPLACEMENT_RETIRED",code,"Replacement Permission cannot be RETIRED.");
            });
            if(definition.systemManaged() && definition.lifecycle()==PermissionLifecycle.RETIRED && definition.replacementPermissionCode().isEmpty())
                error(issues,"SYSTEM_PERMISSION_REPLACEMENT_REQUIRED",code,"A retired system-managed Permission requires a replacement.");
            if(definition.lifecycle()==PermissionLifecycle.DEPRECATED && definition.replacementPermissionCode().isEmpty())
                warning(issues,"DEPRECATED_WITHOUT_REPLACEMENT",code,"A deprecated Permission should identify its replacement.");
        }
        PermissionCatalogRevision active=repository.findActiveRevision().orElseThrow(()->new IllegalStateException("PERMISSION_CATALOG_REVISION_NOT_FOUND"));
        for(PermissionDefinition activeDefinition:repository.findDefinitions(active.revisionId())){
            if(!target.containsKey(activeDefinition.code().value())) error(issues,"ACTIVE_PERMISSION_REMOVED",activeDefinition.code().value(),"Existing Permissions must be retired, not removed from a revision.");
        }
        for(String roleCode:repository.findRoleReferencedPermissionCodes()){
            PermissionDefinition definition=target.get(roleCode);
            if(definition==null) error(issues,"ROLE_PERMISSION_MISSING",roleCode,"A Role references a Permission missing from this revision.");
            else if(definition.lifecycle()==PermissionLifecycle.RETIRED || definition.lifecycle()==PermissionLifecycle.DRAFT)
                error(issues,"ROLE_PERMISSION_NOT_RUNTIME_ACTIVE",roleCode,"A Role references a Permission that would not be runtime active.");
        }
        for(PermissionCatalogAlias alias:aliases){
            if(target.containsKey(alias.aliasCode().value())) error(issues,"ALIAS_COLLIDES_WITH_PERMISSION",alias.aliasCode().value(),"Alias code collides with a canonical Permission code.");
            PermissionDefinition canonical=target.get(alias.canonicalPermissionCode().value());
            if(canonical==null) error(issues,"ALIAS_TARGET_NOT_FOUND",alias.aliasCode().value(),"Alias target is missing.");
            else if(canonical.lifecycle()==PermissionLifecycle.RETIRED) error(issues,"ALIAS_TARGET_RETIRED",alias.aliasCode().value(),"Alias target cannot be RETIRED.");
        }
        boolean valid=issues.stream().noneMatch(issue->issue.severity()==PermissionCatalogValidationSeverity.ERROR);
        return new PermissionCatalogValidationReport(revision.revisionId(),valid,definitions.size(),aliases.size(),issues);
    }

    @Override
    public PermissionCatalogPublication publish(PublishPermissionCatalogCommand command) {
        PermissionCatalogRevision draft=requireDraft(command.revisionId());
        if(draft.version()!=command.expectedVersion()) throw new IllegalStateException("PERMISSION_CATALOG_VERSION_CONFLICT");
        PermissionCatalogValidationReport report=validate(command.revisionId());
        if(!report.valid()) throw new IllegalStateException("PERMISSION_CATALOG_VALIDATION_FAILED");
        List<PermissionDefinition> definitions=repository.findDefinitions(command.revisionId());
        List<PermissionCatalogAlias> aliases=repository.findAliases(command.revisionId());
        String contentHash=hashCatalog(definitions,aliases);
        PermissionCatalogRevision previous=repository.findActiveRevision().orElseThrow(()->new IllegalStateException("PERMISSION_CATALOG_REVISION_NOT_FOUND"));
        long activePointerVersion=repository.activeRevisionPointerVersion();
        if(!repository.publishRevision(command.revisionId(),contentHash,command.actorId(),command.requestedAt(),command.expectedVersion()))
            throw new IllegalStateException("PERMISSION_CATALOG_VERSION_CONFLICT");
        if(!previous.revisionId().equals(command.revisionId()) && !repository.supersedeRevision(previous.revisionId(),command.actorId(),command.requestedAt(),previous.version()))
            throw new IllegalStateException("PERMISSION_CATALOG_VERSION_CONFLICT");
        repository.beginPublication(command.revisionId());
        for(PermissionDefinition definition:definitions) repository.materializeActiveDefinition(definition);
        if(!repository.activateRevision(command.revisionId(),command.actorId(),command.requestedAt(),activePointerVersion))
            throw new IllegalStateException("PERMISSION_CATALOG_VERSION_CONFLICT");
        PermissionCatalogPublication publication=new PermissionCatalogPublication(UUID.randomUUID(),command.revisionId(),Optional.of(previous.revisionId()),contentHash,definitions.size(),aliases.size(),command.actorId(),command.auditReason(),command.correlationId(),command.requestedAt());
        repository.appendPublication(publication);
        repository.appendChangeEvent(UUID.randomUUID(),command.revisionId(),null,"REVISION_PUBLISHED","Catalog published and activated",previous.contentHash(),contentHash,command.actorId(),command.correlationId(),command.requestedAt());
        return publication;
    }

    @Override
    public List<PermissionCatalogPublication> publications(int limit) {
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("publication limit must be between 1 and 200");
        return repository.findPublications(limit);
    }

    private PermissionCatalogRevision requireRevision(UUID id){return repository.findRevision(id).orElseThrow(()->new IllegalStateException("PERMISSION_CATALOG_REVISION_NOT_FOUND"));}
    private PermissionCatalogRevision requireDraft(UUID id){PermissionCatalogRevision r=requireRevision(id);if(r.status()!=PermissionCatalogRevisionStatus.DRAFT)throw new IllegalStateException("PERMISSION_CATALOG_REVISION_NOT_DRAFT");return r;}
    private void validateLifecycle(PermissionLifecycle from,PermissionLifecycle to){boolean valid=switch(from){case DRAFT->to==PermissionLifecycle.DRAFT||to==PermissionLifecycle.ACTIVE;case ACTIVE->to==PermissionLifecycle.ACTIVE||to==PermissionLifecycle.DEPRECATED;case DEPRECATED->to==PermissionLifecycle.DEPRECATED||to==PermissionLifecycle.RETIRED;case RETIRED->to==PermissionLifecycle.RETIRED;};if(!valid)throw new IllegalStateException("PERMISSION_CATALOG_LIFECYCLE_TRANSITION_INVALID");}
    private Optional<Instant> lifecycleTime(PermissionLifecycle actual,PermissionLifecycle expected,Optional<Instant> requested,Optional<Instant> existing,Instant now){if(actual!=expected)return existing;return requested.isPresent()?requested:(existing.isPresent()?existing:Optional.of(now));}
    private Map<String,PermissionDefinition> byCode(List<PermissionDefinition> values){return values.stream().collect(Collectors.toMap(value->value.code().value(),value->value,(a,b)->b,LinkedHashMap::new));}
    private List<String> changedFields(PermissionDefinition a,PermissionDefinition b){List<String> fields=new ArrayList<>();if(!a.ownerModule().equals(b.ownerModule()))fields.add("ownerModule");if(!a.resourceType().equals(b.resourceType()))fields.add("resourceType");if(!a.actionCode().equals(b.actionCode()))fields.add("actionCode");if(!a.description().equals(b.description()))fields.add("description");if(!a.riskLevel().equals(b.riskLevel()))fields.add("riskLevel");if(a.riskLane()!=b.riskLane())fields.add("riskLane");if(a.lifecycle()!=b.lifecycle())fields.add("lifecycle");if(!a.allowedScopes().equals(b.allowedScopes()))fields.add("allowedScopes");if(a.systemManaged()!=b.systemManaged())fields.add("systemManaged");if(!a.replacementPermissionCode().equals(b.replacementPermissionCode()))fields.add("replacementPermissionCode");if(!a.deprecatedAt().equals(b.deprecatedAt()))fields.add("deprecatedAt");if(!a.retiredAt().equals(b.retiredAt()))fields.add("retiredAt");return List.copyOf(fields);}
    private void error(List<PermissionCatalogValidationIssue> issues,String code,String permission,String message){issues.add(new PermissionCatalogValidationIssue(PermissionCatalogValidationSeverity.ERROR,code,permission,message));}
    private void warning(List<PermissionCatalogValidationIssue> issues,String code,String permission,String message){issues.add(new PermissionCatalogValidationIssue(PermissionCatalogValidationSeverity.WARNING,code,permission,message));}
    private String hashCatalog(List<PermissionDefinition> definitions,List<PermissionCatalogAlias> aliases){StringBuilder canonical=new StringBuilder();definitions.stream().sorted(Comparator.comparing(value->value.code().value())).forEach(value->canonical.append(hashable(value)).append('\n'));canonical.append("--ALIASES--\n");aliases.stream().sorted(Comparator.comparing(value->value.aliasCode().value())).forEach(value->canonical.append(hashable(value)).append('\n'));return "sha256:"+sha256(canonical.toString());}
    private String hashDefinition(PermissionDefinition value){return "sha256:"+sha256(hashable(value));}
    private String hashAlias(PermissionCatalogAlias value){return "sha256:"+sha256(hashable(value));}
    private String hashable(PermissionDefinition v){return String.join("|",v.code().value(),v.ownerModule(),v.resourceType(),v.actionCode(),v.description(),v.riskLevel(),v.riskLane().name(),v.lifecycle().name(),v.allowedScopes().stream().map(Enum::name).sorted().collect(Collectors.joining(",")),Boolean.toString(v.systemManaged()),v.replacementPermissionCode().map(PermissionCode::value).orElse(""),v.introducedAt().toString(),v.deprecatedAt().map(Instant::toString).orElse(""),v.retiredAt().map(Instant::toString).orElse(""));}
    private String hashable(PermissionCatalogAlias v){return String.join("|",v.aliasCode().value(),v.canonicalPermissionCode().value(),v.aliasType().name(),v.validFrom().toString(),v.validUntil().map(Instant::toString).orElse(""),v.reason());}
    private String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception impossible){throw new IllegalStateException("SHA-256 unavailable",impossible);}}
}
