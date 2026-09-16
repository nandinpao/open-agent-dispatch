package com.opensocket.aievent.core.iam.persistence.repository;

import static com.opensocket.aievent.core.iam.persistence.repository.RowValues.*;

import com.opensocket.aievent.core.iam.persistence.dao.IamRbacDao;
import com.opensocket.aievent.core.iam.rbac.application.port.out.PermissionCatalogAdministrationRepository;
import com.opensocket.aievent.core.iam.rbac.domain.PermissionCode;
import com.opensocket.aievent.core.iam.rbac.domain.ScopeType;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionAliasType;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionCatalogAlias;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionCatalogPublication;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionCatalogRevision;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionCatalogRevisionStatus;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionDefinition;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionLifecycle;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionRiskLane;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@DatabaseRepositoryAdapter
public class MybatisPermissionCatalogAdministrationRepository
        implements PermissionCatalogAdministrationRepository {
    private final IamRbacDao dao;

    public MybatisPermissionCatalogAdministrationRepository(IamRbacDao dao) {
        this.dao = dao;
    }

    @Override public Optional<PermissionCatalogRevision> findActiveRevision() {
        return Optional.ofNullable(dao.findActiveCatalogRevision()).map(this::revision);
    }
    @Override public long activeRevisionPointerVersion() {
        Long value = dao.findActiveCatalogRevisionPointerVersion();
        if (value == null) throw new IllegalStateException("PERMISSION_CATALOG_REVISION_NOT_FOUND");
        return value;
    }
    @Override public Optional<PermissionCatalogRevision> findRevision(UUID revisionId) {
        return Optional.ofNullable(dao.findCatalogRevision(revisionId.toString())).map(this::revision);
    }
    @Override public List<PermissionCatalogRevision> findRevisions() {
        return dao.findCatalogRevisions().stream().map(this::revision).toList();
    }
    @Override public boolean draftExists() { return dao.countDraftCatalogRevisions() > 0; }
    @Override public long nextRevisionNumber() { Long value=dao.nextCatalogRevisionNumber(); return value==null?1:value; }
    @Override public void insertRevision(PermissionCatalogRevision revision) {
        if (dao.insertCatalogRevision(revisionRow(revision)) != 1) throw new IllegalStateException("PERMISSION_CATALOG_VERSION_CONFLICT");
    }
    @Override public int cloneEntries(UUID sourceRevisionId, UUID targetRevisionId, String actorId, Instant at) {
        return dao.cloneCatalogEntries(sourceRevisionId.toString(),targetRevisionId.toString(),actorId,at);
    }
    @Override public int cloneAliases(UUID sourceRevisionId, UUID targetRevisionId, String actorId, Instant at) {
        return dao.cloneCatalogAliases(sourceRevisionId.toString(),targetRevisionId.toString(),actorId,at);
    }
    @Override public List<PermissionDefinition> findDefinitions(UUID revisionId) {
        return dao.findCatalogDefinitions(revisionId.toString()).stream().map(this::definition).toList();
    }
    @Override public Optional<PermissionDefinition> findDefinition(UUID revisionId, PermissionCode code) {
        return Optional.ofNullable(dao.findCatalogDefinition(revisionId.toString(),code.value())).map(this::definition);
    }
    @Override public void insertDefinition(PermissionDefinition definition) {
        if(dao.insertCatalogDefinition(definitionRow(definition))!=1)throw new IllegalStateException("PERMISSION_CATALOG_VERSION_CONFLICT");
    }
    @Override public boolean updateDefinition(PermissionDefinition definition,long expectedVersion) {
        return dao.updateCatalogDefinition(definitionRow(definition),expectedVersion)==1;
    }
    @Override public boolean deleteDefinition(UUID revisionId,PermissionCode code,long expectedVersion) {
        return dao.deleteCatalogDefinition(revisionId.toString(),code.value(),expectedVersion)==1;
    }
    @Override public List<PermissionCatalogAlias> findAliases(UUID revisionId) {
        return dao.findCatalogAliases(revisionId.toString()).stream().map(this::alias).toList();
    }
    @Override public Optional<PermissionCatalogAlias> findAlias(UUID revisionId,PermissionCode aliasCode) {
        return Optional.ofNullable(dao.findCatalogAlias(revisionId.toString(),aliasCode.value())).map(this::alias);
    }
    @Override public void insertAlias(PermissionCatalogAlias alias) {
        if(dao.insertCatalogAlias(aliasRow(alias))!=1)throw new IllegalStateException("PERMISSION_CATALOG_VERSION_CONFLICT");
    }
    @Override public boolean updateAlias(PermissionCatalogAlias alias,long expectedVersion) {
        return dao.updateCatalogAlias(aliasRow(alias),expectedVersion)==1;
    }
    @Override public boolean deleteAlias(UUID revisionId,PermissionCode aliasCode,long expectedVersion) {
        return dao.deleteCatalogAlias(revisionId.toString(),aliasCode.value(),expectedVersion)==1;
    }
    @Override public Set<String> findRoleReferencedPermissionCodes() {
        return new LinkedHashSet<>(dao.findRoleReferencedPermissionCodes());
    }
    @Override public void appendChangeEvent(UUID eventId,UUID revisionId,String permissionCode,String eventType,
            String changeSummary,String beforeHash,String afterHash,String actorId,String correlationId,Instant occurredAt) {
        Map<String,Object> row=new HashMap<>();
        row.put("eventId",eventId.toString());row.put("revisionId",revisionId.toString());row.put("permissionCode",safe(permissionCode));
        row.put("eventType",eventType);row.put("changeSummary",changeSummary);row.put("beforeHash",safe(beforeHash));
        row.put("afterHash",safe(afterHash));row.put("actorId",actorId);row.put("correlationId",safe(correlationId));row.put("occurredAt",occurredAt);
        if(dao.insertCatalogChangeEvent(row)!=1)throw new IllegalStateException("PERMISSION_CATALOG_CHANGE_EVENT_FAILED");
    }
    @Override public boolean publishRevision(UUID revisionId,String contentHash,String actorId,Instant at,long expectedVersion) {
        return dao.publishCatalogRevision(revisionId.toString(),contentHash,actorId,at,expectedVersion)==1;
    }
    @Override public boolean supersedeRevision(UUID revisionId,String actorId,Instant at,long expectedVersion) {
        return dao.supersedeCatalogRevision(revisionId.toString(),actorId,at,expectedVersion)==1;
    }
    @Override public void beginPublication(UUID revisionId) { dao.setCatalogPublicationContext(revisionId.toString()); }
    @Override public void materializeActiveDefinition(PermissionDefinition definition) {
        if(dao.upsertActivePermissionDefinition(definitionRow(definition))!=1)throw new IllegalStateException("PERMISSION_CATALOG_ACTIVE_PROJECTION_FAILED");
    }
    @Override public boolean activateRevision(UUID revisionId,String actorId,Instant at,long expectedVersion) {
        return dao.activateCatalogRevision(revisionId.toString(),actorId,at,expectedVersion)==1;
    }
    @Override public void appendPublication(PermissionCatalogPublication publication) {
        Map<String,Object> row=new HashMap<>();row.put("publicationId",publication.publicationId().toString());
        row.put("revisionId",publication.revisionId().toString());row.put("previousRevisionId",publication.previousRevisionId().map(UUID::toString).orElse(""));
        row.put("contentHash",publication.contentHash());row.put("entryCount",publication.entryCount());row.put("aliasCount",publication.aliasCount());
        row.put("actorId",publication.actorId());row.put("auditReason",publication.auditReason());row.put("correlationId",publication.correlationId());row.put("publishedAt",publication.publishedAt());
        if(dao.insertCatalogPublication(row)!=1)throw new IllegalStateException("PERMISSION_CATALOG_PUBLICATION_EVENT_FAILED");
    }
    @Override public List<PermissionCatalogPublication> findPublications(int limit) {
        return dao.findCatalogPublications(limit).stream().map(this::publication).toList();
    }

    private PermissionCatalogPublication publication(Map<String,Object> row) {
        return new PermissionCatalogPublication(UUID.fromString(string(row,"publicationId")),
                UUID.fromString(string(row,"revisionId")),optionalUuid(row,"previousRevisionId"),
                string(row,"contentHash"),(int)longValue(row,"entryCount"),(int)longValue(row,"aliasCount"),
                string(row,"actorId"),string(row,"auditReason"),string(row,"correlationId"),
                instant(row,"publishedAt"));
    }
    private PermissionCatalogRevision revision(Map<String,Object> row) {
        return new PermissionCatalogRevision(UUID.fromString(string(row,"revisionId")),string(row,"revisionCode"),
                longValue(row,"revisionNumber"),PermissionCatalogRevisionStatus.valueOf(string(row,"status")),
                string(row,"contentHash"),string(row,"description"),optionalUuid(row,"supersedesRevisionId"),
                instant(row,"createdAt"),string(row,"createdBy"),Optional.ofNullable(instant(row,"publishedAt")),
                Optional.ofNullable(string(row,"publishedBy")),longValue(row,"version"));
    }
    private PermissionDefinition definition(Map<String,Object> row) {
        Set<ScopeType> scopes=new LinkedHashSet<>();for(String value:csv(row,"allowedScopes"))if(!value.isBlank())scopes.add(ScopeType.valueOf(value));
        return new PermissionDefinition(new PermissionCode(string(row,"permissionCode")),string(row,"ownerModule"),
                string(row,"resourceType"),string(row,"actionCode"),string(row,"description"),string(row,"riskLevel"),
                PermissionRiskLane.valueOf(string(row,"riskLane")),PermissionLifecycle.valueOf(string(row,"lifecycle")),scopes,
                bool(row,"systemManaged"),UUID.fromString(string(row,"catalogRevisionId")),
                optionalPermission(row,"replacementPermissionCode"),instant(row,"introducedAt"),Optional.ofNullable(instant(row,"deprecatedAt")),
                Optional.ofNullable(instant(row,"retiredAt")),instant(row,"updatedAt"),string(row,"updatedBy"),longValue(row,"version"));
    }
    private PermissionCatalogAlias alias(Map<String,Object> row) {
        return new PermissionCatalogAlias(UUID.fromString(string(row,"revisionId")),new PermissionCode(string(row,"aliasCode")),
                new PermissionCode(string(row,"canonicalPermissionCode")),PermissionAliasType.valueOf(string(row,"aliasType")),
                instant(row,"validFrom"),Optional.ofNullable(instant(row,"validUntil")),string(row,"reason"),instant(row,"createdAt"),
                string(row,"createdBy"),longValue(row,"version"));
    }
    private Map<String,Object> revisionRow(PermissionCatalogRevision v){Map<String,Object> row=new HashMap<>();row.put("revisionId",v.revisionId().toString());row.put("revisionCode",v.revisionCode());row.put("revisionNumber",v.revisionNumber());row.put("status",v.status().name());row.put("contentHash",v.contentHash());row.put("description",v.description());row.put("supersedesRevisionId",v.supersedesRevisionId().map(UUID::toString).orElse(""));row.put("createdAt",v.createdAt());row.put("createdBy",v.createdBy());row.put("publishedAt",v.publishedAt().orElse(null));row.put("publishedBy",v.publishedBy().orElse(null));row.put("version",v.version());return row;}
    private Map<String,Object> definitionRow(PermissionDefinition v){Map<String,Object> row=new HashMap<>();row.put("catalogRevisionId",v.catalogRevisionId().toString());row.put("permissionCode",v.code().value());row.put("ownerModule",v.ownerModule());row.put("resourceType",v.resourceType());row.put("actionCode",v.actionCode());row.put("description",v.description());row.put("riskLevel",v.riskLevel());row.put("riskLane",v.riskLane().name());row.put("lifecycle",v.lifecycle().name());row.put("allowedScopes",join(v.allowedScopes().stream().map(Enum::name).sorted().toList()));row.put("systemManaged",v.systemManaged());row.put("replacementPermissionCode",v.replacementPermissionCode().map(PermissionCode::value).orElse(""));row.put("introducedAt",v.introducedAt());row.put("deprecatedAt",v.deprecatedAt().orElse(null));row.put("retiredAt",v.retiredAt().orElse(null));row.put("updatedAt",v.updatedAt());row.put("updatedBy",v.updatedBy());row.put("version",v.version());return row;}
    private Map<String,Object> aliasRow(PermissionCatalogAlias v){Map<String,Object> row=new HashMap<>();row.put("revisionId",v.revisionId().toString());row.put("aliasCode",v.aliasCode().value());row.put("canonicalPermissionCode",v.canonicalPermissionCode().value());row.put("aliasType",v.aliasType().name());row.put("validFrom",v.validFrom());row.put("validUntil",v.validUntil().orElse(null));row.put("reason",v.reason());row.put("createdAt",v.createdAt());row.put("createdBy",v.createdBy());row.put("version",v.version());return row;}
    private Optional<UUID> optionalUuid(Map<String,Object> row,String key){String value=string(row,key);return value==null||value.isBlank()?Optional.empty():Optional.of(UUID.fromString(value));}
    private Optional<PermissionCode> optionalPermission(Map<String,Object> row,String key){String value=string(row,key);return value==null||value.isBlank()?Optional.empty():Optional.of(new PermissionCode(value));}
    private String safe(String value){return value==null?"":value;}
}
